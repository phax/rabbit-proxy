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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ExecutorService;

import com.helger.rnio.NioHandler;
import com.helger.rnio.StatisticsHolder;

/**
 * A basic server for rnio.
 * <p>
 * This server will create a {@link MultiSelectorNioHandler} using a
 * {@link BasicStatisticsHolder} and the ExecutorService you pass. <br>
 * When you start this server it will begin to listen for socket connections on
 * the specified InetAddress and port and hand off new socket connections to the
 * {@link AcceptorListener}
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class AcceptingServer
{
  private final ServerSocketChannel ssc;
  private final AcceptorListener listener;
  private final NioHandler nioHandler;

  /**
   * Create a new server using the parameters given.
   *
   * @param addr
   *        the InetAddress to bind to, may be null for wildcard address
   * @param port
   *        the port number to bind to
   * @param listener
   *        the client that will handle the accepted sockets
   * @param es
   *        the ExecutorService to use for the NioHandler
   * @param selectorThreads
   *        the number of threads that the NioHandler will use
   * @param defaultTimeout
   *        the default timeout value for the NioHandler
   * @throws IOException
   *         if network setup fails
   */
  public AcceptingServer (final InetAddress addr,
                          final int port,
                          final AcceptorListener listener,
                          final ExecutorService es,
                          final int selectorThreads,
                          final Long defaultTimeout) throws IOException
  {
    ssc = ServerSocketChannel.open ();
    ssc.configureBlocking (false);
    final ServerSocket ss = ssc.socket ();
    ss.bind (new InetSocketAddress (addr, port));
    this.listener = listener;
    final StatisticsHolder stats = new BasicStatisticsHolder ();
    nioHandler = new MultiSelectorNioHandler (es, stats, selectorThreads, defaultTimeout);
  }

  /**
   * Start the NioHandler and register to accept new socket connections.
   */
  public void start ()
  {
    nioHandler.start (new SimpleThreadFactory ());
    final Acceptor acceptor = new Acceptor (ssc, nioHandler, listener);
    acceptor.register ();
  }

  /**
   * Shutdown the NioHandler.
   */
  public void shutdown ()
  {
    nioHandler.shutdown ();
  }

  /**
   * Get the NioHandler in use by this server.
   *
   * @return the NioHandler used by this server
   */
  public NioHandler getNioHandler ()
  {
    return nioHandler;
  }
}
