package com.helger.rabbit.proxy;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.helger.commons.base64.Base64;
import com.helger.commons.charset.CCharset;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.HttpHeaderSender;
import com.helger.rabbit.httpio.HttpHeaderSentListener;
import com.helger.rabbit.httpio.HttpResponseListener;
import com.helger.rabbit.httpio.HttpResponseReader;
import com.helger.rabbit.io.BufferHandle;
import com.helger.rabbit.io.CacheBufferHandle;
import com.helger.rabbit.io.IProxyChain;
import com.helger.rabbit.io.Resolver;
import com.helger.rabbit.io.WebConnection;
import com.helger.rabbit.io.WebConnectionListener;
import com.helger.rnio.impl.Closer;

/**
 * A handler that shuttles ssl traffic
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class SSLHandler implements TunnelDoneListener
{
  private final HttpProxy proxy;
  private final Connection con;
  private final HttpHeader request;
  private final TrafficLoggerHandler tlh;
  private final Resolver resolver;
  private SocketChannel channel;
  private BufferHandle bh;
  private BufferHandle sbh;
  private WebConnection wc;
  private final Logger logger = Logger.getLogger (getClass ().getName ());

  /**
   * Create a new SSLHandler
   *
   * @param proxy
   *        the HttpProxy this SSL connection is serving
   * @param con
   *        the Connection to handle
   * @param request
   *        the CONNECT header
   * @param tlh
   *        the traffic statistics gatherer
   */
  public SSLHandler (final HttpProxy proxy,
                     final Connection con,
                     final HttpHeader request,
                     final TrafficLoggerHandler tlh)
  {
    this.proxy = proxy;
    this.con = con;
    this.request = request;
    this.tlh = tlh;
    final IProxyChain pc = con.getProxy ().getProxyChain ();
    resolver = pc.getResolver (request.getRequestURI ());
  }

  /**
   * Are we allowed to proxy ssl-type connections ?
   *
   * @return true if we allow the CONNECT &lt;port&gt; command.
   */
  public boolean isAllowed ()
  {
    final String hp = request.getRequestURI ();
    final int c = hp.indexOf (':');
    Integer port = Integer.valueOf (443);
    if (c >= 0)
    {
      try
      {
        port = new Integer (hp.substring (c + 1));
      }
      catch (final NumberFormatException e)
      {
        logger.warning ("Connect to odd port: " + e);
        return false;
      }
    }
    if (!proxy.proxySSL)
      return false;
    if (proxy.proxySSL && proxy.sslports == null)
      return true;
    for (int i = 0; i < proxy.sslports.size (); i++)
    {
      if (port.equals (proxy.sslports.get (i)))
        return true;
    }
    return false;
  }

  /**
   * handle the tunnel.
   *
   * @param channel
   *        the client channel
   * @param bh
   *        the buffer handle used, may contain data from client.
   */
  public void handle (final SocketChannel channel, final BufferHandle bh)
  {
    this.channel = channel;
    this.bh = bh;
    if (resolver.isProxyConnected ())
    {
      final String auth = resolver.getProxyAuthString ();
      // it should look like this (using RabbIT:RabbIT):
      // Proxy-authorization: Basic UmFiYklUOlJhYmJJVA==
      if (auth != null && !auth.equals (""))
        request.setHeader ("Proxy-authorization", "Basic " + Base64.safeEncode (auth, CCharset.CHARSET_UTF_8_OBJ));
    }
    final WebConnectionListener wcl = new WebConnector ();
    proxy.getWebConnection (request, wcl);
  }

  private class WebConnector implements WebConnectionListener
  {
    private final String uri;

    public WebConnector ()
    {
      uri = request.getRequestURI ();
      // java needs protocoll to build URL
      request.setRequestURI ("http://" + uri);
    }

    public void connectionEstablished (final WebConnection wce)
    {
      wc = wce;
      if (resolver.isProxyConnected ())
      {
        request.setRequestURI (uri); // send correct connect to next proxy.
        setupChain ();
      }
      else
      {
        final BufferHandle bh = new CacheBufferHandle (proxy.getBufferHandler ());
        sendOkReplyAndTunnel (bh);
      }
    }

    public void timeout ()
    {
      final String err = "SSLHandler: Timeout waiting for web connection: " + uri;
      logger.warning (err);
      closeDown ();
    }

    public void failed (final Exception e)
    {
      warn ("SSLHandler: failed to get web connection to: " + uri, e);
      closeDown ();
    }
  }

  private void closeDown ()
  {
    if (bh != null)
      bh.possiblyFlush ();
    if (sbh != null)
      sbh.possiblyFlush ();
    Closer.close (wc, logger);
    wc = null;
    con.logAndClose ();
  }

  private void warn (final String err, final Exception e)
  {
    logger.log (Level.WARNING, err, e);
  }

  private void setupChain ()
  {
    final HttpResponseListener cr = new ChainResponseHandler ();
    try
    {
      final HttpResponseReader hrr = new HttpResponseReader (wc.getChannel (),
                                                             proxy.getNioHandler (),
                                                             tlh.getNetwork (),
                                                             proxy.getBufferHandler (),
                                                             request,
                                                             proxy.getStrictHttp (),
                                                             resolver.isProxyConnected (),
                                                             cr);
      hrr.sendRequestAndWaitForResponse ();
    }
    catch (final IOException e)
    {
      warn ("IOException when waiting for chained response: " + request.getRequestURI (), e);
      closeDown ();
    }
  }

  private class ChainResponseHandler implements HttpResponseListener
  {
    public void httpResponse (final HttpHeader response,
                              final BufferHandle rbh,
                              final boolean keepalive,
                              final boolean isChunked,
                              final long dataSize)
    {
      final String status = response.getStatusCode ();
      if (!"200".equals (status))
      {
        closeDown ();
      }
      else
      {
        sendOkReplyAndTunnel (rbh);
      }
    }

    public void failed (final Exception cause)
    {
      warn ("SSLHandler: failed to get chained response: " + request.getRequestURI (), cause);
      closeDown ();
    }

    public void timeout ()
    {
      final String err = "SSLHandler: Timeout waiting for chained response: " + request.getRequestURI ();
      logger.warning (err);
      closeDown ();
    }
  }

  private void sendOkReplyAndTunnel (final BufferHandle server2client)
  {
    final HttpHeader reply = new HttpHeader ();
    reply.setStatusLine ("HTTP/1.0 200 Connection established");
    reply.setHeader ("Proxy-agent", proxy.getServerIdentity ());

    final HttpHeaderSentListener tc = new TunnelConnected (server2client);
    try
    {
      final HttpHeaderSender hhs = new HttpHeaderSender (channel,
                                                         proxy.getNioHandler (),
                                                         tlh.getClient (),
                                                         reply,
                                                         false,
                                                         tc);
      hhs.sendHeader ();
    }
    catch (final IOException e)
    {
      warn ("IOException when sending header", e);
      closeDown ();
    }
  }

  private class TunnelConnected implements HttpHeaderSentListener
  {
    private final BufferHandle server2client;

    public TunnelConnected (final BufferHandle server2client)
    {
      this.server2client = server2client;
    }

    public void httpHeaderSent ()
    {
      tunnelData (server2client);
    }

    public void timeout ()
    {
      logger.warning ("SSLHandler: Timeout when sending http header: " + request.getRequestURI ());
      closeDown ();
    }

    public void failed (final Exception e)
    {
      warn ("SSLHandler: Exception when sending http header: " + request.getRequestURI (), e);
      closeDown ();
    }
  }

  private void tunnelData (final BufferHandle server2client)
  {
    sbh = server2client;
    final SocketChannel sc = wc.getChannel ();
    final Tunnel tunnel = new Tunnel (proxy.getNioHandler (),
                                      channel,
                                      bh,
                                      tlh.getClient (),
                                      sc,
                                      server2client,
                                      tlh.getNetwork (),
                                      this);
    tunnel.start ();
  }

  public void tunnelClosed ()
  {
    if (wc != null)
    {
      con.logAndClose ();
      Closer.close (wc, logger);
    }
    wc = null;
  }
}
