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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executors;

import com.helger.rnio.BufferHandler;
import com.helger.rnio.NioHandler;
import com.helger.rnio.impl.AcceptingServer;
import com.helger.rnio.impl.AcceptorListener;
import com.helger.rnio.impl.CachingBufferHandler;
import com.helger.rnio.impl.SimpleBlockReader;
import com.helger.rnio.impl.SimpleBlockSender;

/**
 * An echo server built using rnio. This echo server will handle many concurrent
 * clients without any problems.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class EchoServer
{
  private final AcceptingServer as;
  private final BufferHandler bufferHandler;
  private final AcceptListener acceptHandler;

  private final ByteBuffer QUIT = ByteBuffer.wrap ("quit\r\n".getBytes ("UTF-8"));

  /**
   * Start a new EchoServer
   *
   * @param args
   *        the command line arguments
   */
  public static void main (final String [] args)
  {
    int port = 9999;

    if (args.length > 0)
      port = Integer.parseInt (args[0]);

    try
    {
      final EchoServer es = new EchoServer (port);
      es.start ();
    }
    catch (final IOException e)
    {
      e.printStackTrace ();
    }
  }

  /**
   * Create a new EchoServer listening on the given port
   *
   * @param port
   *        the port lnumber to listen on
   * @throws IOException
   *         if creating the server fails
   */
  public EchoServer (final int port) throws IOException
  {
    bufferHandler = new CachingBufferHandler ();
    acceptHandler = new AcceptListener ();
    as = new AcceptingServer (null, port, acceptHandler, Executors.newCachedThreadPool (), 1, Long.valueOf (15000));
  }

  /**
   * Start listening for connections
   */
  public void start ()
  {
    as.start ();
  }

  private void quit ()
  {
    as.shutdown ();
  }

  private Long getTimeout ()
  {
    final long now = System.currentTimeMillis ();
    return Long.valueOf (now + 60 * 1000);
  }

  private class AcceptListener implements AcceptorListener
  {
    public void connectionAccepted (final SocketChannel sc) throws IOException
    {
      final Reader rh = new Reader (sc, as.getNioHandler (), getTimeout ());
      rh.register ();
    }
  }

  private class Reader extends SimpleBlockReader
  {
    public Reader (final SocketChannel sc, final NioHandler nioHandler, final Long timeout)
    {
      super (sc, nioHandler, timeout);
    }

    /** Use the direct byte buffers from the bufferHandler */
    @Override
    public ByteBuffer getByteBuffer ()
    {
      return bufferHandler.getBuffer ();
    }

    /** Cache the ByteBuffer again */
    @Override
    public void putByteBuffer (final ByteBuffer buf)
    {
      bufferHandler.putBuffer (buf);
    }

    @Override
    public void channelClosed ()
    {
      closed ();
    }

    @Override
    public void handleBufferRead (final ByteBuffer buf)
    {
      if (quitMessage (buf))
      {
        quit ();
      }
      else
      {
        final Writer writer = new Writer (sc, nioHandler, buf, this, getTimeout ());
        writer.write ();
      }
    }

    private boolean quitMessage (final ByteBuffer buf)
    {
      return buf.compareTo (QUIT) == 0;
    }
  }

  private class Writer extends SimpleBlockSender
  {
    private final Reader reader;

    public Writer (final SocketChannel sc,
                   final NioHandler nioHandler,
                   final ByteBuffer buf,
                   final Reader reader,
                   final Long timeout)
    {
      super (sc, nioHandler, buf, timeout);
      this.reader = reader;
    }

    @Override
    public void done ()
    {
      bufferHandler.putBuffer (getBuffer ());
      reader.register ();
    }

    @Override
    public void closed ()
    {
      bufferHandler.putBuffer (getBuffer ());
      super.closed ();
    }
  }
}
