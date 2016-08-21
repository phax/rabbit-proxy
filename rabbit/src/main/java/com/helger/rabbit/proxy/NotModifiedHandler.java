package com.helger.rabbit.proxy;

import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import com.helger.rabbit.cache.ICacheEntry;
import com.helger.rabbit.http.HttpDateParser;
import com.helger.rabbit.http.HttpHeader;

class NotModifiedHandler
{
  /**
   * Check if the request allows us to use a "304 Not modified" response.
   * 
   * @param in
   *        the request being made.
   * @param httpGenerator
   *        the HttpGenerator to use when creating the response headers
   * @param rh
   *        the RequestHandler for this request
   * @return the response header or null
   */
  public HttpHeader is304 (final HttpHeader in, final HttpGenerator httpGenerator, final RequestHandler rh)
  {
    final ICacheEntry <HttpHeader, HttpHeader> entry = rh.getEntry ();
    if (entry == null)
      return null;
    final HttpHeader oldresp = rh.getDataHook ();

    /*
     * if we should have gotten anything but a 2xx or a 304, we should act like
     * IMS and INM was not there.
     */
    /*
     * This should not be needed, it is checked before we enter this method.
     * HttpHeader expfail = checkExpectations (in, oldresp); if (expfail !=
     * null) return expfail;
     */

    final String ifRange = in.getHeader ("If-Range");
    if (ifRange != null)
      return null;

    final String sims = in.getHeader ("If-Modified-Since");
    final String sums = in.getHeader ("If-Unmodified-Since");
    final List <String> vinm = in.getHeaders ("If-None-Match");
    final String et = oldresp.getHeader ("Etag");
    final String range = in.getHeader ("Range");
    final boolean mustUseStrong = range != null;
    boolean etagMatch = false;
    Date ims = null;
    Date ums = null;
    Date dm = null;
    if (sims != null)
      ims = HttpDateParser.getDate (sims);
    if (sums != null)
      ums = HttpDateParser.getDate (sums);
    if (ims != null || ums != null)
    {
      final String lm = oldresp.getHeader ("Last-Modified");
      if (lm == null)
        return ematch (httpGenerator, etagMatch, oldresp);
      dm = HttpDateParser.getDate (lm);
    }

    long diff;
    if (ums != null && (diff = dm.getTime () - ums.getTime ()) >= 0)
    {
      if (mustUseStrong && diff > 60000)
        return httpGenerator.get412 ();
      return httpGenerator.get412 ();
    }

    /* Check if we have a match of etags (Weak comparison). */
    if (et != null)
    {
      for (final String sinm : vinm)
      {
        if (sinm != null &&
            (sinm.equals ("*") ||
             ((mustUseStrong && ETagUtils.checkStrongEtag (et, sinm)) ||
              (!mustUseStrong && ETagUtils.checkWeakEtag (et, sinm)))))
          etagMatch = true;
      }
    }

    if (sims == null)
    {
      /* No IMS, act upon INM only. */
      return ematch (httpGenerator, etagMatch, oldresp);
    }
    /*
     * Here we may or may not have a etagMatch. Etagmatch and
     * bad(nonexistant/unparsable..) IMS => act on etag
     */
    if (ims == null)
    {
      final Logger logger = Logger.getLogger (getClass ().getName ());
      logger.info ("unparseable date: " + sims + " for URL: " + in.getRequestURI ());
      return ematch (httpGenerator, etagMatch, oldresp);
    }

    if (dm == null)
      return ematch (httpGenerator, etagMatch, oldresp);

    if (dm.after (ims))
      return null;
    if (vinm.size () < 1)
    {
      if (mustUseStrong && dm.getTime () - ims.getTime () < 60000)
        return null;
      return httpGenerator.get304 (oldresp);
    }
    return ematch (httpGenerator, etagMatch, oldresp);
  }

  private HttpHeader ematch (final HttpGenerator httpGenerator, final boolean etagMatch, final HttpHeader oldresp)
  {
    if (etagMatch)
      return httpGenerator.get304 (oldresp);
    return null;
  }

  private void updateHeader (final RequestHandler rh, final HttpHeader cachedHeader, final String header)
  {
    final String h = rh.getWebHeader ().getHeader (header);
    if (h != null)
      cachedHeader.setHeader (header, h);
  }

  void updateHeader (final RequestHandler rh)
  {
    if (rh.getEntry () == null)
      return;
    final HttpHeader cachedHeader = rh.getDataHook ();
    updateHeader (rh, cachedHeader, "Date");
    updateHeader (rh, cachedHeader, "Expires");
    updateHeader (rh, cachedHeader, "Content-Location");
    final List <String> ccs = rh.getWebHeader ().getHeaders ("Cache-Control");
    if (ccs.size () > 0)
    {
      cachedHeader.removeHeader ("Cache-Control");
      for (final String cc : ccs)
        cachedHeader.addHeader ("Cache-Control", cc);
    }
    final List <String> varys = rh.getWebHeader ().getHeaders ("Vary");
    if (varys.size () > 0)
    {
      cachedHeader.removeHeader ("Vary");
      for (final String v : varys)
        cachedHeader.addHeader ("Vary", v);
    }

    final WarningsHandler wh = new WarningsHandler ();
    wh.removeWarnings (cachedHeader, true);
    wh.updateWarnings (cachedHeader, rh.getWebHeader ());
  }
}
