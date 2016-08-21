package com.helger.rabbit.proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingException;

import com.helger.commons.url.SMap;
import com.helger.rabbit.cache.ICache;
import com.helger.rabbit.cache.ncache.NCache;
import com.helger.rabbit.dns.IDNSHandler;
import com.helger.rabbit.dns.DNSJavaHandler;
import com.helger.rabbit.dns.DNSSunHandler;
import com.helger.rabbit.handler.IHandlerFactory;
import com.helger.rabbit.http.HttpDateParser;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.ProxiedProxyChain;
import com.helger.rabbit.httpio.SimpleProxyChain;
import com.helger.rabbit.io.ConnectionHandler;
import com.helger.rabbit.io.IProxyChain;
import com.helger.rabbit.io.ProxyChainFactory;
import com.helger.rabbit.io.WebConnection;
import com.helger.rabbit.io.WebConnectionListener;
import com.helger.rabbit.util.Config;
import com.helger.rabbit.util.Counter;
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
 * A filtering and caching http proxy.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class HttpProxy
{

  /** Current version */
  public static final String VERSION = "RabbIT proxy version 4.11";

  /** The current config of this proxy. */
  private Config config;

  /** The time this proxy was started. Time in millis. */
  private long started;

  /** The identity of this server. */
  private String serverIdentity = VERSION;

  /** The logger of this proxy. */
  private final Logger logger = Logger.getLogger (getClass ().getName ());

  /** The access logger of the proxy */
  private final ProxyLogger accessLogger = new ProxyLogger ();

  /** The traffic loggers of the proxy */
  private ClientTrafficLoggerHandler clientTrafficLoggers;

  /** The id sequence for acceptors. */
  private static int acceptorId = 0;

  /** The dns handler */
  private IDNSHandler dnsHandler;

  /** The socket access controller. */
  private SocketAccessController socketAccessController;

  /** The http header filterer. */
  private HttpHeaderFilterer httpHeaderFilterer;

  /** The connection handler */
  private ConnectionHandler conhandler;

  /** The local adress of the proxy. */
  private final InetAddress localhost;

  /** The port the proxy is using. */
  private int port = -1;

  /** The proxy chain we are using */
  private IProxyChain proxyChain;

  /** The serversocket the proxy is using. */
  private ServerSocketChannel ssc = null;

  private INioHandler nioHandler;

  /** The buffer handlers. */
  private final IBufferHandler bufferHandler = new CachingBufferHandler ();

  /** If this proxy is using strict http parsing. */
  private boolean strictHttp = true;

  /** Maximum number of concurrent connections */
  private int maxConnections = 50;

  /** The counter of events. */
  private final Counter counter = new Counter ();

  /** The cache-handler */
  private NCache <HttpHeader, HttpHeader> cache;

  /** Are we allowed to proxy ssl? */
  protected boolean proxySSL = false;
  /** The List of acceptable ssl-ports. */
  protected List <Integer> sslports = null;

  /** The handler factory handler. */
  private HandlerFactoryHandler handlerFactoryHandler;

  /** All the currently active connections. */
  private final List <Connection> connections = new ArrayList<> ();

  /** The total traffic in and out of this proxy. */
  private final TrafficLoggerHandler tlh = new TrafficLoggerHandler ();

  /** The factory for http header generator */
  private HttpGeneratorFactory hgf;

  /** The ClassLoader to use when loading handlers */
  private ClassLoader libLoader;

  /**
   * Create a new HttpProxy.
   *
   * @throws UnknownHostException
   *         if the local host address can not be determined
   */
  public HttpProxy () throws UnknownHostException
  {
    localhost = InetAddress.getLocalHost ();
  }

  /**
   * Set the config file to use for this proxy.
   *
   * @param conf
   *        the name of the file to use for proxy configuration.
   * @throws IOException
   *         if the config file can not be read
   */
  public void setConfig (final String conf) throws IOException
  {
    setConfig (new Config (conf));
  }

  private void setupLogging ()
  {
    final SMap logProps = config.getProperties ("logging");
    try
    {
      accessLogger.setup (logProps);
    }
    catch (final IOException e)
    {
      logger.log (Level.SEVERE, "Failed to configure logging", e);
    }
  }

  private void setupDateParsing ()
  {
    HttpDateParser.setOffset (getOffset ());
  }

  private void setup3rdPartyClassLoader ()
  {
    final ProxyClassLoaderHelper clh = new ProxyClassLoaderHelper ();
    final String libDirs = config.getProperty (getClass ().getName (), "libs", "libs");
    libLoader = clh.get3rdPartyClassLoader (libDirs);
  }

  private void setupDNSHandler ()
  {
    /*
     * DNSJava have problems with international versions of windows. so we
     * default to the default dns handler.
     */
    final String osName = System.getProperty ("os.name");
    if (osName.toLowerCase ().indexOf ("windows") > -1)
    {
      logger.warning ("This seems like a windows system, " + "will use default sun handler for DNS");
      dnsHandler = new DNSSunHandler ();
    }
    else
    {
      final String dnsHandlerClass = config.getProperty (getClass ().getName (),
                                                         "dnsHandler",
                                                         DNSJavaHandler.class.getName ());
      try
      {
        final Class <? extends IDNSHandler> clz = load3rdPartyClass (dnsHandlerClass, IDNSHandler.class);
        dnsHandler = clz.newInstance ();
        dnsHandler.setup (config.getProperties ("dns"));
      }
      catch (final Exception e)
      {
        logger.warning ("Unable to create and setup dns handler: " + e + ", will try to use default instead.");
        dnsHandler = new DNSJavaHandler ();
        dnsHandler.setup (config.getProperties ("dns"));
      }
    }
  }

  private void setupNioHandler ()
  {
    final String section = getClass ().getName ();
    final int cpus = Runtime.getRuntime ().availableProcessors ();
    final int threads = getInt (section, "num_selector_threads", cpus);
    final ExecutorService es = Executors.newCachedThreadPool ();
    final IStatisticsHolder sh = new BasicStatisticsHolder ();
    final Long timeout = Long.valueOf (15000);
    try
    {
      nioHandler = new MultiSelectorNioHandler (es, sh, threads, timeout);
    }
    catch (final IOException e)
    {
      logger.log (Level.SEVERE, "Failed to create the NioHandler", e);
      stop ();
    }
  }

  private IProxyChain setupProxyChainFromFactory (final String pcf)
  {
    try
    {
      final Class <? extends ProxyChainFactory> clz = load3rdPartyClass (pcf, ProxyChainFactory.class);
      final ProxyChainFactory factory = clz.newInstance ();
      final SMap props = config.getProperties (pcf);
      return factory.getProxyChain (props, nioHandler, dnsHandler, logger);
    }
    catch (final Exception e)
    {
      logger.log (Level.WARNING, "Unable to create the proxy chain " + "will fall back to the default one.", e);
    }
    return null;
  }

  /* TODO: remove this method, only kept for backwards compability. */
  private IProxyChain setupProxiedProxyChain (final String pname, final String pport, final String pauth)
  {
    try
    {
      final InetAddress proxy = dnsHandler.getInetAddress (pname);
      try
      {
        final int port = Integer.parseInt (pport);
        return new ProxiedProxyChain (proxy, port, pauth);
      }
      catch (final NumberFormatException e)
      {
        logger.severe ("Strange proxyport: '" + pport + "', will not chain");
      }
    }
    catch (final UnknownHostException e)
    {
      logger.severe ("Unknown proxyhost: '" + pname + "', will not chain");
    }
    return null;
  }

  /**
   * Configure the chained proxy rabbit is using (if any).
   */
  private void setupProxyConnection ()
  {
    final String sec = getClass ().getName ();
    final String pcf = config.getProperty (sec, "proxy_chain_factory", "").trim ();
    final String pname = config.getProperty (sec, "proxyhost", "").trim ();
    final String pport = config.getProperty (sec, "proxyport", "").trim ();
    final String pauth = config.getProperty (sec, "proxyauth");

    if (!"".equals (pcf))
    {
      proxyChain = setupProxyChainFromFactory (pcf);
    }
    else
      if (!pname.equals ("") && !pport.equals (""))
      {
        proxyChain = setupProxiedProxyChain (pname, pport, pauth);
      }
    if (proxyChain == null)
      proxyChain = new SimpleProxyChain (nioHandler, dnsHandler);
  }

  private void setupResources ()
  {
    final SMap props = config.getProperties ("data_sources");
    if (props == null || props.isEmpty ())
      return;
    final String resources = props.getOrDefault ("resources", "");
    if (resources.isEmpty ())
      return;
    try
    {
      final ResourceLoader rl = new ResourceLoader ();
      for (final String r : resources.split (","))
        rl.setupResource (r, config.getProperties (r), this);
    }
    catch (final NamingException e)
    {
      logger.log (Level.WARNING, "Failed to setup initial context", e);
    }
  }

  private void setupCache ()
  {
    final SMap props = config.getProperties (NCache.class.getName ());
    final HttpHeaderFileHandler hhfh = new HttpHeaderFileHandler ();
    try
    {
      cache = new NCache<> (props, hhfh, hhfh);
      cache.start ();
    }
    catch (final IOException e)
    {
      logger.log (Level.SEVERE, "Failed to setup cache", e);
    }
  }

  /**
   * Configure the SSL support RabbIT should have.
   */
  private void setupSSLSupport ()
  {
    String ssl = config.getProperty ("sslhandler", "allowSSL", "no");
    ssl = ssl.trim ();
    if (ssl.equals ("no"))
    {
      proxySSL = false;
    }
    else
      if (ssl.equals ("yes"))
      {
        proxySSL = true;
        sslports = null;
      }
      else
      {
        proxySSL = true;
        // ok, try to get the portnumbers.
        sslports = new ArrayList<> ();
        final StringTokenizer st = new StringTokenizer (ssl, ",");
        while (st.hasMoreTokens ())
        {
          String s = null;
          try
          {
            final Integer port = new Integer (s = st.nextToken ());
            sslports.add (port);
          }
          catch (final NumberFormatException e)
          {
            logger.warning ("bad number: '" + s + "' for ssl port, ignoring.");
          }
        }
      }
  }

  /**
   * Toogle the strict http flag.
   *
   * @param b
   *        the new mode for the strict http flag
   */
  public void setStrictHttp (final boolean b)
  {
    this.strictHttp = b;
  }

  /**
   * Check if strict http is turned on or off.
   *
   * @return the strict http flag
   */
  public boolean getStrictHttp ()
  {
    return strictHttp;
  }

  /**
   * Configure the maximum number of simultanious connections we handle
   */
  private void setupMaxConnections ()
  {
    final String mc = config.getProperty (getClass ().getName (), "maxconnections", "500").trim ();
    try
    {
      maxConnections = Integer.parseInt (mc);
    }
    catch (final NumberFormatException e)
    {
      logger.warning ("bad number for maxconnections: '" + mc + "', using old value: " + maxConnections);
    }
  }

  private void setupConnectionHandler ()
  {
    if (nioHandler == null)
    {
      logger.info ("nioHandler == null " + this);
      return;
    }
    conhandler = new ConnectionHandler (counter, proxyChain, nioHandler);
    final String section = conhandler.getClass ().getName ();
    conhandler.setup (config.getProperties (section));
  }

  private void setupHttpGeneratorFactory ()
  {
    final String def = StandardHttpGeneratorFactory.class.getName ();
    final String hgfClass = config.getProperty (getClass ().getName (), "http_generator_factory", def);
    try
    {
      final Class <? extends HttpGeneratorFactory> clz = load3rdPartyClass (hgfClass, HttpGeneratorFactory.class);
      hgf = clz.newInstance ();
    }
    catch (final Exception e)
    {
      logger.log (Level.WARNING,
                  "Unable to create the http generator " + "factory, will fall back to the default one.",
                  e);
      hgf = new StandardHttpGeneratorFactory ();
    }
    final String section = hgf.getClass ().getName ();
    hgf.setup (config.getProperties (section));
  }

  private void setConfig (final Config config)
  {
    this.config = config;
    setupLogging ();
    setupDateParsing ();
    setup3rdPartyClassLoader ();
    setupDNSHandler ();
    setupNioHandler ();
    setupProxyConnection ();
    final String cn = getClass ().getName ();
    serverIdentity = config.getProperty (cn, "serverIdentity", VERSION);
    final String strictHttp = config.getProperty (cn, "StrictHTTP", "true");
    setStrictHttp (strictHttp.equals ("true"));
    setupMaxConnections ();
    setupResources ();
    setupCache ();
    setupSSLSupport ();
    loadClasses ();
    openSocket ();
    setupConnectionHandler ();
    setupHttpGeneratorFactory ();
    logger.info (VERSION + ": Configuration loaded: ready for action.");
  }

  private int getInt (final String section, final String key, final int defaultValue)
  {
    final String defVal = Integer.toString (defaultValue);
    final String configValue = config.getProperty (section, key, defVal).trim ();
    return Integer.parseInt (configValue);
  }

  /**
   * Open a socket on the specified port also make the proxy continue accepting
   * connections.
   */
  private void openSocket ()
  {
    final String section = getClass ().getName ();
    final int tport = getInt (section, "port", 9666);

    final String bindIP = config.getProperty (section, "listen_ip");
    if (tport != port)
    {
      try
      {
        closeSocket ();
        port = tport;
        ssc = ServerSocketChannel.open ();
        ssc.configureBlocking (false);
        if (bindIP == null)
        {
          ssc.socket ().bind (new InetSocketAddress (port));
        }
        else
        {
          final InetAddress ia = InetAddress.getByName (bindIP);
          logger.info ("listening on inetaddress: " + ia + ":" + port + " on inet address: " + ia);
          ssc.socket ().bind (new InetSocketAddress (ia, port));
        }
        final IAcceptorListener listener = new ProxyConnectionAcceptor (acceptorId++, this);
        final Acceptor acceptor = new Acceptor (ssc, nioHandler, listener);
        acceptor.register ();
      }
      catch (final IOException e)
      {
        logger.log (Level.SEVERE, "Failed to open serversocket on port " + port, e);
        stop ();
      }
    }
  }

  /**
   * Closes the serversocket and makes the proxy stop listening for connections.
   */
  private void closeSocket ()
  {
    try
    {
      port = -1;
      if (ssc != null)
      {
        ssc.close ();
        ssc = null;
      }
    }
    catch (final IOException e)
    {
      logger.severe ("Failed to close serversocket on port " + port);
      stop ();
    }
  }

  private void closeNioHandler ()
  {
    if (nioHandler != null)
      nioHandler.shutdown ();
  }

  /**
   * Make sure all filters and handlers are available
   */
  private void loadClasses ()
  {
    final SMap hProps = config.getProperties ("Handlers");
    final SMap chProps = config.getProperties ("CacheHandlers");
    handlerFactoryHandler = new HandlerFactoryHandler (hProps, chProps, config, this);

    final String filters = config.getProperty ("Filters", "accessfilters", "");
    socketAccessController = new SocketAccessController (filters, config, this);

    final String in = config.getProperty ("Filters", "httpinfilters", "");
    final String out = config.getProperty ("Filters", "httpoutfilters", "");
    final String connect = config.getProperty ("Filters", "connectfilters", "");
    httpHeaderFilterer = new HttpHeaderFilterer (in, out, connect, config, this);

    clientTrafficLoggers = new ClientTrafficLoggerHandler (config, this);
  }

  /** Run the proxy in a separate thread. */
  public void start ()
  {
    started = System.currentTimeMillis ();
    nioHandler.start (new SimpleThreadFactory ());
  }

  /** Run the proxy in a separate thread. */
  public void stop ()
  {
    logger.severe ("HttpProxy.stop() called, shutting down");
    synchronized (this)
    {
      closeSocket ();
      // TODO: wait for remaining connections.
      // TODO: as it is now, it will just close connections in the middle.
      closeNioHandler ();
      cache.flush ();
      cache.stop ();
    }
  }

  /**
   * Get the NioHandler that this proxy is using.
   *
   * @return the NioHandler in use
   */
  public INioHandler getNioHandler ()
  {
    return nioHandler;
  }

  /**
   * Get the cache that this proxy is currently using.
   *
   * @return the Cache in use
   */
  public ICache <HttpHeader, HttpHeader> getCache ()
  {
    return cache;
  }

  /**
   * Get the time offset, that is the time between GMT and local time.
   *
   * @return the current time offset in millis
   */
  public long getOffset ()
  {
    return accessLogger.getOffset ();
  }

  /**
   * Get the time this proxy was started.
   *
   * @return the start time as returned from System.currentTimeMillis()
   */
  public long getStartTime ()
  {
    return started;
  }

  ConnectionLogger getConnectionLogger ()
  {
    return accessLogger;
  }

  ServerSocketChannel getServerSocketChannel ()
  {
    return ssc;
  }

  /**
   * Get the current Counter
   *
   * @return the Ćounter in use
   */
  public Counter getCounter ()
  {
    return counter;
  }

  SocketAccessController getSocketAccessController ()
  {
    return socketAccessController;
  }

  HttpHeaderFilterer getHttpHeaderFilterer ()
  {
    return httpHeaderFilterer;
  }

  /**
   * Get the configuration of the proxy.
   *
   * @return the current configuration
   */
  public Config getConfig ()
  {
    return config;
  }

  IHandlerFactory getHandlerFactory (final String mime)
  {
    return handlerFactoryHandler.getHandlerFactory (mime);
  }

  IHandlerFactory getCacheHandlerFactory (final String mime)
  {
    return handlerFactoryHandler.getCacheHandlerFactory (mime);
  }

  /**
   * Get the version of this proxy.
   *
   * @return the version of the proxy
   */
  public String getVersion ()
  {
    return VERSION;
  }

  /**
   * Get the current server identity.
   *
   * @return the current identity
   */
  public String getServerIdentity ()
  {
    return serverIdentity;
  }

  /**
   * Get the local host.
   *
   * @return the InetAddress of the host the proxy is running on.
   */
  public InetAddress getHost ()
  {
    return localhost;
  }

  /**
   * Get the port this proxy is using.
   *
   * @return the port number the proxy is listening on.
   */
  public int getPort ()
  {
    return port;
  }

  /**
   * Get the ProxyChain this proxy is currently using
   *
   * @return the current ProxyChain
   */
  public IProxyChain getProxyChain ()
  {
    return proxyChain;
  }

  /**
   * Try hard to check if the given address matches the proxy. Will use the
   * localhost name and all ip addresses.
   *
   * @param uhost
   *        the host name to check
   * @param urlport
   *        the port number to check
   * @return true if the given hostname and port matches this proxy
   */
  public boolean isSelf (final String uhost, final int urlport)
  {
    if (urlport == getPort ())
    {
      final String proxyhost = getHost ().getHostName ();
      if (uhost.equalsIgnoreCase (proxyhost))
        return true;
      try
      {
        final Enumeration <NetworkInterface> e = NetworkInterface.getNetworkInterfaces ();
        while (e.hasMoreElements ())
        {
          final NetworkInterface ni = e.nextElement ();
          final Enumeration <InetAddress> ei = ni.getInetAddresses ();
          while (ei.hasMoreElements ())
          {
            final InetAddress ia = ei.nextElement ();
            if (ia.getHostAddress ().equalsIgnoreCase (uhost))
              return true;
            if (ia.isLoopbackAddress () && ia.getHostName ().equalsIgnoreCase (uhost))
              return true;
          }
        }
      }
      catch (final SocketException e)
      {
        logger.log (Level.WARNING, "Failed to get network interfaces", e);
      }
    }
    return false;
  }

  /**
   * Get a WebConnection.
   *
   * @param header
   *        the http header to get the host and port from
   * @param wcl
   *        the listener that wants to get the connection.
   */
  public void getWebConnection (final HttpHeader header, final WebConnectionListener wcl)
  {
    conhandler.getConnection (header, wcl);
  }

  /**
   * Release a WebConnection so that it may be reused if possible.
   *
   * @param wc
   *        the WebConnection to release.
   */
  public void releaseWebConnection (final WebConnection wc)
  {
    conhandler.releaseConnection (wc);
  }

  /**
   * Mark a WebConnection for pipelining.
   *
   * @param wc
   *        the WebConnection to mark.
   */
  public void markForPipelining (final WebConnection wc)
  {
    conhandler.markForPipelining (wc);
  }

  /**
   * Add a current connection
   *
   * @param con
   *        the connection
   */
  public void addCurrentConnection (final Connection con)
  {
    synchronized (connections)
    {
      connections.add (con);
    }
  }

  /**
   * Remove a current connection.
   *
   * @param con
   *        the connection
   */
  public void removeCurrentConnection (final Connection con)
  {
    synchronized (connections)
    {
      connections.remove (con);
    }
  }

  /**
   * Get the connection handler.
   *
   * @return the current ConnectionHandler
   */
  public ConnectionHandler getConnectionHandler ()
  {
    return conhandler;
  }

  /**
   * Get all the current connections
   *
   * @return all current connections
   */
  public List <Connection> getCurrentConnections ()
  {
    synchronized (connections)
    {
      return Collections.unmodifiableList (connections);
    }
  }

  /**
   * Update the currently transferred traffic statistics.
   *
   * @param tlh
   *        the traffic statistics for some operation
   */
  protected void updateTrafficLog (final TrafficLoggerHandler tlh)
  {
    synchronized (this.tlh)
    {
      tlh.addTo (this.tlh);
    }
  }

  /**
   * Get the currently transferred traffic statistics.
   *
   * @return the current TrafficLoggerHandler
   */
  public TrafficLoggerHandler getTrafficLoggerHandler ()
  {
    return tlh;
  }

  /**
   * Get the ClientTrafficLoggerHandler
   *
   * @return the current ClientTrafficLoggerHandler.
   */
  public ClientTrafficLoggerHandler getClientTrafficLoggerHandler ()
  {
    return clientTrafficLoggers;
  }

  /**
   * Get the BufferHandler this proxy is using
   *
   * @return a BufferHandler
   */
  public IBufferHandler getBufferHandler ()
  {
    return bufferHandler;
  }

  /**
   * Get the current HttpGeneratorFactory.
   *
   * @return the HttpGeneratorFactory in use
   */
  public HttpGeneratorFactory getHttpGeneratorFactory ()
  {
    return hgf;
  }

  /**
   * Load a 3:rd party class.
   *
   * @param name
   *        the fully qualified name of the class to load
   * @param type
   *        the super type of the class
   * @param <T>
   *        the type of the clas
   * @return the loaded class
   * @throws ClassNotFoundException
   *         if the class can not be found
   */
  public <T> Class <? extends T> load3rdPartyClass (final String name,
                                                    final Class <T> type) throws ClassNotFoundException
  {
    return Class.forName (name, true, libLoader).asSubclass (type);
  }
}
