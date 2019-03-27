package com.helger.rabbit.handler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Date;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.collection.attr.StringMap;
import com.helger.rabbit.cache.CacheException;
import com.helger.rabbit.cache.ICache;
import com.helger.rabbit.cache.ICacheEntry;
import com.helger.rabbit.http.ContentRangeParser;
import com.helger.rabbit.http.HttpDateParser;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.BlockListener;
import com.helger.rabbit.httpio.BlockSender;
import com.helger.rabbit.httpio.BlockSentListener;
import com.helger.rabbit.httpio.ChunkEnder;
import com.helger.rabbit.httpio.HttpHeaderSender;
import com.helger.rabbit.httpio.HttpHeaderSentListener;
import com.helger.rabbit.httpio.IResourceSource;
import com.helger.rabbit.httpio.TransferHandler;
import com.helger.rabbit.httpio.TransferListener;
import com.helger.rabbit.io.BufferHandle;
import com.helger.rabbit.io.FileHelper;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.proxy.HttpProxy;
import com.helger.rabbit.proxy.PartialCacher;
import com.helger.rabbit.proxy.TrafficLoggerHandler;

/**
 * This class is an implementation of the Handler interface. This handler does
 * no filtering, it only sends the data as effective as it can.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class BaseHandler implements IHandler, IHandlerFactory, HttpHeaderSentListener, BlockListener, BlockSentListener
{
  private static final Logger LOGGER = LoggerFactory.getLogger (BaseHandler.class);

  /** The Connection handling the request. */
  protected Connection con;
  /** The traffic LOGGER handler. */
  protected TrafficLoggerHandler tlh;
  /** The actual request made. */
  protected HttpHeader request;
  /** The actual response. */
  protected HttpHeader response;
  /** The resource */
  protected IResourceSource content;

  /** The cache entry if available. */
  protected ICacheEntry <HttpHeader, HttpHeader> entry = null;
  /** The cache channel. */
  protected WritableByteChannel cacheChannel;

  /** May we cache this request. */
  protected boolean mayCache;
  /** May we filter this request */
  protected boolean mayFilter;
  /** The length of the data beeing handled or -1 if unknown. */
  protected long size = -1;
  /** The total amount of data that we read. */
  protected long totalRead = 0;

  /** The flag for the last empty chunk */
  private boolean emptyChunkSent = false;

  /**
   * For creating the factory.
   */
  public BaseHandler ()
  {
    // empty
  }

  /**
   * Create a new BaseHandler for the given request.
   *
   * @param con
   *        the Connection handling the request.
   * @param tlh
   *        the TrafficLoggerHandler to update with traffic information
   * @param request
   *        the actual request made.
   * @param response
   *        the actual response.
   * @param content
   *        the resource.
   * @param mayCache
   *        May we cache this request?
   * @param mayFilter
   *        May we filter this request?
   * @param size
   *        the size of the data beeing handled.
   */
  public BaseHandler (final Connection con,
                      final TrafficLoggerHandler tlh,
                      final HttpHeader request,
                      final HttpHeader response,
                      final IResourceSource content,
                      final boolean mayCache,
                      final boolean mayFilter,
                      final long size)
  {
    this.con = con;
    this.tlh = tlh;
    this.request = request;
    this.response = response;
    if (!request.isDot9Request () && response == null)
      throw new IllegalArgumentException ("response may not be null");
    this.content = content;
    this.mayCache = mayCache;
    this.mayFilter = mayFilter;
    this.size = size;
  }

  public IHandler getNewInstance (final Connection con,
                                  final TrafficLoggerHandler tlh,
                                  final HttpHeader header,
                                  final HttpHeader webHeader,
                                  final IResourceSource content,
                                  final boolean mayCache,
                                  final boolean mayFilter,
                                  final long size)
  {
    return new BaseHandler (con, tlh, header, webHeader, content, mayCache, mayFilter, size);
  }

  /**
   * Handle the request. A request is made in these steps: <xmp> sendHeader ();
   * addCache (); prepare (); send (); finishData (); finish (); </xmp> Note
   * that finish is always called, no matter what exceptions are thrown. The
   * middle steps are most probably only performed if the previous steps have
   * all succeded
   */
  public void handle ()
  {
    if (request.isDot9Request ())
      send ();
    else
      sendHeader ();
  }

  /**
   * ®return false if this handler never modifies the content.
   */
  public boolean changesContentSize ()
  {
    return false;
  }

  protected void sendHeader ()
  {
    try
    {
      final HttpHeaderSender hhs = new HttpHeaderSender (con.getChannel (),
                                                         con.getNioHandler (),
                                                         tlh.getClient (),
                                                         response,
                                                         false,
                                                         this);
      hhs.sendHeader ();
    }
    catch (final IOException e)
    {
      failed (e);
    }
  }

  public void httpHeaderSent ()
  {
    addCache ();
    prepare ();
  }

  /**
   * This method is used to prepare the data for the resource being sent. This
   * method does nothing here.
   */
  protected void prepare ()
  {
    send ();
  }

  /**
   * This method is used to finish the data for the resource being sent. This
   * method will send an end chunk if needed and then call finish
   */
  protected void finishData ()
  {
    if (con.getChunking () && !emptyChunkSent)
    {
      emptyChunkSent = true;
      final BlockSentListener bsl = new Finisher ();
      final ChunkEnder ce = new ChunkEnder ();
      ce.sendChunkEnding (con.getChannel (), con.getNioHandler (), tlh.getClient (), bsl);
    }
    else
    {
      finish (true);
    }
  }

  private void removePrivateParts (final HttpHeader header, final String type)
  {
    for (final String val : header.getHeaders ("Cache-Control"))
    {
      final int j = val.indexOf (type);
      if (j >= 0)
      {
        final String p = val.substring (j + type.length ());
        final StringTokenizer st = new StringTokenizer (p, ",\"");
        while (st.hasMoreTokens ())
        {
          final String t = st.nextToken ();
          header.removeHeader (t);
        }
      }
    }
  }

  private void removePrivateParts (final HttpHeader header)
  {
    removePrivateParts (header, "private=");
    removePrivateParts (header, "no-cache=");
  }

  /**
   * Mark the current response as a partial response.
   *
   * @param shouldbe
   *        the number of byte that the resource ought to be
   */
  protected void setPartialContent (final long shouldbe)
  {
    response.setHeader ("RabbIT-Partial", "" + shouldbe);
  }

  /**
   * Close nesseccary channels and adjust the cached files. If you override this
   * one, remember to call super.finish ()!
   *
   * @param good
   *        if true then the connection may be restarted, if false then the
   *        connection may not be restared
   */
  protected void finish (final boolean good)
  {
    boolean ok = false;
    try
    {
      if (content != null)
        content.release ();
      if (cacheChannel != null)
      {
        try
        {
          cacheChannel.close ();
        }
        catch (final IOException e)
        {
          failed (e);
        }
      }

      finishCache ();
      if (response != null && response.getHeader ("Content-Length") != null)
        con.setContentLength (response.getHeader ("Content-length"));

      ok = true;
    }
    finally
    {
      // and clean up...
      request = null;
      response = null;
      content = null;
      entry = null;
      cacheChannel = null;
    }
    // Not sure why we need this, seems to call finish multiple times.
    if (con != null)
    {
      if (good && ok)
        con.logAndTryRestart ();
      else
        con.logAndClose ();
    }
    tlh = null;
    con = null;
  }

  private void finishCache ()
  {
    if (entry == null || !mayCache)
      return;
    final ICache <HttpHeader, HttpHeader> cache = con.getProxy ().getCache ();
    final File entryName = cache.getEntryName (entry.getID (), false, null);
    final long filesize = entryName.length ();
    final String cl = response.getHeader ("Content-Length");
    if (cl == null)
    {
      response.removeHeader ("Transfer-Encoding");
      response.setHeader ("Content-Length", "" + filesize);
    }
    removePrivateParts (response);
    try
    {
      cache.addEntry (entry);
    }
    catch (final CacheException e)
    {
      LOGGER.warn ("Failed to add cache entry: " + request.getRequestURI (), e);
    }
  }

  /**
   * Try to use the resource size to decide if we may cache or not. If the size
   * is known and the size is bigger than the maximum cache size, then we dont
   * want to cache the resource.
   *
   * @return true if the current resource may be cached, false otherwise
   */
  protected boolean mayCacheFromSize ()
  {
    final ICache <HttpHeader, HttpHeader> cache = con.getProxy ().getCache ();
    final long maxSize = cache.getCacheConfiguration ().getMaxSize ();
    return !(maxSize == 0 || (size > 0 && size > maxSize));
  }

  /**
   * Check if this handler may force the cached resource to be less than the
   * cache max size.
   *
   * @return true
   */
  protected boolean mayRestrictCacheSize ()
  {
    return true;
  }

  /**
   * Set the expire time on the cache entry. If the expire time is 0 then the
   * cache is not written.
   */
  private void setCacheExpiry ()
  {
    final String expires = response.getHeader ("Expires");
    if (expires != null)
    {
      Date exp = HttpDateParser.getDate (expires);
      // common case, handle it...
      if (exp == null && expires.equals ("0"))
        exp = new Date (0);
      if (exp != null)
      {
        final long now = System.currentTimeMillis ();
        if (now > exp.getTime ())
        {
          LOGGER.info ("expire date in the past: '" + expires + "'");
          entry = null;
          return;
        }
        entry.setExpires (exp.getTime ());
      }
      else
      {
        LOGGER.info ("unable to parse expire date: '" + expires + "' for URI: '" + request.getRequestURI () + "'");
        entry = null;
      }
    }
  }

  private void updateRange (final ICacheEntry <HttpHeader, HttpHeader> old,
                            final PartialCacher pc,
                            final ICache <HttpHeader, HttpHeader> cache) throws CacheException
  {
    final HttpHeader oldRequest = old.getKey ();
    final HttpHeader oldResponse = old.getDataHook ();
    String cr = oldResponse.getHeader ("Content-Range");
    if (cr == null)
    {
      final String cl = oldResponse.getHeader ("Content-Length");
      if (cl != null)
      {
        final long size = Long.parseLong (cl);
        cr = "bytes 0-" + (size - 1) + "/" + size;
      }
    }
    final ContentRangeParser crp = new ContentRangeParser (cr);
    if (crp.isValid ())
    {
      final long start = crp.getStart ();
      final long end = crp.getEnd ();
      final long total = crp.getTotal ();
      final String t = total < 0 ? "*" : Long.toString (total);
      if (end == pc.getStart () - 1)
      {
        oldRequest.setHeader ("Range", "bytes=" + start + "-" + end);
        oldResponse.setHeader ("Content-Range", "bytes " + start + "-" + pc.getEnd () + "/" + t);
      }
      else
      {
        oldRequest.addHeader ("Range", "bytes=" + start + "-" + end);
        oldResponse.addHeader ("Content-Range", "bytes " + start + "-" + pc.getEnd () + "/" + t);
      }
      cache.entryChanged (old, oldRequest, oldResponse);
    }
  }

  private void setupPartial (final ICacheEntry <HttpHeader, HttpHeader> oldEntry,
                             final ICacheEntry <HttpHeader, HttpHeader> entry,
                             final File entryName,
                             final ICache <HttpHeader, HttpHeader> cache) throws IOException
  {
    if (oldEntry != null)
    {
      final File oldName = cache.getEntryName (oldEntry.getID (), true, null);
      final PartialCacher pc = new PartialCacher (oldName, response);
      cacheChannel = pc.getChannel ();
      try
      {
        updateRange (oldEntry, pc, cache);
      }
      catch (final CacheException e)
      {
        LOGGER.warn ("Failed to update range: " + request.getRequestURI (), e);
      }
      return;
    }
    entry.setDataHook (response);
    final PartialCacher pc = new PartialCacher (entryName, response);
    cacheChannel = pc.getChannel ();
  }

  /**
   * Set up the cache stream if available.
   */
  protected void addCache ()
  {
    if (mayCache && mayCacheFromSize ())
    {
      final ICache <HttpHeader, HttpHeader> cache = con.getProxy ().getCache ();
      try
      {
        entry = cache.newEntry (request);
      }
      catch (final CacheException e)
      {
        LOGGER.warn ("Failed to create new entry for: " + request + ", will not cache", e);
      }
      setCacheExpiry ();
      if (entry == null)
      {
        LOGGER.info ("Expiry =< 0 set on entry, will not cache");
        return;
      }
      final File entryName = cache.getEntryName (entry.getID (), false, null);
      if (response.getStatusCode ().equals ("206"))
      {
        ICacheEntry <HttpHeader, HttpHeader> oldEntry = null;
        try
        {
          oldEntry = cache.getEntry (request);
        }
        catch (final CacheException e)
        {
          LOGGER.warn ("Failed to get old entry: " + request.getRequestURI (), e);
        }
        try
        {
          setupPartial (oldEntry, entry, entryName, cache);
        }
        catch (final IOException e)
        {
          LOGGER.warn ("Got IOException, not updating cache", e);
          entry = null;
          cacheChannel = null;
        }
      }
      else
      {
        entry.setDataHook (response);
        try
        {
          @SuppressWarnings ("resource")
          final FileOutputStream cacheStream = new FileOutputStream (entryName);
          /*
           * TODO: implement this: if (mayRestrictCacheSize ()) cacheStream =
           * new MaxSizeOutputStream (cacheStream, cache.getMaxSize ());
           */
          cacheChannel = cacheStream.getChannel ();
        }
        catch (final IOException e)
        {
          LOGGER.warn ("Got IOException, not caching", e);
          entry = null;
          cacheChannel = null;
        }
      }
    }
  }

  /**
   * Check if this handler supports direct transfers.
   *
   * @return this handler always return true.
   */
  protected boolean mayTransfer ()
  {
    return true;
  }

  protected void send ()
  {
    if (mayTransfer () && content.length () > 0 && content.supportsTransfer ())
    {
      final TransferListener tl = new ContentTransferListener ();
      final TransferHandler th = new TransferHandler (con.getNioHandler (),
                                                      content,
                                                      con.getChannel (),
                                                      tlh.getCache (),
                                                      tlh.getClient (),
                                                      tl);
      th.transfer ();
    }
    else
    {
      content.addBlockListener (this);
    }
  }

  private class ContentTransferListener implements TransferListener
  {
    public void transferOk ()
    {
      finishData ();
    }

    public void failed (final Exception cause)
    {
      BaseHandler.this.failed (cause);
    }
  }

  protected void writeCache (final ByteBuffer buf) throws IOException
  {
    // TODO: another thread?
    final int currentPosition = buf.position ();
    while (buf.hasRemaining ())
      cacheChannel.write (buf);
    buf.position (currentPosition);
    tlh.getCache ().write (buf.remaining ());
  }

  public void bufferRead (final BufferHandle bufHandle)
  {
    if (con == null)
    {
      // not sure why this can happen, client has closed connection.
      return;
    }
    try
    {
      // TODO: do this in another thread?
      final ByteBuffer buffer = bufHandle.getBuffer ();
      if (cacheChannel != null)
        writeCache (buffer);
      totalRead += buffer.remaining ();
      final BlockSender bs = new BlockSender (con.getChannel (),
                                              con.getNioHandler (),
                                              tlh.getClient (),
                                              bufHandle,
                                              con.getChunking (),
                                              this);
      bs.write ();
    }
    catch (final IOException e)
    {
      failed (e);
    }
  }

  public void blockSent ()
  {
    content.addBlockListener (BaseHandler.this);
  }

  public void finishedRead ()
  {
    if (size > 0 && totalRead != size)
      setPartialContent (size);
    finishData ();
  }

  private class Finisher implements BlockSentListener
  {
    public void blockSent ()
    {
      finish (true);
    }

    public void failed (final Exception cause)
    {
      BaseHandler.this.failed (cause);
    }

    public void timeout ()
    {
      BaseHandler.this.timeout ();
    }
  }

  String getStackTrace (final Exception cause)
  {
    final StringWriter sw = new StringWriter ();
    final PrintWriter ps = new PrintWriter (sw);
    cause.printStackTrace (ps);
    return sw.toString ();
  }

  protected void deleteFile (final File f)
  {
    try
    {
      FileHelper.delete (f);
    }
    catch (final IOException e)
    {
      LOGGER.warn ("Failed to delete file", e);
    }
  }

  protected void removeCache ()
  {
    if (cacheChannel != null)
    {
      try
      {
        cacheChannel.close ();
        final ICache <HttpHeader, HttpHeader> cache = con.getProxy ().getCache ();
        final File entryName = cache.getEntryName (entry.getID (), false, null);
        deleteFile (entryName);
        entry = null;
      }
      catch (final IOException e)
      {
        LOGGER.warn ("failed to remove cache entry: ", e);
      }
      finally
      {
        cacheChannel = null;
      }
    }
  }

  public void failed (final Exception cause)
  {
    if (con != null)
    {
      String st;
      if (cause instanceof IOException)
      {
        final IOException ioe = (IOException) cause;
        final String msg = ioe.getMessage ();
        if ("Broken pipe".equals (msg))
          st = ioe.toString () + ", probably cancelled pipeline";
        else
          if ("Connection reset by peer".equals (msg))
            st = ioe.toString () + ", client aborted connection";
          else
            st = getStackTrace (cause);
      }
      else
      {
        st = getStackTrace (cause);
      }
      LOGGER.warn ("BaseHandler: error handling request: " + request.getRequestURI () + ": " + st);
      con.setStatusCode ("500");
      String ei = con.getExtraInfo ();
      ei = ei == null ? cause.toString () : (ei + ", " + cause);
      con.setExtraInfo (ei);
    }
    removeCache ();
    finish (false);
  }

  public void timeout ()
  {
    if (con != null)
      LOGGER.warn ("BaseHandler: timeout: uri: " + request.getRequestURI ());
    removeCache ();
    finish (false);
  }

  public void setup (final StringMap properties, final HttpProxy proxy)
  {
    // nothing to do.
  }
}
