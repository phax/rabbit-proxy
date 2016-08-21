package com.helger.rabbit.filter;

import java.nio.channels.SocketChannel;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.helger.commons.url.SMap;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.proxy.HttpProxy;

/**
 * This is a filter that set up rabbit for reverse proxying.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ReverseProxy implements IHttpFilter
{
  private String matcher = null;
  private String replacer = null;
  private Pattern deny = null;
  private boolean allowMeta = false;

  public HttpHeader doHttpInFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    final String s = header.getRequestURI ();
    if (deny != null)
    {
      final Matcher m = deny.matcher (s);
      if (m.matches () && allowMeta)
      {
        final String metaStart = "http://" +
                                 con.getProxy ().getHost ().getHostName () +
                                 ":" +
                                 con.getProxy ().getPort () +
                                 "/";
        if (!s.startsWith (metaStart))
        {
          return con.getHttpGenerator ().get403 ();
        }
      }
    }
    if (matcher != null && replacer != null && s != null && s.length () > 0 && s.charAt (0) == '/')
    {
      final String newRequest = s.replaceAll (matcher, replacer);
      header.setRequestURI (newRequest);
    }
    return null;
  }

  public HttpHeader doHttpOutFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    return null;
  }

  public HttpHeader doConnectFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    return null;
  }

  /**
   * Setup this class with the given properties.
   *
   * @param properties
   *        the new configuration of this class.
   */
  public void setup (final SMap properties, final HttpProxy proxy)
  {
    matcher = properties.getOrDefault ("transformMatch", "");
    replacer = properties.getOrDefault ("transformTo", "");
    final String denyString = properties.get ("deny");
    if (denyString != null)
      deny = Pattern.compile (denyString);
    allowMeta = properties.getOrDefault ("allowMeta", "true").equalsIgnoreCase ("true");
  }
}
