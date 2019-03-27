package com.helger.rabbit.proxy;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.rabbit.cache.CacheException;
import com.helger.rabbit.cache.ICache;
import com.helger.rabbit.cache.ICacheEntry;
import com.helger.rabbit.http.HttpDateParser;
import com.helger.rabbit.http.HttpHeader;

/**
 * A class used to check for conditional requests.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class ConditionalChecker
{
  private static final Logger LOGGER = LoggerFactory.getLogger (ConditionalChecker.class);

  boolean checkConditional (final Connection con,
                            final HttpHeader header,
                            final RequestHandler rh,
                            final boolean mustRevalidate)
  {
    if (mustRevalidate)
      return setupRevalidation (con, header, rh);
    return (checkVary (con, header, rh) ||
            checkMaxAge (con, header, rh) ||
            checkNoCache (con, header, rh) ||
            checkQuery (con, header, rh) ||
            checkMinFresh (con, header, rh) ||
            checkRevalidation (con, header, rh));
  }

  private boolean checkVary (final Connection con, final HttpHeader req, final RequestHandler rh)
  {
    final ICacheEntry <HttpHeader, HttpHeader> entry = rh.getEntry ();
    if (entry == null)
      return false;
    final HttpHeader resp = rh.getDataHook ();
    final List <String> varies = resp.getHeaders ("Vary");
    final int s = varies.size ();
    for (int i = 0; i < s; i++)
    {
      final String vary = varies.get (i);
      if (vary.equals ("*"))
      {
        con.setMayUseCache (false);
        return false;
      }
      final HttpHeader origreq = entry.getKey ();
      final List <String> vals = origreq.getHeaders (vary);
      final List <String> nvals = req.getHeaders (vary);
      if (vals.size () != nvals.size ())
      {
        return setupRevalidation (con, req, rh);
      }
      for (final String val : vals)
      {
        final int k = nvals.indexOf (val);
        if (k == -1)
        {
          return setupRevalidation (con, req, rh);
        }
      }
    }
    return false;
  }

  private boolean checkMaxAge (final Connection con, final HttpHeader req, final RequestHandler rh, String cached)
  {
    if (cached != null)
    {
      cached = cached.trim ();
      if (cached.startsWith ("max-age=0"))
      {
        return setupRevalidation (con, req, rh);
      }
      final Date now = new Date ();
      final ICacheEntry <HttpHeader, HttpHeader> entry = rh.getEntry ();
      if (checkMaxAge (cached, "max-age=", entry.getCacheTime (), now) ||
          checkMaxAge (cached, "s-maxage=", entry.getCacheTime (), now))
      {
        con.setMayUseCache (false);
        return false;
      }
    }
    return false;
  }

  private boolean checkMaxAge (final String cached, final String type, final long cachetime, final Date now)
  {
    if (cached.startsWith (type))
    {
      String secs = cached.substring (type.length ());
      final int ci = secs.indexOf (',');
      if (ci >= 0)
        secs = secs.substring (0, ci);
      try
      {
        final long l = Long.parseLong (secs) * 1000;
        final long ad = now.getTime () - cachetime;
        if (ad > l)
          return true;
      }
      catch (final NumberFormatException e)
      {
        LOGGER.warn ("Bad number for max-age: '" + cached.substring (8) + "'");
      }
    }
    return false;
  }

  protected boolean checkMaxAge (final Connection con, final HttpHeader req, final RequestHandler rh)
  {
    final ICacheEntry <HttpHeader, HttpHeader> entry = rh.getEntry ();
    if (entry == null)
      return false;
    final List <String> ccs = req.getHeaders ("Cache-Control");
    final int s = ccs.size ();
    for (int i = 0; i < s; i++)
    {
      final String cc = ccs.get (i);
      if (checkMaxAge (con, req, rh, cc))
        return true;
    }
    return false;
  }

  private boolean checkNoCacheHeader (final List <String> v)
  {
    final int s = v.size ();
    for (int i = 0; i < s; i++)
      if (v.get (i).equals ("no-cache"))
        return true;
    return false;
  }

  private boolean checkNoCache (final Connection con, final HttpHeader header, final RequestHandler rh)
  {
    final ICacheEntry <HttpHeader, HttpHeader> entry = rh.getEntry ();
    if (entry == null)
      return false;
    // Only check the response header,
    // request headers with no-cache == refetch.
    final HttpHeader resp = rh.getDataHook ();
    final boolean noCache = checkNoCacheHeader (resp.getHeaders ("Cache-Control"));
    return noCache && setupRevalidation (con, header, rh);
  }

  private boolean checkQuery (final Connection con, final HttpHeader header, final RequestHandler rh)
  {
    final ICacheEntry <HttpHeader, HttpHeader> entry = rh.getEntry ();
    if (entry == null)
      return false;
    final String uri = header.getRequestURI ();
    final int i = uri.indexOf ('?');
    if (i >= 0)
    {
      return setupRevalidation (con, header, rh);
    }
    return false;
  }

  protected long getCacheControlValue (final HttpHeader header, final String cc)
  {
    final List <String> nccs = header.getHeaders ("Cache-Control");
    final int s = nccs.size ();
    for (int i = 0; i < s; i++)
    {
      final String [] sts = nccs.get (i).split (",");
      for (String nc : sts)
      {
        nc = nc.trim ();
        if (nc.startsWith (cc))
          return Long.parseLong (nc.substring (cc.length ()));
      }
    }
    return -1;
  }

  private boolean checkMinFresh (final Connection con, final HttpHeader header, final RequestHandler rh)
  {
    final ICacheEntry <HttpHeader, HttpHeader> entry = rh.getEntry ();
    if (entry == null)
      return false;
    final long minFresh = getCacheControlValue (header, "min-fresh=");
    if (minFresh == -1)
      return false;
    final long maxAge = getCacheControlValue (rh.getDataHook (), "max-age=");
    if (maxAge == -1)
      return false;
    final long currentAge = (System.currentTimeMillis () - entry.getCacheTime ()) / 1000;
    if ((maxAge - currentAge) < minFresh)
      return setupRevalidation (con, header, rh);
    return false;
  }

  private boolean checkRevalidation (final Connection con, final HttpHeader header, final RequestHandler rh)
  {
    final ICacheEntry <HttpHeader, HttpHeader> entry = rh.getEntry ();
    if (entry == null)
      return false;

    final HttpHeader resp = rh.getDataHook ();
    for (final String ncc : resp.getHeaders ("Cache-Control"))
    {
      final String [] sts = ncc.split (",");
      for (String nc : sts)
      {
        nc = nc.trim ();
        if (nc.equals ("must-revalidate") || nc.equals ("proxy-revalidate"))
        {
          con.setMustRevalidate ();
          final long maxAge = getCacheControlValue (rh.getDataHook (), "max-age=");
          if (maxAge >= 0)
          {
            final long currentAge = (System.currentTimeMillis () - entry.getCacheTime ()) / 1000;
            if (maxAge == 0 || currentAge > maxAge)
            {
              return setupRevalidation (con, header, rh);
            }
          }
        }
        else
          if (nc.startsWith ("s-maxage="))
          {
            con.setMustRevalidate ();
            final long sm = Long.parseLong (nc.substring ("s-maxage=".length ()));
            if (sm >= 0)
            {
              final long currentAge = (System.currentTimeMillis () - entry.getCacheTime ()) / 1000;
              if (sm == 0 || currentAge > sm)
              {
                return setupRevalidation (con, header, rh);
              }
            }
          }
      }
    }
    return false;
  }

  // Return true if we are sending If-None-Match, or If-Modified-Since
  private boolean setupRevalidation (final Connection con, final HttpHeader req, final RequestHandler rh)
  {
    final ICacheEntry <HttpHeader, HttpHeader> entry = rh.getEntry ();
    con.setMayUseCache (false);
    final String method = req.getMethod ();
    // if we can not filter (noproxy-request) we can not revalidate...
    if (method.equals ("GET") && entry != null && con.getMayFilter ())
    {
      final HttpHeader resp = rh.getDataHook ();
      final String etag = resp.getHeader ("ETag");
      final String lmod = resp.getHeader ("Last-Modified");
      if (etag != null)
      {
        final String inm = req.getHeader ("If-None-Match");
        if (inm == null)
        {
          req.setHeader ("If-None-Match", etag);
          con.setAddedINM ();
        }
        return true;
      }
      else
        if (lmod != null)
        {
          final String ims = req.getHeader ("If-Modified-Since");
          if (ims == null)
          {
            req.setHeader ("If-Modified-Since", lmod);
            con.setAddedIMS ();
          }
          return true;
        }
        else
        {
          con.setMayUseCache (false);
          return false;
        }
    }
    return false;
  }

  boolean checkMaxStale (final HttpHeader req, final RequestHandler rh)
  {
    for (String cc : req.getHeaders ("Cache-Control"))
    {
      cc = cc.trim ();
      if (cc.equals ("max-stale"))
      {
        if (rh.getEntry () != null)
        {
          final HttpHeader resp = rh.getDataHook ();
          final long maxAge = rh.getCond ().getCacheControlValue (resp, "max-age=");
          if (maxAge >= 0)
          {
            final long now = System.currentTimeMillis ();
            long currentAge = (now - rh.getEntry ().getCacheTime ()) / 1000;
            final String age = resp.getHeader ("Age");
            if (age != null)
              currentAge += Long.parseLong (age);
            if (currentAge > maxAge)
            {
              resp.addHeader ("Warning", "110 RabbIT \"Response is stale\"");
            }
          }
        }
        return true;
      }
    }
    return false;
  }

  // Remove the cache entry if it is stale according to header 'str'
  private void checkStaleHeader (final HttpHeader header,
                                 final HttpHeader webHeader,
                                 final HttpHeader cachedWebHeader,
                                 final String str,
                                 final ICache <HttpHeader, HttpHeader> cache) throws CacheException
  {
    final String cln = webHeader.getHeader (str);
    final String clo = cachedWebHeader.getHeader (str);
    // if the headers are not equal, remove cache entry
    if (clo != null)
    {
      if (!clo.equals (cln))
        cache.remove (header);
    }
    else
    {
      // if the header exists for one but not the other, remove cache entry
      if (cln != null)
        cache.remove (header);
    }
  }

  // Returns false if the cached Date header is newer,
  // indicating that we should not cache
  protected boolean checkStaleCache (final HttpHeader requestHeader,
                                     final Connection con,
                                     final RequestHandler rh) throws CacheException
  {
    if (rh.getEntry () == null)
      return true;
    if (rh.getWebHeader ().getStatusCode ().trim ().equals ("304"))
      return true;
    final HttpHeader cachedWebHeader = rh.getDataHook ();

    final String sd = rh.getWebHeader ().getHeader ("Date");
    final String cd = cachedWebHeader.getHeader ("Date");
    if (sd != null && cd != null)
    {
      final Date d1 = HttpDateParser.getDate (sd);
      final Date d2 = HttpDateParser.getDate (cd);
      // if we get a response with a date older than we have,
      // we keep our cache.
      if (d1 != null && d1.before (d2))
        return false;
    }
    final ICache <HttpHeader, HttpHeader> cache = con.getProxy ().getCache ();
    // check that some headers are equal
    if (rh.getWebHeader ().getStatusCode ().equals ("200"))
      checkStaleHeader (requestHeader, rh.getWebHeader (), cachedWebHeader, "Content-Length", cache);
    checkStaleHeader (requestHeader, rh.getWebHeader (), cachedWebHeader, "Content-MD5", cache);
    checkStaleHeader (requestHeader, rh.getWebHeader (), cachedWebHeader, "ETag", cache);
    checkStaleHeader (requestHeader, rh.getWebHeader (), cachedWebHeader, "Last-Modified", cache);
    return true;
  }
}
