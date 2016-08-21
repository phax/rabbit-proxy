package com.helger.rabbit.io;

import java.nio.ByteBuffer;

/**
 * A handle to a ByteBuffer.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class SimpleBufferHandle implements BufferHandle
{
  private ByteBuffer buffer;

  /**
   * Create a BufferHandle that wraps the given ByteBuffer.
   * 
   * @param buffer
   *        the ByteBuffer to wrap
   */
  public SimpleBufferHandle (final ByteBuffer buffer)
  {
    this.buffer = buffer;
  }

  public boolean isEmpty ()
  {
    return !buffer.hasRemaining ();
  }

  public ByteBuffer getBuffer ()
  {
    return buffer;
  }

  public ByteBuffer getLargeBuffer ()
  {
    throw new RuntimeException ("Not implemented");
  }

  public boolean isLarge (final ByteBuffer buffer)
  {
    return false; // we only give out small buffers
  }

  public void possiblyFlush ()
  {
    if (!buffer.hasRemaining ())
      buffer = null;
  }

  public void setMayBeFlushed (final boolean mayBeFlushed)
  {
    // ignore
  }
}
