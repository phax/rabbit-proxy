package com.helger.rabbit.filter;

import java.nio.channels.SocketChannel;
import java.util.Map;

import com.helger.commons.url.SMap;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.proxy.HttpProxy;

/**
 * This is a class that sets headers in the request and/or response. Mostly an
 * example of how to set headers.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class SetHeaderFilter implements HttpFilter
{
  private SMap props;

  private void addHeaders (final HttpHeader header, final String prefix)
  {
    for (final Map.Entry <String, String> me : props.entrySet ())
    {
      String key = me.getKey ();
      if (key.startsWith (prefix))
      {
        key = key.substring (prefix.length ());
        final String value = me.getValue ();
        header.addHeader (key, value);
      }
    }
  }

  public HttpHeader doHttpInFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    addHeaders (header, "request.");
    return null;
  }

  public HttpHeader doHttpOutFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {

    addHeaders (header, "response.");
    return null;
  }

  public HttpHeader doConnectFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    addHeaders (header, "connect.");
    return null;
  }

  public void setup (final SMap properties, final HttpProxy proxy)
  {
    this.props = properties;
  }
}
