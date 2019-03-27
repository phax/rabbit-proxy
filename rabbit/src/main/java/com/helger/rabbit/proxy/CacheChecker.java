package com.helger.rabbit.proxy;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.rabbit.cache.CacheException;
import com.helger.rabbit.cache.ICache;
import com.helger.rabbit.cache.ICacheEntry;
import com.helger.rabbit.http.HttpDateParser;
import com.helger.rabbit.http.HttpHeader;

/**
 * A class to verify if a cache entry can be used.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class CacheChecker
{
  private static final Logger LOGGER = LoggerFactory.getLogger (CacheChecker.class);
  private static final String EXP_ERR = "No expected header found";

  HttpHeader checkExpectations (final Connection con, final HttpHeader header, final HttpHeader webheader)
  {
    final String exp = header.getHeader ("Expect");
    if (exp == null)
      return null;
    if (exp.equals ("100-continue"))
    {
      final String status = webheader.getStatusCode ();
      if (status.equals ("200") || status.equals ("304"))
        return null;
      return con.getHttpGenerator ().get417 (exp);
    }

    final String [] sts = exp.split (";");
    for (final String e : sts)
    {
      final int i = e.indexOf ('=');
      if (i == -1 || i == e.length () - 1)
        return con.getHttpGenerator ().get417 (e);
      final String type = e.substring (0, i);
      final String value = e.substring (i + 1);
      if (type.equals ("expect"))
      {
        final String h = webheader.getHeader (value);
        if (h == null)
          return con.getHttpGenerator ().get417 (EXP_ERR);
      }
    }

    return con.getHttpGenerator ().get417 (exp);
  }

  private HttpHeader checkIfMatch (final Connection con, final HttpHeader header, final RequestHandler rh)
  {
    final ICacheEntry <HttpHeader, HttpHeader> entry = rh.getEntry ();
    if (entry == null)
      return null;
    final HttpHeader oldresp = rh.getDataHook ();
    final HttpHeader expfail = checkExpectations (con, header, oldresp);
    if (expfail != null)
      return expfail;
    final String im = header.getHeader ("If-Match");
    if (im == null)
      return null;
    final String et = oldresp.getHeader ("Etag");
    if (!ETagUtils.checkStrongEtag (et, im))
      return con.getHttpGenerator ().get412 ();
    return null;
  }

  /**
   * Check if we can use the cached entry.
   *
   * @param con
   *        the Connection handling the request
   * @param header
   *        the reques.
   * @param rh
   *        the RequestHandler
   * @return true if the request was handled, false otherwise.
   */
  public boolean checkCachedEntry (final Connection con, final HttpHeader header, final RequestHandler rh)
  {
    con.getCounter ().inc ("Cache hits");
    con.setKeepalive (true);
    HttpHeader resp = checkIfMatch (con, header, rh);
    if (resp == null)
    {
      final NotModifiedHandler nmh = new NotModifiedHandler ();
      resp = nmh.is304 (header, con.getHttpGenerator (), rh);
    }
    if (resp != null)
    {
      con.sendAndTryRestart (resp);
      return true;
    }
    con.setMayCache (false);
    try
    {
      resp = con.setupCachedEntry (rh);
      if (resp != null)
      {
        con.sendAndClose (resp);
        return true;
      }
    }
    catch (final FileNotFoundException e)
    {
      // ignore sorta, to pull resource from the web.
      rh.setContent (null);
      rh.setEntry (null);
    }
    catch (final IOException e)
    {
      rh.setContent (null);
      rh.setEntry (null);
    }
    return false;
  }

  /*
   * If-None-Match: "tag-hbhpjfvtsy"\r\n If-Modified-Since: Thu, 11 Apr 2002
   * 20:56:16 GMT\r\n If-Range: "tag-hbhpjfvtsy"\r\n
   * ----------------------------------- If-Unmodified-Since: Thu, 11 Apr 2002
   * 20:56:16 GMT\r\n If-Match: "tag-ajbqyucqaf"\r\n If-Range:
   * "tag-ajbqyucqaf"\r\n
   */
  public boolean checkConditions (final HttpHeader header, final HttpHeader webheader)
  {
    final String inm = header.getHeader ("If-None-Match");
    if (inm != null)
    {
      final String etag = webheader.getHeader ("ETag");
      if (!ETagUtils.checkWeakEtag (inm, etag))
        return false;
    }
    Date dm = null;
    final String sims = header.getHeader ("If-Modified-Since");
    if (sims != null)
    {
      final Date ims = HttpDateParser.getDate (sims);
      final String lm = webheader.getHeader ("Last-Modified");
      if (lm != null)
      {
        dm = HttpDateParser.getDate (lm);
        if (dm.getTime () - ims.getTime () < 60000) // dm.after (ims))
          return false;
      }
    }
    final String sums = header.getHeader ("If-Unmodified-Since");
    if (sums != null)
    {
      final Date ums = HttpDateParser.getDate (sums);
      if (dm != null)
      {
        if (dm.after (ums))
          return false;
      }
      else
      {
        final String lm = webheader.getHeader ("Last-Modified");
        if (lm != null)
        {
          dm = HttpDateParser.getDate (lm);
          if (dm.after (ums))
            return false;
        }
      }
    }
    return true;
  }

  private void removeCaches (final HttpHeader request,
                             final HttpHeader webHeader,
                             final String type,
                             final ICache <HttpHeader, HttpHeader> cache)
  {
    final String loc = webHeader.getHeader (type);
    if (loc == null)
      return;
    try
    {
      final URL u = new URL (request.getRequestURI ());
      final URL u2 = new URL (u, loc);
      final String host1 = u.getHost ();
      final String host2 = u.getHost ();
      if (!host1.equals (host2))
        return;
      int port1 = u.getPort ();
      if (port1 == -1)
        port1 = 80;
      int port2 = u2.getPort ();
      if (port2 == -1)
        port2 = 80;
      if (port1 != port2)
        return;
      final HttpHeader h = new HttpHeader ();
      h.setRequestURI (u2.toString ());
      cache.remove (h);
    }
    catch (final CacheException e)
    {
      LOGGER.warn ("RemoveCaches failed to remove cache entry: " + request.getRequestURI () + ", " + loc, e);
    }
    catch (final MalformedURLException e)
    {
      LOGGER.warn ("RemoveCaches got bad url: " + request.getRequestURI () + ", " + loc, e);
    }
  }

  private void removeCaches (final HttpHeader request,
                             final HttpHeader webHeader,
                             final ICache <HttpHeader, HttpHeader> cache)
  {
    removeCaches (request, webHeader, "Location", cache);
    removeCaches (request, webHeader, "Content-Location", cache);
  }

  void removeOtherStaleCaches (final HttpHeader request,
                               final HttpHeader webHeader,
                               final ICache <HttpHeader, HttpHeader> cache)
  {
    final String method = request.getMethod ();
    final String status = webHeader.getStatusCode ();
    if ((method.equals ("PUT") || method.equals ("POST")) && status.equals ("201"))
    {
      removeCaches (request, webHeader, cache);
    }
    else
      if (method.equals ("DELETE") && status.equals ("200"))
      {
        removeCaches (request, webHeader, cache);
      }
  }
}
