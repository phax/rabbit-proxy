package com.helger.rabbit.io;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.collection.attr.StringMap;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.util.Counter;
import com.helger.rnio.INioHandler;
import com.helger.rnio.IReadHandler;

/**
 * A class to handle the connections to the net. Tries to reuse connections
 * whenever possible.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ConnectionHandler
{
  private static final Logger LOGGER = LoggerFactory.getLogger (ConnectionHandler.class);

  // The counter to use.
  private final Counter counter;

  // The resolver to use
  private final IProxyChain proxyChain;

  // The available connections.
  private final Map <Address, List <WebConnection>> activeConnections;

  // The channels waiting for closing
  private final Map <WebConnection, CloseListener> wc2closer;

  // the keepalivetime.
  private long keepaliveTime = 1000;

  // should we use pipelining...
  private boolean usePipelining = true;

  // the nio handler
  private final INioHandler nioHandler;

  // the socket binder
  private SocketBinder socketBinder = new DefaultBinder ();

  // The tcp no delay flag
  private boolean setTcpNoDelay;

  /**
   * Create a new ConnectionHandler.
   *
   * @param counter
   *        the Counter to update with statistics
   * @param proxyChain
   *        the ProxyChain to use when doing dns lookups
   * @param nioHandler
   *        the NioHandler to use for network and background tasks
   */
  public ConnectionHandler (final Counter counter, final IProxyChain proxyChain, final INioHandler nioHandler)
  {
    this.counter = counter;
    this.proxyChain = proxyChain;
    this.nioHandler = nioHandler;

    activeConnections = new HashMap <> ();
    wc2closer = new ConcurrentHashMap <> ();
  }

  /**
   * Set the keep alive time for this handler.
   *
   * @param milis
   *        the keep alive time in miliseconds.
   */
  public void setKeepaliveTime (final long milis)
  {
    keepaliveTime = milis;
  }

  /**
   * Get the current keep alive time.
   *
   * @return the keep alive time in miliseconds.
   */
  public long getKeepaliveTime ()
  {
    return keepaliveTime;
  }

  /**
   * Get a copy of the current connections.
   *
   * @return the current connections
   */
  public Map <Address, List <WebConnection>> getActiveConnections ()
  {
    final Map <Address, List <WebConnection>> ret = new HashMap <> ();
    synchronized (activeConnections)
    {
      for (final Map.Entry <Address, List <WebConnection>> me : activeConnections.entrySet ())
      {
        ret.put (me.getKey (), Collections.unmodifiableList (me.getValue ()));
      }
    }
    return ret;
  }

  /**
   * Get a WebConnection for the given header.
   *
   * @param header
   *        the HttpHeader containing the URL to connect to.
   * @param wcl
   *        the Listener that wants the connection.
   */
  public void getConnection (final HttpHeader header, final WebConnectionListener wcl)
  {
    // TODO: should we use the Host: header if its available? probably...
    final String requri = header.getRequestURI ();
    URL url;
    try
    {
      url = new URL (requri);
    }
    catch (final MalformedURLException e)
    {
      wcl.failed (e);
      return;
    }
    final Resolver resolver = proxyChain.getResolver (requri);
    final int port = url.getPort () > 0 ? url.getPort () : 80;
    final int rport = resolver.getConnectPort (port);

    resolver.getInetAddress (url, new InetAddressListener ()
    {
      public void lookupDone (final InetAddress ia)
      {
        final Address a = new Address (ia, rport);
        getConnection (header, wcl, a);
      }

      public void unknownHost (final Exception e)
      {
        wcl.failed (e);
      }
    });
  }

  private SocketBinder getSocketBinder ()
  {
    return socketBinder;
  }

  private void getConnection (final HttpHeader header, final WebConnectionListener wcl, final Address a)
  {
    WebConnection wc;
    counter.inc ("WebConnections used");
    String method = header.getMethod ();

    if (method != null)
    {
      // since we should not retry POST (and other) we
      // have to get a fresh connection for them..
      method = method.trim ();
      if (!(method.equals ("GET") || method.equals ("HEAD")))
      {
        wc = new WebConnection (a, getSocketBinder (), counter);
      }
      else
      {
        wc = getPooledConnection (a, activeConnections);
        if (wc == null)
          wc = new WebConnection (a, getSocketBinder (), counter);
      }
      try
      {
        wc.connect (nioHandler, wcl, setTcpNoDelay);
      }
      catch (final IOException e)
      {
        wcl.failed (e);
      }
    }
    else
    {
      final String err = "No method specified: " + header;
      wcl.failed (new IllegalArgumentException (err));
    }
  }

  private WebConnection getPooledConnection (final Address a, final Map <Address, List <WebConnection>> conns)
  {
    synchronized (conns)
    {
      final List <WebConnection> pool = conns.get (a);
      if (pool != null)
      {
        if (pool.size () > 0)
        {
          final WebConnection wc = pool.remove (pool.size () - 1);
          if (pool.isEmpty ())
            conns.remove (a);
          return unregister (wc);
        }
      }
    }
    return null;
  }

  private WebConnection unregister (final WebConnection wc)
  {
    CloseListener closer;
    closer = wc2closer.remove (wc);
    if (closer != null)
      nioHandler.cancel (wc.getChannel (), closer);
    return wc;
  }

  private void removeFromPool (final WebConnection wc, final Map <Address, List <WebConnection>> conns)
  {
    synchronized (conns)
    {
      final List <WebConnection> pool = conns.get (wc.getAddress ());
      if (pool != null)
      {
        pool.remove (wc);
        if (pool.isEmpty ())
          conns.remove (wc.getAddress ());
      }
    }
  }

  /**
   * Return a WebConnection to the pool so that it may be reused.
   *
   * @param wc
   *        the WebConnection to return.
   */
  public void releaseConnection (final WebConnection wc)
  {
    counter.inc ("WebConnections released");
    if (!wc.getChannel ().isOpen ())
    {
      return;
    }

    final Address a = wc.getAddress ();
    if (!wc.getKeepalive ())
    {
      closeWebConnection (wc);
      return;
    }

    synchronized (wc)
    {
      wc.setReleased ();
    }
    synchronized (activeConnections)
    {
      List <WebConnection> pool = activeConnections.get (a);
      if (pool == null)
      {
        pool = new ArrayList <> ();
        activeConnections.put (a, pool);
      }
      else
      {
        if (pool.contains (wc))
        {
          final String err = "web connection already added to pool: " + wc;
          throw new IllegalStateException (err);
        }
      }
      pool.add (wc);
      final CloseListener cl = new CloseListener (wc);
      wc2closer.put (wc, cl);
      cl.register ();
    }
  }

  private void closeWebConnection (final WebConnection wc)
  {
    if (wc == null)
      return;
    if (!wc.getChannel ().isOpen ())
      return;
    try
    {
      wc.close ();
    }
    catch (final IOException e)
    {
      LOGGER.warn ("Failed to close WebConnection: " + wc, e);
    }
  }

  private class CloseListener implements IReadHandler
  {
    private final WebConnection wc;
    private Long timeout;

    public CloseListener (final WebConnection wc)
    {
      this.wc = wc;
    }

    public void register ()
    {
      timeout = nioHandler.getDefaultTimeout ();
      nioHandler.waitForRead (wc.getChannel (), this);
    }

    public void read ()
    {
      closeChannel ();
    }

    public void closed ()
    {
      closeChannel ();
    }

    public void timeout ()
    {
      closeChannel ();
    }

    public Long getTimeout ()
    {
      return timeout;
    }

    private void closeChannel ()
    {
      try
      {
        wc2closer.remove (wc);
        removeFromPool (wc, activeConnections);
        wc.close ();
      }
      catch (final IOException e)
      {
        LOGGER.warn ("CloseListener: Failed to close web connection", e);
      }
    }

    public boolean useSeparateThread ()
    {
      return false;
    }

    public String getDescription ()
    {
      return "ConnectionHandler$CloseListener: address: " + wc.getAddress ();
    }

    @Override
    public String toString ()
    {
      return getClass ().getSimpleName () + "{wc: " + wc + "}@" + Integer.toString (hashCode (), 16);
    }
  }

  /**
   * Mark a WebConnection ready for pipelining.
   *
   * @param wc
   *        the WebConnection to mark ready for pipelining.
   */
  public void markForPipelining (final WebConnection wc)
  {
    if (!usePipelining)
      return;
    synchronized (wc)
    {
      if (wc.getKeepalive ())
        wc.setMayPipeline (true);
    }
  }

  /**
   * Configure this ConnectionHandler using the given properties.
   *
   * @param config
   *        the properties to read the configuration from
   */
  public void setup (final StringMap config)
  {
    if (config == null)
      return;
    final String kat = config.getOrDefault ("keepalivetime", "1000");
    try
    {
      setKeepaliveTime (Long.parseLong (kat));
    }
    catch (final NumberFormatException e)
    {
      LOGGER.warn ("Bad number for ConnectionHandler keepalivetime: '" + kat + "'");
    }
    final String tcpNoDelay = config.getOrDefault ("use_tcp_no_delay", "false");
    setTcpNoDelay = "true".equalsIgnoreCase (tcpNoDelay);
    String up = config.get ("usepipelining");
    if (up == null)
      up = "true";
    usePipelining = up.equalsIgnoreCase ("true");

    final String bindIP = config.get ("bind_ip");
    if (bindIP != null)
    {
      try
      {
        final InetAddress ia = InetAddress.getByName (bindIP);
        if (ia != null)
        {
          LOGGER.info ("Will bind to: " + ia + " for outgoing traffic");
          socketBinder = new BoundBinder (ia);
        }
      }
      catch (final IOException e)
      {
        LOGGER.error ("Failed to find inet address for: " + bindIP, e);
      }
    }
  }
}
