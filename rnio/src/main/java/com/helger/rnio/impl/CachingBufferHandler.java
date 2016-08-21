/**
 * Copyright (c) 2010 Robert Olofsson.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the authors nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHORS AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHORS OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */
package com.helger.rnio.impl;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.helger.rnio.IBufferHandler;

/**
 * A buffer handler that re-uses returned buffers.
 * <p>
 * This class uses no synchronization.
 * <p>
 * This class only allocates direct buffers.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class CachingBufferHandler implements IBufferHandler
{
  private final Queue <BufferHolder> cache = new ConcurrentLinkedQueue<> ();
  private final Queue <BufferHolder> largeCache = new ConcurrentLinkedQueue<> ();

  private static final int SMALL_BUFFER_SIZE = 4096;
  private static final int LARGE_BUFFER_SIZE = 128 * 1024;

  private ByteBuffer getBuffer (final Queue <BufferHolder> bufs, final int size)
  {
    final BufferHolder r = bufs.poll ();
    ByteBuffer b = null;
    if (r != null)
      b = r.getBuffer ();
    else
      b = ByteBuffer.allocateDirect (size);
    b.clear ();
    return b;
  }

  public ByteBuffer getBuffer ()
  {
    return getBuffer (cache, SMALL_BUFFER_SIZE);
  }

  private void addCache (final Queue <BufferHolder> bufs, final BufferHolder bh)
  {
    bufs.add (bh);
  }

  public void putBuffer (final ByteBuffer buffer)
  {
    if (buffer == null)
      throw new IllegalArgumentException ("null buffer not allowed");
    final BufferHolder bh = new BufferHolder (buffer);
    if (buffer.capacity () == SMALL_BUFFER_SIZE)
      addCache (cache, bh);
    else
      addCache (largeCache, bh);
  }

  public ByteBuffer growBuffer (final ByteBuffer buffer)
  {
    final ByteBuffer lb = getBuffer (largeCache, LARGE_BUFFER_SIZE);
    if (buffer != null)
    {
      lb.put (buffer);
      putBuffer (buffer);
    }
    return lb;
  }

  public boolean isLarge (final ByteBuffer buffer)
  {
    return buffer.capacity () > SMALL_BUFFER_SIZE;
  }

  private static final class BufferHolder
  {
    private final ByteBuffer buffer;

    public BufferHolder (final ByteBuffer buffer)
    {
      this.buffer = buffer;
    }

    // Two holders are equal if they hold the same buffer
    @Override
    public boolean equals (final Object o)
    {
      if (o == null)
        return false;
      if (o == this)
        return true;

      // ByteBuffer.equals depends on content, not what I want.
      if (o instanceof BufferHolder)
        return ((BufferHolder) o).buffer == buffer;
      return false;
    }

    @Override
    public int hashCode ()
    {
      // ByteBuffer.hashCode depends on its contents.
      return System.identityHashCode (buffer);
    }

    public ByteBuffer getBuffer ()
    {
      return buffer;
    }
  }
}
