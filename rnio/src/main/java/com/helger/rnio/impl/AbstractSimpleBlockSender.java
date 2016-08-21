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

import com.helger.rnio.INioHandler;
import com.helger.rnio.IWriteHandler;

/**
 * A simple sender of data. Will try to send all data with no timeout.
 * <p>
 * Subclass this sender and implement <code>done()</code> to do any work after
 * the data has been sent.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public abstract class AbstractSimpleBlockSender extends AbstractSocketHandlerBase <SocketChannel> implements IWriteHandler
{
  private final ByteBuffer buf;
  private final Logger logger = Logger.getLogger ("org.khelekore.rnio");

  /**
   * @param sc
   *        the channel to handle
   * @param nioHandler
   *        the NioHandler
   * @param buf
   *        the ByteBuffer to send
   * @param timeout
   *        the timeout in millis, may be null if no timeout is wanted.
   */
  public AbstractSimpleBlockSender (final SocketChannel sc,
                            final INioHandler nioHandler,
                            final ByteBuffer buf,
                            final Long timeout)
  {
    super (sc, nioHandler, timeout);
    this.buf = buf;
  }

  /**
   * Get the buffer we are sending data from.
   *
   * @return the ByteBuffer with the data that is being sent
   */
  public ByteBuffer getBuffer ()
  {
    return buf;
  }

  public void write ()
  {
    try
    {
      int written = 0;
      do
      {
        written = sc.write (buf);
      } while (buf.hasRemaining () && written > 0);
      if (buf.hasRemaining ())
        register ();
      else
        done ();
    }
    catch (final IOException e)
    {
      handleIOException (e);
    }
  }

  /**
   * Handle the exception, default is to log it and to close the channel.
   *
   * @param e
   *        the IOException that is the cause of data write failure
   */
  public void handleIOException (final IOException e)
  {
    logger.log (Level.WARNING, "Failed to send data", e);
    Closer.close (sc, logger);
  }

  /**
   * The default is to do nothing, override in subclasses if needed.
   */
  public void done ()
  {
    // empty
  }

  /**
   * Register writeWait on the nioHandler
   */
  public void register ()
  {
    nioHandler.waitForWrite (sc, this);
  }
}
