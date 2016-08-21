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
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import com.helger.rnio.IAcceptHandler;
import com.helger.rnio.INioHandler;

/**
 * A standard acceptor.
 * <p>
 * This AcceptHandler will never timeout, will never use a separate thread and
 * will keep accepting connections until you remove it.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class Acceptor extends AbstractSocketHandlerBase <ServerSocketChannel> implements IAcceptHandler
{
  private final IAcceptorListener listener;

  /**
   * Create a new Acceptor that will wait for accepts on the given channel.
   *
   * @param ssc
   *        the channel to accept connections from
   * @param nioHandler
   *        the NioHandler to use for waiting
   * @param listener
   *        the listener waiting for connections
   */
  public Acceptor (final ServerSocketChannel ssc, final INioHandler nioHandler, final IAcceptorListener listener)
  {
    super (ssc, nioHandler, null);
    this.listener = listener;
  }

  /**
   * Returns the class name and the channel we are using.
   */
  @Override
  public String getDescription ()
  {
    return getClass ().getSimpleName () + ": channel: " + sc;
  }

  /**
   * Accept a SocketChannel.
   */
  public void accept ()
  {
    try
    {
      final SocketChannel s = sc.accept ();
      s.configureBlocking (false);
      listener.connectionAccepted (s);
      register ();
    }
    catch (final IOException e)
    {
      throw new RuntimeException ("Got some IOException", e);
    }
  }

  /**
   * Register OP_ACCEPT with the selector.
   */
  public void register ()
  {
    nioHandler.waitForAccept (sc, this);
  }
}
