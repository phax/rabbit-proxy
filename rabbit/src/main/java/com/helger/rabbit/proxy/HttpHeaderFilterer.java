package com.helger.rabbit.proxy;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.helger.rabbit.filter.IHttpFilter;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.util.Config;

/**
 * A class to load and run the HttpFilters.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class HttpHeaderFilterer
{
  private final List <IHttpFilter> httpInFilters;
  private final List <IHttpFilter> httpOutFilters;
  private final List <IHttpFilter> connectFilters;

  public HttpHeaderFilterer (final String in,
                             final String out,
                             final String connect,
                             final Config config,
                             final HttpProxy proxy)
  {
    httpInFilters = new ArrayList <> ();
    loadHttpFilters (in, httpInFilters, config, proxy);

    httpOutFilters = new ArrayList <> ();
    loadHttpFilters (out, httpOutFilters, config, proxy);

    connectFilters = new ArrayList <> ();
    loadHttpFilters (connect, connectFilters, config, proxy);
  }

  private static interface FilterHandler
  {
    HttpHeader filter (IHttpFilter hf, SocketChannel channel, HttpHeader in, Connection con);
  }

  private HttpHeader filter (final Connection con,
                             final SocketChannel channel,
                             final HttpHeader in,
                             final List <IHttpFilter> filters,
                             final FilterHandler fh)
  {
    for (int i = 0, s = filters.size (); i < s; i++)
    {
      final IHttpFilter hf = filters.get (i);
      final HttpHeader badresponse = fh.filter (hf, channel, in, con);
      if (badresponse != null)
        return badresponse;
    }
    return null;
  }

  private static class InFilterer implements FilterHandler
  {
    public HttpHeader filter (final IHttpFilter hf,
                              final SocketChannel channel,
                              final HttpHeader in,
                              final Connection con)
    {
      return hf.doHttpInFiltering (channel, in, con);
    }
  }

  private static class OutFilterer implements FilterHandler
  {
    public HttpHeader filter (final IHttpFilter hf,
                              final SocketChannel channel,
                              final HttpHeader in,
                              final Connection con)
    {
      return hf.doHttpOutFiltering (channel, in, con);
    }
  }

  private static class ConnectFilterer implements FilterHandler
  {
    public HttpHeader filter (final IHttpFilter hf,
                              final SocketChannel channel,
                              final HttpHeader in,
                              final Connection con)
    {
      return hf.doConnectFiltering (channel, in, con);
    }
  }

  /**
   * Runs all input filters on the given header.
   * 
   * @param con
   *        the Connection handling the request
   * @param channel
   *        the SocketChannel for the client
   * @param in
   *        the request.
   * @return null if all is ok, a HttpHeader if this request is blocked.
   */
  public HttpHeader filterHttpIn (final Connection con, final SocketChannel channel, final HttpHeader in)
  {
    return filter (con, channel, in, httpInFilters, new InFilterer ());
  }

  /**
   * Runs all output filters on the given header.
   * 
   * @param con
   *        the Connection handling the request
   * @param channel
   *        the SocketChannel for the client
   * @param in
   *        the response.
   * @return null if all is ok, a HttpHeader if this request is blocked.
   */
  public HttpHeader filterHttpOut (final Connection con, final SocketChannel channel, final HttpHeader in)
  {
    return filter (con, channel, in, httpOutFilters, new OutFilterer ());
  }

  /**
   * Runs all connect filters on the given header.
   * 
   * @param con
   *        the Connection handling the request
   * @param channel
   *        the SocketChannel for the client
   * @param in
   *        the response.
   * @return null if all is ok, a HttpHeader if this request is blocked.
   */
  public HttpHeader filterConnect (final Connection con, final SocketChannel channel, final HttpHeader in)
  {
    return filter (con, channel, in, connectFilters, new ConnectFilterer ());
  }

  private void loadHttpFilters (final String filters,
                                final List <IHttpFilter> ls,
                                final Config config,
                                final HttpProxy proxy)
  {
    final Logger log = Logger.getLogger (getClass ().getName ());
    final String [] filterArray = filters.split (",");
    for (String className : filterArray)
    {
      className = className.trim ();
      if (className.isEmpty ())
        continue;
      try
      {
        className = className.trim ();
        final Class <? extends IHttpFilter> cls = proxy.load3rdPartyClass (className, IHttpFilter.class);
        final IHttpFilter hf = cls.newInstance ();
        hf.setup (config.getProperties (className), proxy);
        ls.add (hf);
      }
      catch (final ClassNotFoundException ex)
      {
        log.log (Level.WARNING, "Could not load http filter class: '" + className + "'", ex);
      }
      catch (final InstantiationException ex)
      {
        log.log (Level.WARNING, "Could not instansiate http filter: '" + className + "'", ex);
      }
      catch (final IllegalAccessException ex)
      {
        log.log (Level.WARNING, "Could not access http filter: '" + className + "'", ex);
      }
    }
  }

  public List <IHttpFilter> getHttpInFilters ()
  {
    return Collections.unmodifiableList (httpInFilters);
  }

  public List <IHttpFilter> getHttpOutFilters ()
  {
    return Collections.unmodifiableList (httpOutFilters);
  }

  public List <IHttpFilter> getConnectFilters ()
  {
    return Collections.unmodifiableList (connectFilters);
  }
}
