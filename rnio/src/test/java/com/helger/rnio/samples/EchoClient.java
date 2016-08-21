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
package com.helger.rnio.samples;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.helger.commons.io.stream.StreamHelper;
import com.helger.rnio.INioHandler;
import com.helger.rnio.IStatisticsHolder;
import com.helger.rnio.impl.AbstractSimpleBlockReader;
import com.helger.rnio.impl.AbstractSimpleBlockSender;
import com.helger.rnio.impl.BasicStatisticsHolder;
import com.helger.rnio.impl.MultiSelectorNioHandler;
import com.helger.rnio.impl.SimpleThreadFactory;

/**
 * An echo client built using rnio.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class EchoClient
{
  private final BufferedReader input;
  private final PrintWriter output;
  private final SocketChannel serverChannel;
  private final INioHandler nioHandler;
  private final Thread inputReaderThread;
  private final Logger logger = Logger.getLogger ("org.khelekore.rnio.echoserver");

  /**
   * Create a new EchoClient.
   *
   * @param host
   *        the server to connect to
   * @param port
   *        the port number the server is listening on
   * @param input
   *        the reader used to read data from
   * @param output
   *        the writer to write the result to
   * @throws IOException
   *         if network setup fails
   */
  public EchoClient (final String host,
                     final int port,
                     final BufferedReader input,
                     final PrintWriter output) throws IOException
  {
    this.input = input;
    this.output = output;

    // TODO: could use nioHandler to wait for connect.
    serverChannel = SocketChannel.open (new InetSocketAddress (host, port));
    serverChannel.configureBlocking (false);

    inputReaderThread = new Thread (new InputReader ());

    final ExecutorService es = Executors.newCachedThreadPool ();
    final IStatisticsHolder stats = new BasicStatisticsHolder ();
    final Long timeout = Long.valueOf (15000);
    nioHandler = new MultiSelectorNioHandler (es, stats, 1, timeout);
  }

  /**
   * Start the client.
   */
  public void start ()
  {
    nioHandler.start (new SimpleThreadFactory ());
    final ServerReader sr = new ServerReader (serverChannel, nioHandler);
    nioHandler.waitForRead (serverChannel, sr);
    inputReaderThread.start ();
  }

  /**
   * Try to shutdown the client in a nice way
   */
  public void shutdown ()
  {
    nioHandler.shutdown ();
    StreamHelper.close (serverChannel);
    // would want to shutdown inputReaderThread but it will be
    // blocked in BufferedReader.readLine and that one is not
    // inerruptible.
  }

  private class ServerReader extends AbstractSimpleBlockReader
  {
    public ServerReader (final SocketChannel sc, final INioHandler nioHandler)
    {
      super (sc, nioHandler, null);
    }

    @Override
    public void channelClosed ()
    {
      logger.info ("Server shut down");
      shutdown ();
    }

    @Override
    public void handleBufferRead (final ByteBuffer buf) throws IOException
    {
      final String s = new String (buf.array (), buf.position (), buf.remaining (), "UTF-8");
      output.println ("Server sent: " + s);
      output.flush ();
      nioHandler.waitForRead (sc, this);
    }
  }

  private class Sender extends AbstractSimpleBlockSender
  {
    public Sender (final INioHandler nioHandler, final ByteBuffer buf)
    {
      super (serverChannel, nioHandler, buf, null);
    }
  }

  private class InputReader implements Runnable
  {
    public void run ()
    {
      try
      {
        while (true)
        {
          final String line = input.readLine ();
          if (line == null || !serverChannel.isOpen ())
            return;
          final byte [] bytes = line.getBytes ("UTF-8");
          final ByteBuffer buf = ByteBuffer.wrap (bytes);
          final Sender s = new Sender (nioHandler, buf);
          // if we fail to send everything before we read the next
          // line we may end up with several writers, but this is
          // an example, handle concurrency in real apps.
          s.write ();
        }
      }
      catch (final IOException e)
      {
        logger.log (Level.WARNING, "Failed to read", e);
      }
      finally
      {
        shutdown ();
      }
    }
  }

  /**
   * The entry point for the EchoClient
   *
   * @param args
   *        the command line arguments
   */
  public static void main (final String [] args)
  {
    if (args.length < 2)
    {
      usage ();
      return;
    }
    final String host = args[0];
    final int port = Integer.parseInt (args[1]);
    final InputStreamReader isr = new InputStreamReader (System.in);
    final BufferedReader br = new BufferedReader (isr);
    final PrintWriter pw = new PrintWriter (System.out);
    try
    {
      final EchoClient ec = new EchoClient (host, port, br, pw);
      ec.start ();
    }
    catch (final IOException e)
    {
      e.printStackTrace ();
    }
  }

  private static void usage ()
  {
    System.err.println ("java " + EchoClient.class.getName () + " <host> <port>");
  }
}
