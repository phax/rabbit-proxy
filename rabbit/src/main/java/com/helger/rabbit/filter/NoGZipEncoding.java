package com.helger.rabbit.filter;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import com.helger.commons.collection.attr.StringMap;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.proxy.HttpProxy;

/**
 * This is a class that removes "Accept-Encoding: gzip"
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class NoGZipEncoding implements IHttpFilter
{
  private boolean remove = true;

  public HttpHeader doHttpInFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    if (!remove)
      return null;

    final List <String> aes = header.getHeaders ("Accept-Encoding");
    final List <String> faes = new ArrayList <> (aes.size ());
    boolean found = false;
    int s = aes.size ();
    for (int i = 0; i < s; i++)
    {
      String ae = aes.get (i);
      final String lcAe = ae.toLowerCase ();
      final int k = lcAe.indexOf ("gzip");
      if (k != -1)
      {
        found = true;
        final StringBuilder sb = new StringBuilder ();
        if (k > 0)
          sb.append (ae.substring (0, k));
        if (ae.length () > k + 4)
        {
          String rest = ae.substring (k + 4);
          if (rest.charAt (0) == ',')
            rest = rest.substring (1);
          sb.append (rest);
        }
        ae = sb.toString ();
        ae = ae.trim ();
        if (!"".equals (ae))
          faes.add (ae);
      }
    }
    if (found)
    {
      header.removeHeader ("Accept-Encoding");
      s = faes.size ();
      for (int i = 0; i < s; i++)
        header.addHeader ("Accept-Encoding", faes.get (i));
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
  public void setup (final StringMap properties, final HttpProxy proxy)
  {
    final String rs = properties.getOrDefault ("remove", "");
    remove = "true".equalsIgnoreCase (rs);
  }
}
