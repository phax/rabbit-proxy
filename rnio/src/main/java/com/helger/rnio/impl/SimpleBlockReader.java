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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.helger.rnio.NioHandler;
import com.helger.rnio.ReadHandler;

/**
 * A reader of data. Will wait until a channel is read-ready and then read a
 * block of data from it.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public abstract class SimpleBlockReader extends SocketHandlerBase <SocketChannel> implements ReadHandler
{

  private final Logger logger = Logger.getLogger ("org.khelekore.rnio");

  /**
   * Create a new block reader.
   *
   * @param sc
   *        the channel to read from
   * @param nioHandler
   *        the NioHandler to use for waiting on data
   * @param timeout
   *        the timeout time, may be null if not timeout is set
   */
  public SimpleBlockReader (final SocketChannel sc, final NioHandler nioHandler, final Long timeout)
  {
    super (sc, nioHandler, timeout);
  }

  /**
   * Try to read data from the channel.
   */
  public void read ()
  {
    try
    {
      final ByteBuffer buf = getByteBuffer ();
      final int read = sc.read (buf);
      if (read == -1)
      {
        channelClosed ();
        putByteBuffer (buf);
        return;
      }
      if (read == 0)
      {
        putByteBuffer (buf);
        register ();
      }
      else
      {
        buf.flip ();
        handleBufferRead (buf);
      }
    }
    catch (final IOException e)
    {
      handleIOException (e);
    }
  }

  /**
   * Called before a read attempt is made. The default is to create a new 1kB
   * big ByteBuffer and return it.
   *
   * @return the ByteBuffer to read data into
   */
  public ByteBuffer getByteBuffer ()
  {
    return ByteBuffer.allocate (1024);
  }

  /**
   * Return the ByteBuffer, this method will be called when read gets EOF or no
   * data. The default is to do nothing.
   *
   * @param buf
   *        the ByteBuffer that is returned
   */
  public void putByteBuffer (final ByteBuffer buf)
  {
    // nothing.
  }

  /**
   * Handle the exception, default is to log it and to close the channel.
   *
   * @param e
   *        the IOException that was the cause of a read failure
   */
  public void handleIOException (final IOException e)
  {
    logger.log (Level.WARNING, "Failed to read data", e);
    Closer.close (sc, logger);
  }

  /**
   * Do any cleanup that needs to be done when the channel we tried to read from
   * was closed.
   */
  public abstract void channelClosed ();

  /**
   * Handle the buffer content.
   *
   * @param buf
   *        the ByteBuffer with the newly read data
   * @throws IOException
   *         if data handling fails
   */
  public abstract void handleBufferRead (ByteBuffer buf) throws IOException;

  /** Wait for the channel to become read ready. */
  public void register ()
  {
    nioHandler.waitForRead (sc, this);
  }
}
