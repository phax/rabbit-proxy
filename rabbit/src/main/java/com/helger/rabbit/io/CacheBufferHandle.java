package com.helger.rabbit.io;

import java.nio.ByteBuffer;

import com.helger.rnio.IBufferHandler;

/**
 * A handle to a ByteBuffer that uses a buffer handler
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class CacheBufferHandle implements BufferHandle
{
  private final IBufferHandler bh;
  private ByteBuffer buffer;
  private boolean mayBeFlushed = true;

  /**
   * Create a new CacheBufferHandle that uses the given BufferHandler for the
   * caching of the ByteBuffer:s
   * 
   * @param bh
   *        the BufferHandler that is the actual cache
   */
  public CacheBufferHandle (final IBufferHandler bh)
  {
    this.bh = bh;
  }

  public synchronized boolean isEmpty ()
  {
    return buffer == null || !buffer.hasRemaining ();
  }

  public synchronized ByteBuffer getBuffer ()
  {
    if (buffer == null)
      buffer = bh.getBuffer ();
    return buffer;
  }

  public synchronized ByteBuffer getLargeBuffer ()
  {
    if (buffer != null && isLarge (buffer))
      return buffer;
    buffer = bh.growBuffer (buffer);
    return buffer;
  }

  public boolean isLarge (final ByteBuffer buffer)
  {
    return bh.isLarge (buffer);
  }

  public synchronized void possiblyFlush ()
  {
    if (!mayBeFlushed)
      throw new IllegalStateException ("buffer may not be flushed!: " + System.identityHashCode (buffer));
    if (buffer == null)
      return;
    if (!buffer.hasRemaining ())
    {
      bh.putBuffer (buffer);
      buffer = null;
    }
  }

  public synchronized void setMayBeFlushed (final boolean mayBeFlushed)
  {
    this.mayBeFlushed = mayBeFlushed;
  }

  @Override
  public String toString ()
  {
    return getClass ().getName () + "[buffer: " + buffer + ", bh: " + bh + "}";
  }
}
