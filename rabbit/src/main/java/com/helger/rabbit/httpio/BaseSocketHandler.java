package com.helger.rabbit.httpio;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import com.helger.rabbit.io.BufferHandle;
import com.helger.rnio.INioHandler;
import com.helger.rnio.IReadHandler;
import com.helger.rnio.ISocketChannelHandler;
import com.helger.rnio.IWriteHandler;

/**
 * A base class for socket handlers.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public abstract class BaseSocketHandler implements ISocketChannelHandler
{
  /** The client channel. */
  private final SocketChannel channel;

  /** The nio handler we are using. */
  private final INioHandler nioHandler;

  /** The buffer handle. */
  private final BufferHandle bh;

  /** The timeout value set by the previous channel registration */
  private Long timeout;

  /**
   * Create a new BaseSocketHandler that will handle the traffic on the given
   * channel
   *
   * @param channel
   *        the SocketChannel to read to and write from
   * @param bh
   *        the BufferHandle to use for the io operation
   * @param nioHandler
   *        the NioHandler to use to wait for operations on
   */
  public BaseSocketHandler (final SocketChannel channel, final BufferHandle bh, final INioHandler nioHandler)
  {
    this.channel = channel;
    this.bh = bh;
    this.nioHandler = nioHandler;
  }

  protected ByteBuffer getBuffer ()
  {
    return bh.getBuffer ();
  }

  protected ByteBuffer getLargeBuffer ()
  {
    return bh.getLargeBuffer ();
  }

  protected boolean isUsingSmallBuffer (final ByteBuffer buffer)
  {
    return !bh.isLarge (buffer);
  }

  protected void releaseBuffer ()
  {
    bh.possiblyFlush ();
  }

  /** Does nothing by default */
  public void closed ()
  {
    // empty
  }

  /** Does nothing by default */
  public void timeout ()
  {
    // empty
  }

  /** Runs on the selector thread by default */
  public boolean useSeparateThread ()
  {
    return false;
  }

  public String getDescription ()
  {
    return getClass ().getName () + ":" + channel;
  }

  public Long getTimeout ()
  {
    return timeout;
  }

  protected void closeDown ()
  {
    releaseBuffer ();
    nioHandler.close (channel);
  }

  /**
   * Get the channel this BaseSocketHandler is using
   *
   * @return the SocketChannel being used
   */
  public SocketChannel getChannel ()
  {
    return channel;
  }

  /**
   * Get the BufferHandle this BaseSocketHandler is using
   *
   * @return the BufferHandle used for io operations
   */
  public BufferHandle getBufferHandle ()
  {
    return bh;
  }

  /**
   * Wait for more data to be readable on the channel
   *
   * @param rh
   *        the handler that will be notified when more data is ready to be read
   */
  public void waitForRead (final IReadHandler rh)
  {
    this.timeout = nioHandler.getDefaultTimeout ();
    nioHandler.waitForRead (channel, rh);
  }

  /**
   * Wait for more data to be writable on the channel
   *
   * @param rh
   *        the handler that will be notified when more data is ready to be
   *        written
   */
  public void waitForWrite (final IWriteHandler rh)
  {
    this.timeout = nioHandler.getDefaultTimeout ();
    nioHandler.waitForWrite (channel, rh);
  }
}
