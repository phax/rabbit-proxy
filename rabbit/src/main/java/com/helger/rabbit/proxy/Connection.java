package com.helger.rabbit.proxy;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.io.stream.StreamHelper;
import com.helger.rabbit.cache.CacheException;
import com.helger.rabbit.cache.ICache;
import com.helger.rabbit.cache.ICacheEntry;
import com.helger.rabbit.handler.BaseHandler;
import com.helger.rabbit.handler.IHandler;
import com.helger.rabbit.handler.MultiPartHandler;
import com.helger.rabbit.http.HttpDateParser;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.HttpHeaderListener;
import com.helger.rabbit.httpio.HttpHeaderReader;
import com.helger.rabbit.httpio.HttpHeaderSender;
import com.helger.rabbit.httpio.HttpHeaderSentListener;
import com.helger.rabbit.httpio.RequestLineTooLongException;
import com.helger.rabbit.io.BufferHandle;
import com.helger.rabbit.io.CacheBufferHandle;
import com.helger.rabbit.io.IProxyChain;
import com.helger.rabbit.io.Resolver;
import com.helger.rabbit.util.Counter;
import com.helger.rnio.INioHandler;
import com.helger.rnio.ITaskIdentifier;
import com.helger.rnio.impl.DefaultTaskIdentifier;

/**
 * The base connection class for rabbit. This is the class that handle the http
 * protocoll for proxies. For the technical overview of how connections and
 * threads works see the file
 * htdocs/technical_documentation/thread_handling_overview.txt
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class Connection
{
  /** The id of this connection. */
  private final ConnectionId id;

  /** The client channel */
  private final SocketChannel channel;

  /** The current request */
  private HttpHeader request;

  /** The current request buffer handle */
  private BufferHandle requestHandle;

  /** The proxy we are serving */
  private final HttpProxy proxy;

  /** The current status of this connection. */
  private String status;

  /** The time this connection was started. */
  private long started;

  private boolean keepalive = true;
  private boolean meta = false;
  private boolean chunk = true;
  private boolean mayUseCache = true;
  private boolean mayCache = true;
  private boolean mayFilter = true;
  private boolean mustRevalidate = false;
  private boolean addedINM = false;
  private boolean addedIMS = false;

  /** If the user has authenticated himself */
  private String userName = null;
  private String password = null;

  /* Current status information */
  private String requestVersion = null;
  private String requestLine = null;
  private String statusCode = null;
  private String extraInfo = null;
  private String contentLength = null;

  private ClientResourceHandler clientResourceHandler;

  private final HttpGenerator responseHandler;

  private final TrafficLoggerHandler tlh = new TrafficLoggerHandler ();

  private static final Logger LOGGER = LoggerFactory.getLogger (Connection.class);

  /**
   * Create a new Connection
   *
   * @param id
   *        the ConnectionId of this connection.
   * @param channel
   *        the SocketChannel to the client.
   * @param proxy
   *        the HttpProxy that this connection belongs to.
   */
  public Connection (final ConnectionId id, final SocketChannel channel, final HttpProxy proxy)
  {
    this.id = id;
    this.channel = channel;
    this.proxy = proxy;
    this.requestHandle = new CacheBufferHandle (proxy.getBufferHandler ());
    proxy.addCurrentConnection (this);
    final HttpGeneratorFactory hgf = proxy.getHttpGeneratorFactory ();
    responseHandler = hgf.create (proxy.getServerIdentity (), this);
  }

  /**
   * @return the ConnectionId of this connection
   */
  public ConnectionId getId ()
  {
    return id;
  }

  /**
   * Read a request.
   */
  public void readRequest ()
  {
    clearStatuses ();
    try
    {
      final HttpHeaderListener clientListener = new RequestListener ();
      final HttpHeaderReader hr = new HttpHeaderReader (channel,
                                                        requestHandle,
                                                        getNioHandler (),
                                                        tlh.getClient (),
                                                        true,
                                                        proxy.getStrictHttp (),
                                                        clientListener);
      hr.readHeader ();
    }
    catch (final Throwable ex)
    {
      handleFailedRequestRead (ex);
    }
  }

  private boolean connectionReset (final Throwable t)
  {
    if (t instanceof IOException)
      return "Connection reset by peer".equals (t.getMessage ());
    return false;
  }

  private void handleFailedRequestRead (final Throwable t)
  {
    if (t instanceof RequestLineTooLongException)
    {
      final HttpHeader err = getHttpGenerator ().get414 ();
      // Send response and close
      sendAndClose (err);
    }
    else
      if (connectionReset (t))
      {
        LOGGER.info ("Exception when reading request: " + t);
        closeDown ();
      }
      else
      {
        LOGGER.info ("Exception when reading request", t);
        closeDown ();
      }
  }

  private class RequestListener implements HttpHeaderListener
  {
    public void httpHeaderRead (final HttpHeader header,
                                final BufferHandle bh,
                                final boolean keepalive,
                                final boolean isChunked,
                                final long dataSize)
    {
      setKeepalive (keepalive);
      requestRead (header, bh, isChunked, dataSize);
    }

    public void closed ()
    {
      closeDown ();
    }

    public void timeout ()
    {
      closeDown ();
    }

    public void failed (final Exception e)
    {
      handleFailedRequestRead (e);
    }
  }

  private void handleInternalError (final Throwable t)
  {
    extraInfo = extraInfo != null ? extraInfo + t.toString () : t.toString ();
    LOGGER.warn ("Internal Error", t);
    final HttpHeader internalError = getHttpGenerator ().get500 (request.getRequestURI (), t);
    // Send response and close
    sendAndClose (internalError);
  }

  private void requestRead (final HttpHeader request,
                            final BufferHandle bh,
                            final boolean isChunked,
                            final long dataSize)
  {
    if (request == null)
    {
      LOGGER.warn ("Got a null request");
      closeDown ();
      return;
    }
    status = "Request read, processing";
    this.request = request;
    this.requestHandle = bh;
    requestVersion = request.getHTTPVersion ();
    if (request.isDot9Request ())
      requestVersion = "HTTP/0.9";
    requestVersion = requestVersion.toUpperCase ();
    request.addHeader ("Via", requestVersion + " RabbIT");

    requestLine = request.getRequestLine ();
    getCounter ().inc ("Requests");

    try
    {
      // SSL requests are special in a way...
      // Don't depend upon being able to build URLs from the header...
      if (request.isSSLRequest ())
      {
        checkAndHandleSSL (bh);
        return;
      }

      // Now set up handler of any posted data.
      // is the request resource chunked?
      if (isChunked)
      {
        setupChunkedContent ();
      }
      else
      {
        // no? then try regular data
        final String ct = request.getHeader ("Content-Type");
        if (hasRegularContent (request, ct, dataSize))
          setupClientResourceHandler (dataSize);
        else
          // still no? then try multipart
          if (ct != null)
            readMultiPart (ct);
      }

      final ITaskIdentifier ti = new DefaultTaskIdentifier (getClass ().getSimpleName () + ".filterAndHandleRequest: ",
                                                            request.getRequestURI ());
      getNioHandler ().runThreadTask ( () -> filterAndHandleRequest (), ti);
    }
    catch (final Throwable t)
    {
      handleInternalError (t);
    }
  }

  private boolean hasRegularContent (final HttpHeader request, final String ct, final long dataSize)
  {
    if (request.getContent () != null)
      return true;
    if (ct != null && ct.startsWith ("multipart/byteranges"))
      return false;
    return dataSize > -1;
  }

  /**
   * Filter the request and handle it.
   */
  private void filterAndHandleRequest ()
  {
    // Filter the request based on the header.
    // A response means that the request is blocked.
    // For ad blocking, bad header configuration (http/1.1 correctness) ...
    final HttpHeaderFilterer filterer = proxy.getHttpHeaderFilterer ();
    final HttpHeader badresponse = filterer.filterHttpIn (this, channel, request);
    if (badresponse != null)
    {
      statusCode = badresponse.getStatusCode ();
      if (clientResourceHandler != null)
        readOffClientResource (badresponse);
      else
        // Try to keep the connection open
        // (authorization may need it).
        // A filter that want to close can set
        // keep alive to false
        sendAndTryRestart (badresponse);
    }
    else
    {
      if (getMeta ())
        handleMeta ();
      else
        handleRequest ();
    }
  }

  private void readOffClientResource (final HttpHeader badresponse)
  {
    clientResourceHandler.transfer (null, new ClientResourceTransferredListener ()
    {
      public void clientResourceTransferred ()
      {
        sendAndTryRestart (badresponse);
      }

      public void clientResourceAborted (final HttpHeader error)
      {
        closeDown ();
      }

      public void failed (final Exception cause)
      {
        closeDown ();
      }

      public void timeout ()
      {
        closeDown ();
      }
    });
  }

  /**
   * Handle a meta page.
   */
  private void handleMeta ()
  {
    status = "Handling meta page";
    final MetaHandlerHandler mhh = new MetaHandlerHandler ();
    try
    {
      mhh.handleMeta (this, request, tlh.getProxy (), tlh.getClient ());
    }
    catch (final IOException ex)
    {
      logAndClose ();
    }
  }

  private void checkNoStore (final ICacheEntry <HttpHeader, HttpHeader> entry)
  {
    if (entry == null)
      return;
    final List <String> ccs = request.getHeaders ("Cache-Control");
    final int ccl = ccs.size ();
    try
    {
      for (int i = 0; i < ccl; i++)
      {
        if (ccs.get (i).equals ("no-store"))
        {
          proxy.getCache ().remove (entry.getKey ());
        }
      }
    }
    catch (final CacheException e)
    {
      LOGGER.warn ("Failed to remove entry from cache", e);
    }
  }

  private boolean checkMaxAge (final RequestHandler rh)
  {
    return rh.getCond ().checkMaxAge (this, rh.getDataHook (), rh);
  }

  /**
   * Handle a request by getting the datastream (from the cache or the web).
   * After getting the handler for the mimetype, send it.
   */
  private void handleRequest ()
  {
    status = "Handling request";
    final RequestHandler rh = new RequestHandler (this);
    if (proxy.getCache ().getCacheConfiguration ().getMaxSize () > 0)
    {
      fillInCacheEntries (rh);
    }
    else
    {
      handleRequestBottom (rh);
    }
  }

  private void fillInCacheEntries (final RequestHandler rh)
  {
    status = "Handling request - checking cache";
    final ICache <HttpHeader, HttpHeader> cache = proxy.getCache ();
    final String method = request.getMethod ();
    try
    {
      if (!method.equals ("GET") && !method.equals ("HEAD"))
        cache.remove (request);

      rh.setEntry (cache.getEntry (request));
      if (rh.getEntry () != null)
        rh.setDataHook (rh.getEntry ().getDataHook ());
      checkNoStore (rh.getEntry ());
      // Check if cached item is too old
      if (!rh.getCond ().checkMaxStale (request, rh) && checkMaxAge (rh))
        setMayUseCache (false);

      // Add headers to send If-None-Match, or If-Modified-Since
      rh.setConditional (rh.getCond ().checkConditional (this, request, rh, mustRevalidate));
      if (partialContent (rh))
        fillupContent ();
      checkIfRange (rh);
    }
    catch (final CacheException e)
    {
      LOGGER.warn ("Failed cache operation", e);
    }

    final boolean mc = getMayCache ();
    // in cache?
    if (getMayUseCache () && rh.getEntry () != null)
    {
      final CacheChecker cc = new CacheChecker ();
      if (cc.checkCachedEntry (this, request, rh))
        return;
    }
    if (rh.getContent () == null)
    {
      // Ok cache did not have a usable resource,
      // reset value to one before we thought we could use cache...
      mayCache = mc;
    }

    handleRequestBottom (rh);
  }

  private void handleRequestBottom (final RequestHandler rh)
  {
    if (rh.getContent () == null)
    {
      status = "Handling request - setting up web connection";
      // no usable cache entry so get the resource from the net.
      final IProxyChain pc = proxy.getProxyChain ();
      final Resolver r = pc.getResolver (request.getRequestURI ());
      final SWC swc = new SWC (this, r, request, tlh, clientResourceHandler, rh);
      swc.establish ();
    }
    else
    {
      resourceEstablished (rh);
    }
  }

  /**
   * Fired when setting up a web connection failed.
   *
   * @param rh
   *        the RequestHandler
   * @param cause
   *        the Exception that signaled the problem
   */
  public void webConnectionSetupFailed (final RequestHandler rh, final Exception cause)
  {
    if (cause instanceof UnknownHostException)
    {
      // do we really want this in the log?
      LOGGER.warn (cause.toString () + ": " + request.getRequestURI ());
    }
    else
      LOGGER.warn ("Failed to set up web connection to: " + request.getRequestURI () + ", cause: " + cause);
    tryStaleEntry (rh, cause);
  }

  private void setMayCacheFromCC (final RequestHandler rh)
  {
    final HttpHeader resp = rh.getWebHeader ();
    for (final String val : resp.getHeaders ("Cache-Control"))
    {
      if ("public".equals (val) || "must-revalidate".equals (val) || val.startsWith ("s-maxage="))
      {
        final String auth = request.getHeader ("Authorization");
        if (auth != null)
        {
          // TODO this ignores no-store and a few other things...
          mayCache = true;
          break;
        }
      }
    }
  }

  /**
   * Check if we must tunnel a request. Currently will only check if the
   * Authorization starts with NTLM or Negotiate.
   *
   * @return true if the current request needs to be handled by a tunnel
   */
  protected boolean mustTunnel ()
  {
    final String auth = request.getHeader ("Authorization");
    return auth != null && (auth.startsWith ("NTLM") || auth.startsWith ("Negotiate"));
  }

  /**
   * Fired when a web connection has been established. The web connection may be
   * to the origin server or to an upstream proxy.
   *
   * @param rh
   *        the RequestHandler for the current request
   */
  public void webConnectionEstablished (final RequestHandler rh)
  {
    getProxy ().markForPipelining (rh.getWebConnection ());
    if (!request.isDot9Request ())
      setMayCacheFromCC (rh);
    resourceEstablished (rh);
  }

  private void tunnel (final RequestHandler rh)
  {
    status = "Handling request - tunneling";
    final TunnelDoneListener tdl = new TDL (rh);
    final SocketChannel webChannel = rh.getWebConnection ().getChannel ();
    final Tunnel tunnel = new Tunnel (getNioHandler (),
                                      channel,
                                      requestHandle,
                                      tlh.getClient (),
                                      webChannel,
                                      rh.getWebHandle (),
                                      tlh.getNetwork (),
                                      tdl);
    tunnel.start ();
  }

  private void resourceEstablished (final RequestHandler rh)
  {
    status = "Handling request - got resource";
    try
    {
      // and now we filter the response header if any.
      if (!request.isDot9Request ())
      {
        if (mustTunnel ())
        {
          tunnel (rh);
          return;
        }

        final String status = rh.getWebHeader ().getStatusCode ().trim ();

        // Check if the cached Date header is newer,
        // indicating that we should not cache.
        if (!rh.getCond ().checkStaleCache (request, this, rh))
          setMayCache (false);

        final CacheChecker cc = new CacheChecker ();
        cc.removeOtherStaleCaches (request, rh.getWebHeader (), proxy.getCache ());
        if (status.equals ("304"))
        {
          final NotModifiedHandler nmh = new NotModifiedHandler ();
          nmh.updateHeader (rh);
          if (rh.getEntry () != null)
          {
            proxy.getCache ().entryChanged (rh.getEntry (), request, rh.getDataHook ());
          }
        }

        // Check that the cache entry has expected header
        // returns null for a good cache entry
        HttpHeader bad = cc.checkExpectations (this, request, rh.getWebHeader ());
        if (bad == null)
        {
          final HttpHeaderFilterer filterer = proxy.getHttpHeaderFilterer ();
          // Run output filters on the header
          bad = filterer.filterHttpOut (this, channel, rh.getWebHeader ());
        }
        if (bad != null)
        {
          // Bad cache entry or this request is blocked
          rh.getContent ().release ();
          // Send error response, keep connection open
          sendAndTryRestart (bad);
          return;
        }

        if (rh.isConditional () && rh.getEntry () != null && status.equals ("304"))
        {
          // Try to setup a resource from the cache
          if (handleConditional (rh))
          {
            return;
          }
        }
        else
          if (status.length () > 0)
          {
            if (status.equals ("304") || status.equals ("204") || status.charAt (0) == '1')
            {
              rh.getContent ().release ();
              // Send success response and close
              sendAndClose (rh.getWebHeader ());
              return;
            }
          }
      }

      setHandlerFactory (rh);
      status = "Handling request - " + rh.getHandlerFactory ().getClass ().getName ();
      final IHandler handler = rh.getHandlerFactory ()
                                 .getNewInstance (this,
                                                  tlh,
                                                  request,
                                                  rh.getWebHeader (),
                                                  rh.getContent (),
                                                  getMayCache (),
                                                  getMayFilter (),
                                                  rh.getSize ());
      if (handler == null)
      {
        doError (500, "Failed to find handler");
      }
      else
      {
        finalFixesOnWebHeader (rh, handler);
        // HTTP/0.9 does not support HEAD, so webheader should be valid.
        if (request.isHeadOnlyRequest ())
        {
          rh.getContent ().release ();
          sendAndTryRestart (rh.getWebHeader ());
        }
        else
        {
          handler.handle ();
        }
      }
    }
    catch (final Throwable t)
    {
      handleInternalError (t);
    }
  }

  private void finalFixesOnWebHeader (final RequestHandler rh, final IHandler handler)
  {
    if (rh.getWebHeader () == null)
      return;
    if (chunk)
    {
      if (rh.getSize () < 0 || handler.changesContentSize ())
      {
        rh.getWebHeader ().removeHeader ("Content-Length");
        rh.getWebHeader ().setHeader ("Transfer-Encoding", "chunked");
      }
      else
      {
        setChunking (false);
      }
    }
    else
    {
      if (getKeepalive ())
      {
        rh.getWebHeader ().setHeader ("Proxy-Connection", "Keep-Alive");
        rh.getWebHeader ().setHeader ("Connection", "Keep-Alive");
      }
      else
      {
        rh.getWebHeader ().setHeader ("Proxy-Connection", "close");
        rh.getWebHeader ().setHeader ("Connection", "close");
      }
    }
  }

  private void setHandlerFactory (final RequestHandler rh)
  {
    if (rh.getHandlerFactory () == null)
    {
      String ct = null;
      if (rh.getWebHeader () != null)
      {
        ct = rh.getWebHeader ().getHeader ("Content-Type");
        if (ct != null)
        {
          ct = ct.toLowerCase ();
          // remove some white spaces for easier configuration.
          // "text/html; charset=iso-8859-1"
          // "text/html;charset=iso-8859-1"
          ct = ct.replace ("; ", ";");
          if (getMayFilter ())
            rh.setHandlerFactory (proxy.getHandlerFactory (ct));
          if (rh.getHandlerFactory () == null && ct.startsWith ("multipart/byteranges"))
            rh.setHandlerFactory (new MultiPartHandler ());
        }
      }
      if (rh.getHandlerFactory () == null)
      {
        // still null
        if (LOGGER.isDebugEnabled ())
          LOGGER.debug ("Using BaseHandler for " + ct);
        rh.setHandlerFactory (new BaseHandler ()); // fallback...
      }
    }
  }

  private boolean handleConditional (final RequestHandler rh)
  {
    final HttpHeader cachedHeader = rh.getDataHook ();
    rh.getContent ().release ();

    if (addedINM)
      request.removeHeader ("If-None-Match");
    if (addedIMS)
      request.removeHeader ("If-Modified-Since");

    if (ETagUtils.checkWeakEtag (cachedHeader, rh.getWebHeader ()))
    {
      final NotModifiedHandler nmh = new NotModifiedHandler ();
      nmh.updateHeader (rh);
      setMayCache (false);
      try
      {
        final HttpHeader res304 = nmh.is304 (request, getHttpGenerator (), rh);
        if (res304 != null)
        {
          sendAndClose (res304);
          return true;
        }
        // Try to setup a resource from the cache
        setupCachedEntry (rh);
      }
      catch (final IOException e)
      {
        LOGGER.warn ("Conditional request: IOException (" + request.getRequestURI (), e);
      }
    }
    else
    {
      // retry...
      request.removeHeader ("If-None-Match");
      try
      {
        proxy.getCache ().remove (request);
      }
      catch (final CacheException e)
      {
        LOGGER.warn ("Failed to remove entry", e);
      }
      handleRequest ();
      return true;
    }

    // send the cached entry.
    return false;
  }

  private class TDL implements TunnelDoneListener
  {
    private final RequestHandler rh;

    public TDL (final RequestHandler rh)
    {
      this.rh = rh;
    }

    public void tunnelClosed ()
    {
      if (rh != null && rh.getWebConnection () != null)
      {
        proxy.releaseWebConnection (rh.getWebConnection ());
      }
      logAndClose ();
    }
  }

  private void tryStaleEntry (final RequestHandler rh, final Exception e)
  {
    // do we have a stale entry?
    if (rh.getEntry () != null && rh.isConditional () && !mustRevalidate)
      handleStaleEntry (rh);
    else
      doGateWayTimeout (e);
  }

  private void handleStaleEntry (final RequestHandler rh)
  {
    setMayCache (false);
    try
    {
      setupCachedEntry (rh);
      final HttpHeader wh = rh.getWebHeader ();
      wh.addHeader ("Warning", "110 RabbIT \"Response is stale\"");
      resourceEstablished (rh);
    }
    catch (final IOException ex)
    {
      doGateWayTimeout (ex);
    }
  }

  // Setup a resource from the cache
  HttpHeader setupCachedEntry (final RequestHandler rh) throws IOException
  {
    final SCC swc = new SCC (this, request, rh);
    return swc.establish ();
  }

  private void setupChunkedContent ()
  {
    status = "Request read, reading chunked data";
    setMayUseCache (false);
    setMayCache (false);
    clientResourceHandler = new ChunkedContentTransferHandler (this, requestHandle, tlh);
  }

  private void setupClientResourceHandler (final long dataSize)
  {
    status = "Request read, reading client resource data";
    setMayUseCache (false);
    setMayCache (false);
    clientResourceHandler = new ContentTransferHandler (this, requestHandle, dataSize, tlh);
  }

  private void readMultiPart (final String ct)
  {
    status = "Request read, reading multipart data";
    // Content-Type: multipart/byteranges; boundary=B-qpuvxclkeavxeywbqupw
    if (ct.startsWith ("multipart/byteranges"))
    {
      setMayUseCache (false);
      setMayCache (false);

      clientResourceHandler = new MultiPartTransferHandler (this, requestHandle, tlh, ct);
    }
  }

  private boolean partialContent (final RequestHandler rh)
  {
    if (rh.getEntry () == null)
      return false;
    final String method = request.getMethod ();
    if (!method.equals ("GET"))
      return false;
    final HttpHeader resp = rh.getDataHook ();
    final String realLength = resp.getHeader ("RabbIT-Partial");
    return (realLength != null);
  }

  private void fillupContent ()
  {
    setMayUseCache (false);
    setMayCache (true);
    // TODO: if the need arise, think about implementing smart partial updates.
  }

  private void checkIfRange (final RequestHandler rh)
  {
    if (rh.getEntry () == null)
      return;
    final String ifRange = request.getHeader ("If-Range");
    if (ifRange == null)
      return;
    final String range = request.getHeader ("Range");
    if (range == null)
      return;
    final Date d = HttpDateParser.getDate (ifRange);
    final HttpHeader oldresp = rh.getDataHook ();
    if (d == null)
    {
      // we have an etag...
      final String etag = oldresp.getHeader ("Etag");
      if (etag == null || !ETagUtils.checkWeakEtag (etag, ifRange))
        setMayUseCache (false);
    }
  }

  /**
   * Send an error (400 Bad Request) to the client.
   *
   * @param status
   *        the status code of the error.
   * @param message
   *        the error message to tell the client.
   */
  public void doError (final int status, final String message)
  {
    this.statusCode = Integer.toString (status);
    final HttpHeader header = getHttpGenerator ().get400 (new IOException (message));
    sendAndClose (header);
  }

  /**
   * Send an error (400 Bad Request or 504) to the client.
   *
   * @param e
   *        the exception to tell the client.
   */
  private void doGateWayTimeout (final Exception e)
  {
    this.statusCode = "504";
    extraInfo = (extraInfo != null ? extraInfo + e.toString () : e.toString ());
    final HttpHeader header = getHttpGenerator ().get504 (request.getRequestURI (), e);
    sendAndClose (header);
  }

  private void checkAndHandleSSL (final BufferHandle bh)
  {
    status = "Handling ssl request";
    final SSLHandler sslh = new SSLHandler (proxy, this, request, tlh);
    if (sslh.isAllowed ())
    {
      final HttpHeaderFilterer filterer = proxy.getHttpHeaderFilterer ();
      final HttpHeader badresponse = filterer.filterConnect (this, channel, request);
      if (badresponse != null)
      {
        statusCode = badresponse.getStatusCode ();
        // Try to keep the connection open (authorization may need it).
        // A filter that want to close can set keep alive to false
        sendAndTryRestart (badresponse);
      }
      else
      {
        sslh.handle (channel, bh);
      }
    }
    else
    {
      final HttpHeader badresponse = responseHandler.get403 ();
      sendAndClose (badresponse);
    }
  }

  /**
   * Get the SocketChannel to the client
   *
   * @return the SocketChannel connected to the client
   */
  public SocketChannel getChannel ()
  {
    return channel;
  }

  /**
   * @return the NioHandler that this connection is using
   */
  public INioHandler getNioHandler ()
  {
    return proxy.getNioHandler ();
  }

  /**
   * @return the HttProxy that this connection is serving
   */
  public HttpProxy getProxy ()
  {
    return proxy;
  }

  private void closeDown ()
  {
    StreamHelper.close (channel);
    if (!requestHandle.isEmpty ())
    {
      // empty the buffer...
      final ByteBuffer buf = requestHandle.getBuffer ();
      buf.position (buf.limit ());
    }
    requestHandle.possiblyFlush ();
    proxy.removeCurrentConnection (this);
  }

  private ConnectionLogger getConnectionLogger ()
  {
    return proxy.getConnectionLogger ();
  }

  /**
   * @return the Counter that keeps count of operations for this connection.
   */
  public Counter getCounter ()
  {
    return proxy.getCounter ();
  }

  /**
   * Resets the statuses for this connection.
   */
  private void clearStatuses ()
  {
    status = "Reading request";
    started = System.currentTimeMillis ();
    request = null;
    keepalive = true;
    meta = false;
    chunk = true;
    mayUseCache = true;
    mayCache = true;
    mayFilter = true;
    mustRevalidate = false;
    addedINM = false;
    addedIMS = false;
    userName = null;
    password = null;
    requestLine = "?";
    statusCode = "200";
    extraInfo = null;
    contentLength = "-";
    clientResourceHandler = null;
  }

  /**
   * Set keepalive to a new value. Note that keepalive can only be promoted
   * down.
   *
   * @param keepalive
   *        the new keepalive value.
   */
  public void setKeepalive (final boolean keepalive)
  {
    this.keepalive = (this.keepalive && keepalive);
  }

  /**
   * Get the keepalive value.
   *
   * @return true if keepalive should be done, false otherwise.
   */
  private boolean getKeepalive ()
  {
    return keepalive;
  }

  /**
   * Get the name of the user that is currently authorized.
   *
   * @return a username, may be null if the user is not know/authorized
   */
  public String getUserName ()
  {
    return userName;
  }

  /**
   * Set the name of the currently authenticated user (for basic proxy auth)
   *
   * @param userName
   *        the name of the current user
   */
  public void setUserName (final String userName)
  {
    this.userName = userName;
  }

  /**
   * Get the name of the user that is currently authorized.
   *
   * @return a username, may be null if the user is not know/authorized
   */
  public String getPassword ()
  {
    return password;
  }

  /**
   * Set the password of the currently authenticated user (for basic proxy auth)
   *
   * @param password
   *        the password that was used for authentication
   */
  public void setPassword (final String password)
  {
    this.password = password;
  }

  /**
   * Get the request line of the request currently being handled
   *
   * @return the request line for the current request
   */
  public String getRequestLine ()
  {
    return requestLine;
  }

  /**
   * Get the current request uri. This will get the uri from the request header.
   *
   * @return the uri of the current request
   */
  public String getRequestURI ()
  {
    return request.getRequestURI ();
  }

  /**
   * Get debug info for use in 500 error response
   *
   * @return a string with internal state of this connection
   */
  public String getDebugInfo ()
  {
    return "status: " +
           getStatus () +
           "\n" +
           "started: " +
           new Date (getStarted ()) +
           "\n" +
           "keepalive: " +
           getKeepalive () +
           "\n" +
           "meta: " +
           getMeta () +
           "\n" +
           "mayusecache: " +
           getMayUseCache () +
           "\n" +
           "maycache: " +
           getMayCache () +
           "\n" +
           "mayfilter: " +
           getMayFilter () +
           "\n" +
           "requestline: " +
           getRequestLine () +
           "\n" +
           "statuscode: " +
           getStatusCode () +
           "\n" +
           "extrainfo: " +
           getExtraInfo () +
           "\n" +
           "contentlength: " +
           getContentLength () +
           "\n";
  }

  /**
   * Get the http version that the client used. We modify the request header to
   * hold HTTP/1.1 since that is what rabbit uses, but the real client may have
   * sent a 1.0 header.
   *
   * @return the request http version
   */
  public String getRequestVersion ()
  {
    return requestVersion;
  }

  /**
   * Get the current status of this request
   *
   * @return the current status
   */
  public String getStatus ()
  {
    return status;
  }

  // For logging
  String getStatusCode ()
  {
    return statusCode;
  }

  /**
   * @return the content length of the current request
   */
  public String getContentLength ()
  {
    return contentLength;
  }

  /**
   * Get the client resource handler, that is the handler of any content the
   * client is submitting (POSTED data, file uploads etc.)
   *
   * @return the ClientResourceHandler for the current request
   */
  public ClientResourceHandler getClientResourceHandler ()
  {
    return clientResourceHandler;
  }

  /**
   * Get the extra information associated with the current request.
   *
   * @return the currently set extra info or null if no such info is set.
   */
  public String getExtraInfo ()
  {
    return extraInfo;
  }

  /**
   * Set the extra info.
   *
   * @param info
   *        the new info.
   */
  public void setExtraInfo (final String info)
  {
    this.extraInfo = info;
  }

  /**
   * Get the time the current request was started.
   *
   * @return the start time for the current request
   */
  public long getStarted ()
  {
    return started;
  }

  /**
   * Set the chunking option.
   *
   * @param b
   *        if true this connection should use chunking.
   */
  public void setChunking (final boolean b)
  {
    chunk = b;
  }

  /**
   * Get the chunking option.
   *
   * @return if this connection is using chunking.
   */
  public boolean getChunking ()
  {
    return chunk;
  }

  /**
   * Get the state of this request.
   *
   * @return true if this is a metapage request, false otherwise.
   */
  public boolean getMeta ()
  {
    return meta;
  }

  /**
   * Flag this request as a meta-request, that is a request that the proxy
   * should to handle.
   */
  public void setMeta ()
  {
    this.meta = true;
  }

  /**
   * Specify if the current resource may be served from our cache. This can only
   * be promoted down..
   *
   * @param useCache
   *        true if we may use the cache for serving this request, false
   *        otherwise.
   */
  public void setMayUseCache (final boolean useCache)
  {
    mayUseCache = mayUseCache && useCache;
  }

  /**
   * Get the state of this request.
   *
   * @return true if we may use the cache for this request, false otherwise.
   */
  private boolean getMayUseCache ()
  {
    return mayUseCache;
  }

  /**
   * Specify if we may cache the response resource. This can only be promoted
   * down.
   *
   * @param cacheAllowed
   *        true if we may cache the response, false otherwise.
   */
  public void setMayCache (final boolean cacheAllowed)
  {
    mayCache = cacheAllowed && mayCache;
  }

  /**
   * Get the state of this request.
   *
   * @return true if we may cache the response, false otherwise.
   */
  private boolean getMayCache ()
  {
    return mayCache;
  }

  /**
   * Get the state of this request. This can only be promoted down.
   */
  public void setFilteringNotAllowed ()
  {
    mayFilter = false;
  }

  /**
   * Get the state of the request.
   *
   * @return true if we may filter the response, false otherwise.
   */
  public boolean getMayFilter ()
  {
    return mayFilter;
  }

  void setAddedINM ()
  {
    addedINM = true;
  }

  void setAddedIMS ()
  {
    addedIMS = true;
  }

  /**
   * Tell this connection that the current request must be revalidated.
   */
  public void setMustRevalidate ()
  {
    mustRevalidate = true;
  }

  /**
   * Set the content length of the response.
   *
   * @param contentLength
   *        the new content length.
   */
  public void setContentLength (final String contentLength)
  {
    this.contentLength = contentLength;
  }

  /**
   * Set the status code for the current request
   *
   * @param statusCode
   *        the new status code
   */
  public void setStatusCode (final String statusCode)
  {
    this.statusCode = statusCode;
  }

  // Set status and content length
  private void setStatusesFromHeader (final HttpHeader header)
  {
    statusCode = header.getStatusCode ();
    final String cl = header.getHeader ("Content-Length");
    if (cl != null)
      contentLength = cl;
  }

  void sendAndTryRestart (final HttpHeader header)
  {
    status = keepalive ? "Sending response and restarting" : "Sending response and closing";
    setStatusesFromHeader (header);

    final HttpHeaderSentListener sar = new SendAndRestartListener ();
    try
    {
      final HttpHeaderSender hhs = new HttpHeaderSender (channel,
                                                         getNioHandler (),
                                                         tlh.getClient (),
                                                         header,
                                                         false,
                                                         sar);
      hhs.sendHeader ();
    }
    catch (final IOException e)
    {
      LOGGER.warn ("IOException when sending header", e);
      closeDown ();
    }
  }

  /**
   * Send a request and then close this connection.
   *
   * @param header
   *        the HttpHeader to send before closing down.
   */
  public void sendAndClose (final HttpHeader header)
  {
    keepalive = false;
    sendAndTryRestart (header);
  }

  private class SendAndRestartListener implements HttpHeaderSentListener
  {
    public void timeout ()
    {
      status = "Response sending timed out, logging and closing.";
      LOGGER.info ("Timeout when sending http header");
      logAndClose ();
    }

    public void failed (final Exception e)
    {
      status = "Response sending failed: " + e + ", logging and closing.";
      LOGGER.info ("Exception when sending http header", e);
      logAndClose ();
    }

    public void httpHeaderSent ()
    {
      logAndTryRestart ();
    }
  }

  /**
   * Log the current request and close/end this connection
   */
  public void logAndClose ()
  {
    keepalive = false;
    logAndTryRestart ();
  }

  /**
   * Log the current request and start to listen for a new request if possible.
   */
  public void logAndTryRestart ()
  {
    if (getNioHandler ().isSelectorThread ())
    {
      final ITaskIdentifier ti = new DefaultTaskIdentifier ("logConnection", request.getRequestURI ());
      getNioHandler ().runThreadTask ( () -> internalLogAndTryRestart (), ti);
    }
    else
    {
      internalLogAndTryRestart ();
    }
  }

  private void internalLogAndTryRestart ()
  {
    logConnection ();
    if (getKeepalive ())
      readRequest ();
    else
      closeDown ();
  }

  /**
   * Get the HttpGenerator that this connection uses when it needs to generate a
   * custom respons header and resource.
   *
   * @return the current HttpGenerator
   */
  public HttpGenerator getHttpGenerator ()
  {
    return responseHandler;
  }

  private void logConnection ()
  {
    getConnectionLogger ().logConnection (Connection.this);
    proxy.updateTrafficLog (tlh);
    final ClientTrafficLoggerHandler cth = proxy.getClientTrafficLoggerHandler ();
    cth.logTraffic (userName, request, tlh.getClient (), tlh.getNetwork (), tlh.getCache (), tlh.getProxy ());
    tlh.clear ();
  }
}
