package com.helger.rabbit.proxy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.rabbit.handler.BaseHandler;
import com.helger.rabbit.http.ContentRangeParser;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.io.Range;

/**
 * A class that tries to setup a resource from the cache
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class SCC
{
  private static final Logger LOGGER = LoggerFactory.getLogger (SCC.class);

  private final Connection con;
  private final HttpHeader header;
  private final RequestHandler rh;

  /**
   * Create a new SCC, a helper that sets up resources from the cache.
   *
   * @param con
   *        the Connection handling the request
   * @param header
   *        the request header
   * @param rh
   *        the RequestHandler that will be filled in.
   */
  public SCC (final Connection con, final HttpHeader header, final RequestHandler rh)
  {
    this.con = con;
    this.header = header;
    this.rh = rh;
  }

  /**
   * @return null if everything looks ok, non-null on an errornous request.
   * @throws IOException
   *         if the cached resource can not be read
   */
  public HttpHeader establish () throws IOException
  {
    final String ifRange = header.getHeader ("If-Range");
    boolean mayRange = true;
    rh.setWebHeader (rh.getDataHook ());
    if (ifRange != null)
    {
      final String etag = rh.getWebHeader ().getHeader ("ETag");
      if (etag != null)
      {
        /*
         * rfc is fuzzy about if it should be weak or strong match here.
         * mayRange = checkWeakEtag (rh.webheader.getHeader ("ETag"), ifRange);
         */
        mayRange = ETagUtils.checkStrongEtag (etag, ifRange);
      }
      else
      {
        // we can't use strong validators on dates!
        mayRange = false;
      }
      final CacheChecker cck = new CacheChecker ();
      final boolean cc = cck.checkConditions (header, rh.getWebHeader ());
      if (mayRange && !cc)
      {
        // abort...
        rh.setWebHeader (null);
        rh.setContent (null);
        return null;
      }
      else
        if (!cc)
        {
          rh.setContent (null);
          return null;
        }
    }

    List <Range> ranges = null;
    if (mayRange)
    {
      try
      {
        ranges = getRanges (header);
      }
      catch (final IllegalArgumentException e)
      {
        return con.getHttpGenerator ().get416 (e);
      }
    }
    con.setChunking (false);
    if (ranges != null)
    {
      final long totalSize = getTotalSize (rh);
      if (!haveAllRanges (ranges, rh, totalSize))
      {
        // abort and get from web..
        rh.setWebHeader (null);
        rh.setContent (null);
        return null;
      }
      setupRangedEntry (ifRange, ranges, totalSize);
    }
    else
    {
      final HttpProxy proxy = con.getProxy ();
      rh.setContent (new CacheResourceSource (proxy.getCache (),
                                              rh.getEntry (),
                                              con.getNioHandler (),
                                              proxy.getBufferHandler ()));
      rh.setSize (rh.getEntry ().getSize ());
      rh.getWebHeader ().setStatusCode ("200");
      rh.getWebHeader ().setReasonPhrase ("OK");
    }
    setAge ();
    // do we have a handler for it?
    final HttpProxy proxy = con.getProxy ();
    final String ctype = rh.getWebHeader ().getHeader ("Content-Type");
    if (ctype != null)
      rh.setHandlerFactory (proxy.getCacheHandlerFactory (ctype));
    if (rh.getHandlerFactory () == null || ranges != null)
      // Simply send, its
      // already filtered.
      rh.setHandlerFactory (new BaseHandler ());
    final WarningsHandler wh = new WarningsHandler ();
    wh.removeWarnings (rh.getWebHeader (), false);
    return null;
  }

  private void setupRangedEntry (final String ifRange,
                                 final List <Range> ranges,
                                 final long totalSize) throws IOException
  {
    final HttpProxy proxy = con.getProxy ();
    rh.setContent (new RandomCacheResourceSource (proxy.getCache (),
                                                  rh,
                                                  con.getNioHandler (),
                                                  proxy.getBufferHandler (),
                                                  ranges,
                                                  totalSize));
    con.setChunking (false);
    rh.setWebHeader (con.getHttpGenerator ().get206 (ifRange, rh.getWebHeader ()));
    con.setStatusCode ("206");
    if (ranges.size () > 1)
    {
      // prepare multipart...
      rh.getWebHeader ().removeHeader ("Content-Length");
      final String CT = "multipart/byteranges; boundary=THIS_STRING_SEPARATES";
      rh.getWebHeader ().setHeader ("Content-Type", CT);
    }
    else
    {
      final Range r = ranges.get (0);
      rh.getWebHeader ().setHeader ("Content-Range", "bytes " + r.getStart () + "-" + r.getEnd () + "/" + totalSize);
      rh.setSize (r.getEnd () - r.getStart () + 1);
      rh.getWebHeader ().setHeader ("Content-Length", "" + rh.getSize ());
    }
  }

  private void setAge ()
  {
    final String age = rh.getWebHeader ().getHeader ("Age");
    final long now = System.currentTimeMillis ();
    long secs = (now - rh.getEntry ().getCacheTime ()) / 1000;
    if (age != null)
    {
      try
      {
        final long l = Long.parseLong (age);
        secs += l;
      }
      catch (final NumberFormatException e)
      {
        LOGGER.warn ("bad Age : '" + age + "'");
      }
    }
    rh.getWebHeader ().setHeader ("Age", "" + secs);
  }

  private List <Range> getRanges (final HttpHeader header)
  {
    // Range: bytes=10-,11-10\r\n
    final List <String> ranges = header.getHeaders ("Range");
    final int z = ranges.size ();
    if (z == 0)
      return null;
    final List <Range> ret = new ArrayList <> ();
    try
    {
      for (int i = 0; i < z; i++)
      {
        String rs = (ranges.get (i)).trim ();
        if (!rs.startsWith ("bytes"))
          return null;
        rs = rs.substring (5);
        final int j = rs.indexOf ('=');
        if (j == -1)
          return null;
        rs = rs.substring (j + 1);
        final String [] st = rs.split (",");
        for (final String r : st)
        {
          final Range range = parseRange (r);
          if (range == null)
            return null;
          ret.add (range);
        }
      }
    }
    catch (final NumberFormatException e)
    {
      return null;
    }
    return ret;
  }

  private static final String SBTZ = "bad range: start bigger than size";
  private static final String SAELTZ = "bad range: start and end both less than zero";
  private static final String SLTZ = "bad range: start less than zero";
  private static final String FR = "bad range: full range";

  private Range parseRange (final String r)
  {
    final int d = r.indexOf ('-');
    if (d == -1)
      throw new NumberFormatException ("bad range: no '-'");
    final String s = r.substring (0, d).trim ();
    final String e = r.substring (d + 1).trim ();
    long start;
    long end;
    final long size = rh.getEntry ().getSize ();
    if (s.length () > 0)
    {
      start = Integer.parseInt (s);
      if (e.length () > 0)
      {
        end = Integer.parseInt (e);
      }
      else
      {
        // to the end...
        end = size;
      }
      if (start > size)
        throw new IllegalArgumentException (SBTZ);
      if (start > end) // ignore this...
        return null;
      if (start < 0 || end < 0)
        throw new IllegalArgumentException (SAELTZ);
      return new Range (start, end);
    }
    else
      if (e.length () > 0)
      {
        // no start so this many bytes from the end...
        start = Integer.parseInt (e);
        if (start < 0)
          throw new IllegalArgumentException (SLTZ);
        start = size - start;
        end = size;
        return new Range (start, end);
      }
      else
      {
        // "-"
        throw new NumberFormatException (FR);
      }
  }

  private long getTotalSize (final RequestHandler rh)
  {
    final String cr = rh.getWebHeader ().getHeader ("Content-Range");
    if (cr != null)
    {
      final int i = cr.lastIndexOf ('/');
      if (i != -1)
      {
        return Long.parseLong (cr.substring (i + 1));
      }
    }
    final String cl = rh.getWebHeader ().getHeader ("Content-Length");
    if (cl != null)
      return Long.parseLong (cl);
    // ok fallback...
    return rh.getEntry ().getSize ();
  }

  private boolean haveAllRanges (final List <Range> ranges, final RequestHandler rh, final long totalSize)
  {
    // do we have all of it?
    if (rh.getEntry ().getSize () == totalSize)
      return true;

    // check each range...
    // TODO add support for many parts...
    // Content-Range: bytes 0-4/25\r\n
    final String cr = rh.getWebHeader ().getHeader ("Content-Range");
    if (cr == null) // TODO check if its ok to return true here..
      // if we do not have a content range we ought to have full resource.
      return false;

    for (final Range r : ranges)
    {
      final long start = r.getStart ();
      final long end = r.getEnd ();
      final String t = "bytes " + start + "-" + end + "/" + totalSize;
      if (!t.equals (cr))
      {
        final ContentRangeParser crp = new ContentRangeParser (cr);
        if (crp.isValid () && (crp.getStart () > start || crp.getEnd () < end))
          return false;
      }
    }
    return true;
  }
}
