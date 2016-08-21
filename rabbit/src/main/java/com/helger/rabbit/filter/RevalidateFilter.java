package com.helger.rabbit.filter;

import java.nio.channels.SocketChannel;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.helger.commons.url.SMap;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.proxy.HttpProxy;

/**
 * This is a class that makes all requests (matching a few criterias) use
 * revalidation even if there is a usable resource in the cache.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class RevalidateFilter implements HttpFilter
{
  private boolean alwaysRevalidate = false;
  private Pattern revalidatePattern = null;

  public HttpHeader doHttpInFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    if (alwaysRevalidate || needsRevalidation (header.getRequestURI ()))
    {
      con.setMustRevalidate ();
    }
    return null;
  }

  private boolean needsRevalidation (final String uri)
  {
    final Matcher m = revalidatePattern.matcher (uri);
    return m.find ();
  }

  public HttpHeader doHttpOutFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    return null;
  }

  public HttpHeader doConnectFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    return null;
  }

  public void setup (final SMap properties, final HttpProxy proxy)
  {
    final String always = properties.getOrDefault ("alwaysrevalidate", "false");
    alwaysRevalidate = Boolean.parseBoolean (always);
    if (!alwaysRevalidate)
    {
      final String mustRevalidate = properties.get ("revalidate");
      if (mustRevalidate == null)
      {
        final Logger logger = Logger.getLogger (getClass ().getName ());
        logger.warning ("alwaysRevalidate is off and no revalidate " + "patterns found, filter is useless.");
        return;
      }
      revalidatePattern = Pattern.compile (mustRevalidate);
    }
  }
}
