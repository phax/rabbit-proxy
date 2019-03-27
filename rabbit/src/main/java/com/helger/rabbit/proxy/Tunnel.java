package com.helger.rabbit.proxy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.io.stream.StreamHelper;
import com.helger.rabbit.io.BufferHandle;
import com.helger.rabbit.util.ITrafficLogger;
import com.helger.rnio.INioHandler;
import com.helger.rnio.IReadHandler;
import com.helger.rnio.IWriteHandler;

/**
 * A handler that just tunnels data.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class Tunnel
{
  private static final Logger LOGGER = LoggerFactory.getLogger (Tunnel.class);

  private final INioHandler nioHandler;
  private final OneWayTunnel fromToTo;
  private final OneWayTunnel toToFrom;
  private final TunnelDoneListener listener;

  /**
   * Create a tunnel that transfers data as fast as possible in full duplex.
   *
   * @param nioHandler
   *        the NioHandler to use for waiting on data to read as well as waiting
   *        for write ready
   * @param from
   *        one end of the tunnel
   * @param fromHandle
   *        the ByteBuffer holder for the data from "from"
   * @param fromLogger
   *        the traffic statistics gatherer for "from"
   * @param to
   *        the other end of the tunnel
   * @param toHandle
   *        the ByteBuffer holder for the data from "from"
   * @param toLogger
   *        the traffic statistics gatherer for "from"
   * @param listener
   *        the listener that will be notified when the tunnel is closed
   */
  public Tunnel (final INioHandler nioHandler,
                 final SocketChannel from,
                 final BufferHandle fromHandle,
                 final ITrafficLogger fromLogger,
                 final SocketChannel to,
                 final BufferHandle toHandle,
                 final ITrafficLogger toLogger,
                 final TunnelDoneListener listener)
  {
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Tunnel created from: " + from + " to: " + to);
    this.nioHandler = nioHandler;
    fromToTo = new OneWayTunnel (from, to, fromHandle, fromLogger);
    toToFrom = new OneWayTunnel (to, from, toHandle, toLogger);
    this.listener = listener;
  }

  /**
   * Start tunneling data in both directions.
   */
  public void start ()
  {
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Tunnel started");
    fromToTo.start ();
    toToFrom.start ();
  }

  private class OneWayTunnel implements IReadHandler, IWriteHandler
  {
    private final SocketChannel from;
    private final SocketChannel to;
    private final BufferHandle bh;
    private final ITrafficLogger tl;

    public OneWayTunnel (final SocketChannel from,
                         final SocketChannel to,
                         final BufferHandle bh,
                         final ITrafficLogger tl)
    {
      this.from = from;
      this.to = to;
      this.bh = bh;
      this.tl = tl;
    }

    public void start ()
    {
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("OneWayTunnel started: bh.isEmpty: " + bh.isEmpty ());
      if (bh.isEmpty ())
        waitForRead ();
      else
        writeData ();
    }

    private void waitForRead ()
    {
      bh.possiblyFlush ();
      nioHandler.waitForRead (from, this);
    }

    private void waitForWrite ()
    {
      bh.possiblyFlush ();
      nioHandler.waitForWrite (to, this);
    }

    public void unregister ()
    {
      nioHandler.cancel (from, this);
      nioHandler.cancel (to, this);

      // clear buffer and return it.
      final ByteBuffer buf = bh.getBuffer ();
      buf.position (buf.limit ());
      bh.possiblyFlush ();
    }

    private void writeData ()
    {
      try
      {
        if (!to.isOpen ())
        {
          LOGGER.warn ("Tunnel to is closed, not writing data");
          closeDown ();
          return;
        }
        final ByteBuffer buf = bh.getBuffer ();
        if (buf.hasRemaining ())
        {
          int written;
          do
          {
            written = to.write (buf);
            if (LOGGER.isDebugEnabled ())
              LOGGER.debug ("OneWayTunnel wrote: " + written);
            tl.write (written);
          } while (written > 0 && buf.hasRemaining ());
        }

        if (buf.hasRemaining ())
          waitForWrite ();
        else
          waitForRead ();
      }
      catch (final IOException e)
      {
        LOGGER.warn ("Got exception writing to tunnel", e);
        closeDown ();
      }
    }

    public void closed ()
    {
      LOGGER.info ("Tunnel closed");
      closeDown ();
    }

    public void timeout ()
    {
      LOGGER.warn ("Tunnel got timeout");
      closeDown ();
    }

    public boolean useSeparateThread ()
    {
      return false;
    }

    public String getDescription ()
    {
      return "Tunnel part from: " + from + " to: " + to;
    }

    public Long getTimeout ()
    {
      return null;
    }

    public void read ()
    {
      try
      {
        if (!from.isOpen ())
        {
          LOGGER.warn ("Tunnel to is closed, not reading data");
          return;
        }
        final ByteBuffer buffer = bh.getBuffer ();
        buffer.clear ();
        final int read = from.read (buffer);
        if (LOGGER.isDebugEnabled ())
          LOGGER.debug ("OneWayTunnel read: " + read);
        if (read == -1)
        {
          buffer.position (buffer.limit ());
          closeDown ();
        }
        else
        {
          buffer.flip ();
          tl.read (read);
          writeData ();
        }
      }
      catch (final IOException e)
      {
        LOGGER.warn ("Got exception reading from tunnel: " + e);
        closeDown ();
      }
    }

    public void write ()
    {
      writeData ();
    }
  }

  private void closeDown ()
  {
    fromToTo.unregister ();
    toToFrom.unregister ();
    // we do not want to close the channels,
    // it is up to the listener to do that.
    if (listener != null)
    {
      listener.tunnelClosed ();
    }
    else
    {
      // hmm? no listeners, then close down
      StreamHelper.close (fromToTo.from);
      StreamHelper.close (toToFrom.from);
    }
  }
}
