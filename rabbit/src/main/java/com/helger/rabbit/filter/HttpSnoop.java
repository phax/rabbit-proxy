package com.helger.rabbit.filter;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import com.helger.commons.url.SMap;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.io.BufferHandle;
import com.helger.rabbit.proxy.ClientResourceHandler;
import com.helger.rabbit.proxy.ClientResourceListener;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.proxy.HttpProxy;

/**
 * This is a class that prints the Http headers on the standard out stream.
 */
public class HttpSnoop implements IHttpFilter
{
  private enum ESnoopMode
  {
    NORMAL,
    REQUEST_LINE,
    FULL
  }

  private ESnoopMode mode;

  public HttpHeader doHttpInFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    if (mode == ESnoopMode.REQUEST_LINE)
    {
      System.out.println (con.getRequestLine ());
    }
    else
    {
      System.out.print (header.toString ());
      if (mode == ESnoopMode.FULL)
      {
        final ClientResourceHandler crh = con.getClientResourceHandler ();
        if (crh != null)
          crh.addContentListener (new ContentLogger (header));
      }
    }
    return null;
  }

  private static class ContentLogger implements ClientResourceListener
  {
    private final HttpHeader header;

    public ContentLogger (final HttpHeader header)
    {
      this.header = header;
    }

    public void resourceDataRead (final BufferHandle bufHandle)
    {
      ByteBuffer buf = bufHandle.getBuffer ();
      buf = buf.duplicate ();
      final byte [] data = new byte [buf.remaining ()];
      buf.get (data);
      try
      {
        // TODO: better handling of charset,
        // TODO: for now we use latin-1 since that has no
        // TOdO: invalid characters.
        final String s = new String (data, "ISO-8859-1");
        System.out.println (header.getRequestLine () + " request content:\n" + s);
      }
      catch (final UnsupportedEncodingException e)
      {
        throw new RuntimeException ("Failed to get charset", e);
      }
    }
  }

  public HttpHeader doHttpOutFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    if (mode == ESnoopMode.REQUEST_LINE)
    {
      System.out.println (con.getRequestLine () + "\n" + header.getStatusLine ());
    }
    else
    {
      System.out.print (con.getRequestLine () + "\n" + header.toString ());
    }
    return null;
  }

  public HttpHeader doConnectFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    if (mode == ESnoopMode.REQUEST_LINE)
    {
      System.out.println (con.getRequestLine ());
    }
    else
    {
      System.out.print (header.toString ());
    }
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
    String smo = properties.getOrDefault ("mode", "NORMAL");
    smo = smo.toUpperCase ();
    mode = ESnoopMode.valueOf (smo);
  }
}
