package com.helger.rabbit.client;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.helger.rabbit.dns.DNSJavaHandler;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.HttpResponseListener;
import com.helger.rabbit.httpio.HttpResponseReader;
import com.helger.rabbit.httpio.SimpleProxyChain;
import com.helger.rabbit.httpio.WebConnectionResourceSource;
import com.helger.rabbit.io.BufferHandle;
import com.helger.rabbit.io.ConnectionHandler;
import com.helger.rabbit.io.IProxyChain;
import com.helger.rabbit.io.WebConnection;
import com.helger.rabbit.io.WebConnectionListener;
import com.helger.rabbit.util.Counter;
import com.helger.rabbit.util.ITrafficLogger;
import com.helger.rabbit.util.SimpleTrafficLogger;
import com.helger.rnio.IBufferHandler;
import com.helger.rnio.INioHandler;
import com.helger.rnio.IStatisticsHolder;
import com.helger.rnio.impl.BasicStatisticsHolder;
import com.helger.rnio.impl.CachingBufferHandler;
import com.helger.rnio.impl.MultiSelectorNioHandler;
import com.helger.rnio.impl.SimpleThreadFactory;

/**
 * A class for doing http requests.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ClientBase
{
  private final ConnectionHandler connectionHandler;
  private final INioHandler nioHandler;
  private final ITrafficLogger trafficLogger = new SimpleTrafficLogger ();
  private final IBufferHandler bufHandler;

  /**
   * Create a new ClientBase.
   *
   * @throws IOException
   *         if creating the nio handler fails
   */
  public ClientBase () throws IOException
  {
    final ExecutorService es = Executors.newCachedThreadPool ();
    final IStatisticsHolder sh = new BasicStatisticsHolder ();
    nioHandler = new MultiSelectorNioHandler (es, sh, 4, Long.valueOf (15_000L));
    nioHandler.start (new SimpleThreadFactory ());
    final DNSJavaHandler jh = new DNSJavaHandler ();
    jh.setup (null);
    final IProxyChain proxyChain = new SimpleProxyChain (nioHandler, jh);
    final Counter counter = new Counter ();
    connectionHandler = new ConnectionHandler (counter, proxyChain, nioHandler);

    bufHandler = new CachingBufferHandler ();
  }

  /**
   * Submit a new request, using the given method to the given url.
   *
   * @param method
   *        HEAD or GET or POST or ...
   * @param url
   *        the url to do the http request against.
   * @return the header of the request
   * @throws IOException
   *         if the url is not really an URL
   */
  public HttpHeader getRequest (final String method, final String url) throws IOException
  {
    final URL u = new URL (url);
    final HttpHeader ret = new HttpHeader ();
    ret.setStatusLine (method + " " + url + " HTTP/1.1");
    ret.setHeader ("Host", u.getHost ());
    ret.setHeader ("User-Agent", "rabbit client library");
    return ret;
  }

  /**
   * Get the NioHandler that this client is using
   *
   * @return the current NioHandler
   */
  public INioHandler getNioHandler ()
  {
    return nioHandler;
  }

  /**
   * Shutdown this client handler.
   */
  public void shutdown ()
  {
    nioHandler.shutdown ();
  }

  /**
   * Send a request and let the client be notified on response.
   *
   * @param request
   *        the request to send
   * @param client
   *        the listener to notify with the response
   */
  public void sendRequest (final HttpHeader request, final ClientListener client)
  {
    final WebConnectionListener wcl = new WCL (request, client);
    connectionHandler.getConnection (request, wcl);
  }

  private void handleTimeout (final HttpHeader request, final ClientListener client)
  {
    client.handleTimeout (request);
  }

  private void handleFailure (final HttpHeader request, final ClientListener client, final Exception e)
  {
    client.handleFailure (request, e);
  }

  private abstract class BaseAsyncListener
  {
    protected final HttpHeader request;
    protected final ClientListener client;

    public BaseAsyncListener (final HttpHeader request, final ClientListener client)
    {
      this.request = request;
      this.client = client;
    }

    public void timeout ()
    {
      handleTimeout (request, client);
    }

    public void failed (final Exception e)
    {
      handleFailure (request, client, e);
    }
  }

  private class WCL extends BaseAsyncListener implements WebConnectionListener
  {

    public WCL (final HttpHeader request, final ClientListener client)
    {
      super (request, client);
    }

    public void connectionEstablished (final WebConnection wc)
    {
      sendRequest (request, client, wc);
    }
  }

  private void sendRequest (final HttpHeader request, final ClientListener client, final WebConnection wc)
  {
    final HttpResponseListener hrl = new HRL (request, client, wc);
    try
    {
      final HttpResponseReader rr = new HttpResponseReader (wc.getChannel (),
                                                            nioHandler,
                                                            trafficLogger,
                                                            bufHandler,
                                                            request,
                                                            true,
                                                            true,
                                                            hrl);
      rr.sendRequestAndWaitForResponse ();
    }
    catch (final IOException e)
    {
      handleFailure (request, client, e);
    }
  }

  private class HRL extends BaseAsyncListener implements HttpResponseListener
  {
    private final WebConnection wc;

    public HRL (final HttpHeader request, final ClientListener client, final WebConnection wc)
    {
      super (request, client);
      this.wc = wc;
    }

    public void httpResponse (final HttpHeader response,
                              final BufferHandle bufferHandle,
                              final boolean keepalive,
                              final boolean isChunked,
                              final long dataSize)
    {
      final int status = Integer.parseInt (response.getStatusCode ());
      if (client.followRedirects () && isRedirect (status))
      {
        connectionHandler.releaseConnection (wc);
        final String loc = response.getHeader ("Location");
        client.redirected (request, loc, ClientBase.this);
      }
      else
      {
        final WebConnectionResourceSource wrs = getWebConnectionResouceSource (wc, bufferHandle, isChunked, dataSize);
        client.handleResponse (request, response, wrs);
      }
    }
  }

  /**
   * Check if the status code is a redirect code.
   *
   * @param status
   *        the status code to check
   * @return true if the status code is a redirect
   */
  private boolean isRedirect (final int status)
  {
    return status == 301 || status == 302 || status == 303 || status == 307;
  }

  /**
   * Create the url that the response redirected the request to.
   *
   * @param request
   *        the actual request made
   * @param location
   *        the redirect location
   * @return the redirected url
   * @throws IOException
   *         if the redirect url can not be created
   */
  public URL getRedirectedURL (final HttpHeader request, final String location) throws IOException
  {
    final URL u = new URL (request.getRequestURI ());
    return new URL (u, location);
  }

  private WebConnectionResourceSource getWebConnectionResouceSource (final WebConnection wc,
                                                                     final BufferHandle bufferHandle,
                                                                     final boolean isChunked,
                                                                     final long dataSize)
  {
    return new WebConnectionResourceSource (connectionHandler,
                                            nioHandler,
                                            wc,
                                            bufferHandle,
                                            trafficLogger,
                                            isChunked,
                                            dataSize,
                                            true);
  }
}
