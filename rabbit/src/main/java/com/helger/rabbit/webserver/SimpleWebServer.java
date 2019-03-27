package com.helger.rabbit.webserver;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.helger.rabbit.util.ITrafficLogger;
import com.helger.rabbit.util.SimpleTrafficLogger;
import com.helger.rnio.IBufferHandler;
import com.helger.rnio.INioHandler;
import com.helger.rnio.IStatisticsHolder;
import com.helger.rnio.impl.Acceptor;
import com.helger.rnio.impl.BasicStatisticsHolder;
import com.helger.rnio.impl.CachingBufferHandler;
import com.helger.rnio.impl.IAcceptorListener;
import com.helger.rnio.impl.MultiSelectorNioHandler;
import com.helger.rnio.impl.SimpleThreadFactory;

/**
 * A simple web server that serves static resources.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class SimpleWebServer
{
  private File dir;
  private final int port;
  private final INioHandler nioHandler;
  private final ITrafficLogger trafficLogger = new SimpleTrafficLogger ();
  private final IBufferHandler bufferHandler = new CachingBufferHandler ();

  /**
   * Start a web server using the port and base dir given as arguments.
   * 
   * @param args
   *        the command line arguments
   */
  public static void main (final String [] args)
  {
    if (args.length != 2)
    {
      usage ();
      return;
    }
    try
    {
      final int port = Integer.parseInt (args[0]);
      final SimpleWebServer sws = new SimpleWebServer (port, args[1]);
      sws.start ();
    }
    catch (final IOException e)
    {
      e.printStackTrace ();
    }
  }

  private static void usage ()
  {
    System.err.println ("java " + SimpleWebServer.class.getName () + " <port> <dir>");
  }

  /**
   * Start a web server listening on the given port and serving files from the
   * given path. The web server will not serve requests until
   * <code>start ()</code> is called.
   * 
   * @param port
   *        the port to listen on
   * @param path
   *        the directory to serve resources from
   * @throws IOException
   *         if server setup fails
   */
  public SimpleWebServer (final int port, final String path) throws IOException
  {
    this.port = port;
    dir = new File (path);
    if (!(dir.exists () && dir.isDirectory ()))
      throw new IOException (dir + " is not an existing directory");
    dir = dir.getCanonicalFile ();
    final ExecutorService es = Executors.newCachedThreadPool ();
    final IStatisticsHolder sh = new BasicStatisticsHolder ();
    nioHandler = new MultiSelectorNioHandler (es, sh, 4, Long.valueOf (15000L));
  }

  /**
   * Start serving requests.
   */
  public void start ()
  {
    nioHandler.start (new SimpleThreadFactory ());
    setupServerSocket ();
  }

  private void setupServerSocket ()
  {
    try
    {
      final ServerSocketChannel ssc = ServerSocketChannel.open ();
      ssc.configureBlocking (false);
      ssc.socket ().bind (new InetSocketAddress (port));
      final IAcceptorListener acceptListener = new AcceptListener ();
      final Acceptor acceptor = new Acceptor (ssc, nioHandler, acceptListener);
      acceptor.register ();
    }
    catch (final IOException e)
    {
      throw new RuntimeException ("Failed to setup server socket", e);
    }
  }

  private class AcceptListener implements IAcceptorListener
  {
    public void connectionAccepted (final SocketChannel sc) throws IOException
    {
      new Connection (SimpleWebServer.this, sc).readRequest ();
    }
  }

  /**
   * Get the directory files are served from.
   * 
   * @return the root directory for resources
   */
  public File getBaseDir ()
  {
    return dir;
  }

  /**
   * Get the BufferHandler used by this web server.
   * 
   * @return the BufferHandler
   */
  public IBufferHandler getBufferHandler ()
  {
    return bufferHandler;
  }

  /**
   * Get the SelectorRunner used by this web server.
   * 
   * @return the NioHandler in use
   */
  public INioHandler getNioHandler ()
  {
    return nioHandler;
  }

  /**
   * Get the TrafficLogger used by this web server.
   * 
   * @return the TrafficLogger in use
   */
  public ITrafficLogger getTrafficLogger ()
  {
    return trafficLogger;
  }
}
