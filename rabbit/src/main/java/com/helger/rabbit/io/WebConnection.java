package com.helger.rabbit.io;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.rabbit.util.Counter;
import com.helger.rnio.IConnectHandler;
import com.helger.rnio.INioHandler;

/**
 * A class to handle a connection to the Internet.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class WebConnection implements Closeable
{
  private static final Logger LOGGER = LoggerFactory.getLogger (WebConnection.class);
  private static final AtomicInteger idCounter = new AtomicInteger (0);

  private final int id;
  private final Address address;
  private final SocketBinder binder;
  private final Counter counter;
  private SocketChannel channel;
  private long releasedAt = -1;
  private boolean keepalive = true;
  private boolean mayPipeline = false;

  /**
   * Create a new WebConnection to the given InetAddress and port.
   *
   * @param address
   *        the computer to connect to.
   * @param binder
   *        the SocketBinder to use when creating the network socket
   * @param counter
   *        the Counter to used to collect statistics
   */
  public WebConnection (final Address address, final SocketBinder binder, final Counter counter)
  {
    this.id = idCounter.getAndIncrement ();
    this.address = address;
    this.binder = binder;
    this.counter = counter;
    counter.inc ("WebConnections created");
  }

  @Override
  public String toString ()
  {
    final int port = channel != null ? channel.socket ().getLocalPort () : -1;
    return "WebConnection(id: " +
           id +
           ", address: " +
           address +
           ", keepalive: " +
           keepalive +
           ", releasedAt: " +
           releasedAt +
           ", local port: " +
           port +
           ")";
  }

  /**
   * Get the address that this connection is connected to
   *
   * @return the network address that the underlying socket is connected to
   */
  public Address getAddress ()
  {
    return address;
  }

  /**
   * Get the actual SocketChannel that is used
   *
   * @return the network channel
   */
  public SocketChannel getChannel ()
  {
    return channel;
  }

  public void close () throws IOException
  {
    counter.inc ("WebConnections closed");
    channel.close ();
  }

  /**
   * Try to establish the network connection.
   *
   * @param nioHandler
   *        the NioHandler to use for network tasks
   * @param wcl
   *        the listener that will be notified when the connection has been
   *        extablished.
   * @param setTcpNoDelay
   *        pass true if you want to enable tcp no delay, false to leave it at
   *        default.
   * @throws IOException
   *         if the network operations fail
   */
  public void connect (final INioHandler nioHandler,
                       final WebConnectionListener wcl,
                       final boolean setTcpNoDelay) throws IOException
  {
    // if we are a keepalive connection then just say so..
    if (channel != null && channel.isConnected ())
    {
      wcl.connectionEstablished (this);
    }
    else
    {
      // ok, open the connection....
      channel = SocketChannel.open ();
      channel.socket ().bind (new InetSocketAddress (binder.getInetAddress (), binder.getPort ()));
      channel.configureBlocking (false);
      if (setTcpNoDelay)
        channel.socket ().setTcpNoDelay (true);
      final SocketAddress addr = new InetSocketAddress (address.getInetAddress (), address.getPort ());
      final boolean connected = channel.connect (addr);
      if (connected)
      {
        wcl.connectionEstablished (this);
      }
      else
      {
        new ConnectListener (wcl).waitForConnection (nioHandler);
      }
    }
  }

  private class ConnectListener implements IConnectHandler
  {
    private INioHandler nioHandler;
    private final WebConnectionListener wcl;
    private Long timeout;

    public ConnectListener (final WebConnectionListener wcl)
    {
      this.wcl = wcl;
    }

    public void waitForConnection (final INioHandler nioHandler)
    {
      this.nioHandler = nioHandler;
      timeout = nioHandler.getDefaultTimeout ();
      nioHandler.waitForConnect (channel, this);
    }

    public void closed ()
    {
      wcl.failed (new IOException ("channel closed before connect"));
    }

    public void timeout ()
    {
      closeDown ();
      wcl.timeout ();
    }

    public boolean useSeparateThread ()
    {
      return false;
    }

    public String getDescription ()
    {
      return "WebConnection$ConnectListener: address: " + address;
    }

    public Long getTimeout ()
    {
      return timeout;
    }

    public void connect ()
    {
      try
      {
        channel.finishConnect ();
        wcl.connectionEstablished (WebConnection.this);
      }
      catch (final IOException e)
      {
        closeDown ();
        wcl.failed (e);
      }
    }

    private void closeDown ()
    {
      try
      {
        close ();
        nioHandler.close (channel);
      }
      catch (final IOException e)
      {
        LOGGER.warn ("Failed to close down WebConnection", e);
      }
    }

    @Override
    public String toString ()
    {
      return getClass ().getSimpleName () + "{" + address + "}@" + Integer.toString (hashCode (), 16);
    }
  }

  /**
   * Set the keepalive value for this WebConnection, Can only be turned off.
   *
   * @param b
   *        the new keepalive value.
   */
  public void setKeepalive (final boolean b)
  {
    keepalive &= b;
  }

  /**
   * Get the keepalive value of this WebConnection.
   *
   * @return true if this WebConnection may be reused.
   */
  public boolean getKeepalive ()
  {
    return keepalive;
  }

  /**
   * Mark this WebConnection as released at current time.
   */
  public void setReleased ()
  {
    releasedAt = System.currentTimeMillis ();
  }

  /**
   * Get the time that this WebConnection was released.
   *
   * @return the time this WebConnection was last released.
   */
  public long getReleasedAt ()
  {
    return releasedAt;
  }

  /**
   * Mark this WebConnection for pipelining.
   *
   * @param b
   *        if true this connection may be used for pipelining.
   */
  public void setMayPipeline (final boolean b)
  {
    mayPipeline = b;
  }

  /**
   * Check if this WebConnection may be used for pipelining.
   *
   * @return true if this connection may be used for pipelining
   */
  public boolean mayPipeline ()
  {
    return mayPipeline;
  }
}
