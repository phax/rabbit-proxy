package com.helger.rabbit.httpio;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.rabbit.io.BufferHandle;
import com.helger.rabbit.util.ITrafficLogger;
import com.helger.rnio.INioHandler;
import com.helger.rnio.IWriteHandler;

/**
 * A handler that writes data blocks.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class BlockSender extends BaseSocketHandler implements IWriteHandler
{
  private static final Logger LOGGER = LoggerFactory.getLogger (BlockSender.class);

  private ByteBuffer chunkBuffer;
  private final ByteBuffer end;
  private final ByteBuffer [] buffers;
  private final ITrafficLogger tl;
  private final BlockSentListener sender;

  /**
   * Create a new BlockSender that will write data to the given channel
   *
   * @param channel
   *        the SocketChannel to write the data to
   * @param nioHandler
   *        the NioHandler to use to wait for write ready
   * @param tl
   *        the traffic statistics gatherer
   * @param bufHandle
   *        the data to write
   * @param chunking
   *        if true chunk the data out
   * @param sender
   *        the listener that will be notified when the data has been handled.
   */
  public BlockSender (final SocketChannel channel,
                      final INioHandler nioHandler,
                      final ITrafficLogger tl,
                      final BufferHandle bufHandle,
                      final boolean chunking,
                      final BlockSentListener sender)
  {
    super (channel, bufHandle, nioHandler);
    this.tl = tl;
    final ByteBuffer buffer = bufHandle.getBuffer ();
    if (chunking)
    {
      final int len = buffer.remaining ();
      final String s = Long.toHexString (len) + "\r\n";
      try
      {
        chunkBuffer = ByteBuffer.wrap (s.getBytes ("ASCII"));
      }
      catch (final UnsupportedEncodingException e)
      {
        LOGGER.warn ("BlockSender: ASCII not found!", e);
      }
      end = ByteBuffer.wrap (new byte [] { '\r', '\n' });
      buffers = new ByteBuffer [] { chunkBuffer, buffer, end };
    }
    else
    {
      buffers = new ByteBuffer [] { buffer };
      end = buffer;
    }
    this.sender = sender;
  }

  @Override
  public String getDescription ()
  {
    final StringBuilder sb = new StringBuilder ("BlockSender: buffers: " + buffers.length);
    for (int i = 0; i < buffers.length; i++)
    {
      if (i > 0)
        sb.append (", ");
      sb.append ("i: ").append (buffers[i].remaining ());
    }
    return sb.toString ();
  }

  @Override
  public void timeout ()
  {
    releaseBuffer ();
    sender.timeout ();
  }

  @Override
  public void closed ()
  {
    releaseBuffer ();
    sender.failed (new IOException ("channel was closed"));
  }

  public void write ()
  {
    try
    {
      writeBuffer ();
    }
    catch (final IOException e)
    {
      releaseBuffer ();
      sender.failed (e);
    }
  }

  private void writeBuffer () throws IOException
  {
    long written;
    do
    {
      written = getChannel ().write (buffers);
      tl.write (written);
    } while (written > 0 && end.remaining () > 0);

    if (end.remaining () == 0)
    {
      releaseBuffer ();
      sender.blockSent ();
    }
    else
    {
      waitForWrite (this);
    }
  }
}
