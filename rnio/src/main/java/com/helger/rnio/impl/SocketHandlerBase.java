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

import java.nio.channels.SelectableChannel;
import java.util.logging.Logger;

import com.helger.rnio.NioHandler;
import com.helger.rnio.SocketChannelHandler;

/**
 * A socket handler that never times out and always runs on the selector thread.
 *
 * @param <T>
 *        the type of chanel that is handled
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public abstract class SocketHandlerBase <T extends SelectableChannel> implements SocketChannelHandler
{
  /** The actual channel */
  public final T sc;
  /** The NioHandler used to wait for opeations. */
  public final NioHandler nioHandler;
  /** The timeout for the current operation */
  public final Long timeout;

  private final Logger logger = Logger.getLogger ("org.khelekore.rnio");

  /**
   * @param sc
   *        the channel to handle
   * @param nioHandler
   *        the NioHandler
   * @param timeout
   *        the timeout in millis, may be null if no timeout is wanted.
   */
  public SocketHandlerBase (final T sc, final NioHandler nioHandler, final Long timeout)
  {
    this.sc = sc;
    this.nioHandler = nioHandler;
    this.timeout = timeout;
  }

  /**
   * Will return null to indicate no timeout on accepts.
   */
  public Long getTimeout ()
  {
    return timeout;
  }

  /**
   * Returns the class name.
   */
  public String getDescription ()
  {
    return getClass ().getSimpleName ();
  }

  /**
   * Will always run on the selector thread so return false.
   *
   * @return false
   */
  public boolean useSeparateThread ()
  {
    return false;
  }

  /**
   * Handle timeouts. Default implementation just calls closed().
   */
  public void timeout ()
  {
    closed ();
  }

  public void closed ()
  {
    Closer.close (sc, logger);
  }
}
