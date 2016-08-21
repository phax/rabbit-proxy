package com.helger.rabbit.filter;

import java.nio.channels.SocketChannel;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.helger.commons.url.SMap;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.proxy.HttpProxy;
import com.helger.rabbit.util.PatternHelper;

/**
 * This is a class that blocks access to certain part of the www. You can either
 * specify a deny filter, using blockURLmatching or you can specify an accept
 * filter, using allowURLmatching. If you specify an accept filter, then no
 * other urls will be accepted.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class BlockFilter implements HttpFilter
{
  private Pattern blockPattern;
  private Pattern allowPattern;

  public HttpHeader doHttpInFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    if (allowPattern != null)
    {
      final Matcher m = allowPattern.matcher (header.getRequestURI ());
      if (m.find ())
        return null;
      return con.getHttpGenerator ().get403 ();
    }

    if (blockPattern == null)
      return null;
    final Matcher m = blockPattern.matcher (header.getRequestURI ());
    if (m.find ())
      return con.getHttpGenerator ().get403 ();
    return null;
  }

  public HttpHeader doHttpOutFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    return null;
  }

  public HttpHeader doConnectFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    // TODO: possibly block connect requests?
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
    final PatternHelper ph = new PatternHelper ();
    blockPattern = ph.getPattern (properties, "blockURLmatching", "BlockFilter: bad pattern: ");
    allowPattern = ph.getPattern (properties, "allowURLmatching", "AllowFilter: bad pattern: ");
  }
}
