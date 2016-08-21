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
package com.helger.rnio;

import java.nio.ByteBuffer;

/**
 * A ByteBuffer handler
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public interface BufferHandler
{
  /**
   * Get a byte buffer of reasonable size, the buffer will have been cleared.
   *
   * @return the ByteBuffer to use
   */
  ByteBuffer getBuffer ();

  /**
   * Return a buffer.
   *
   * @param buffer
   *        the ByteBuffer to return to the buffer pool
   */
  void putBuffer (ByteBuffer buffer);

  /**
   * Get a larger buffer with the same contents as buffer, this will also return
   * buffer to the pool.
   *
   * @param buffer
   *        an existing buffer, the contents will be copied into the new larger
   *        buffer. May be null.
   * @return the new bigger buffer
   */
  ByteBuffer growBuffer (ByteBuffer buffer);

  /**
   * Check if the given buffer is a large buffer
   *
   * @param buffer
   *        the ByteBuffer to check
   * @return true if the given buffer is large
   */
  boolean isLarge (ByteBuffer buffer);
}
