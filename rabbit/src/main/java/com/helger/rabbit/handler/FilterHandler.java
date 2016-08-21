package com.helger.rabbit.handler;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import com.helger.commons.url.SMap;
import com.helger.rabbit.filter.HtmlFilter;
import com.helger.rabbit.filter.HtmlFilterFactory;
import com.helger.rabbit.html.HtmlBlock;
import com.helger.rabbit.html.HtmlParseException;
import com.helger.rabbit.html.HtmlParser;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.ResourceSource;
import com.helger.rabbit.io.BufferHandle;
import com.helger.rabbit.io.SimpleBufferHandle;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.proxy.HttpProxy;
import com.helger.rabbit.proxy.TrafficLoggerHandler;
import com.helger.rabbit.zip.GZipUnpackListener;
import com.helger.rabbit.zip.GZipUnpacker;

/**
 * This handler filters out unwanted html features.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class FilterHandler extends GZipHandler
{
  private List <HtmlFilterFactory> filterClasses = new ArrayList<> ();
  private boolean repack = false;
  private String defaultCharSet = null;
  private String overrideCharSet = null;

  private List <HtmlFilter> filters;
  private HtmlParser parser;
  private byte [] restBlock = null;
  private boolean sendingRest = false;
  private Iterator <ByteBuffer> sendBlocks = null;

  private GZipUnpacker gzu = null;
  private GZListener gzListener = null;

  /**
   * Create a new FilterHandler that is uninitialized. Normally this should only
   * be used for the factory creation.
   */
  public FilterHandler ()
  {
    // empty
  }

  /**
   * Create a new FilterHandler for the given request.
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
   * @param compress
   *        if we want this handler to compress or not.
   * @param repack
   *        if true unpack, filter and possibly repack compressed resources.
   * @param filterClasses
   *        the filters to use
   */
  public FilterHandler (final Connection con,
                        final TrafficLoggerHandler tlh,
                        final HttpHeader request,
                        final HttpHeader response,
                        final ResourceSource content,
                        final boolean mayCache,
                        final boolean mayFilter,
                        final long size,
                        final boolean compress,
                        final boolean repack,
                        final List <HtmlFilterFactory> filterClasses)
  {
    super (con, tlh, request, response, content, mayCache, mayFilter, size, compress);
    this.repack = repack;
    this.filterClasses = filterClasses;
  }

  @Override
  protected void setupHandler ()
  {
    final String ce = response.getHeader ("Content-Encoding");
    if (repack && ce != null)
      setupRepacking (ce);

    super.setupHandler ();
    if (mayFilter)
    {
      response.removeHeader ("Content-Length");

      String cs;
      if (overrideCharSet != null)
      {
        cs = overrideCharSet;
      }
      else
      {
        cs = tryToGetCharset ();
      }
      // There are lots of other charsets, and it could be specified by a
      // HTML Meta tag.
      // And it might be specified incorrectly for the actual page.
      // http://www.w3.org/International/O-HTTP-charset

      // default fron conf file
      // then look for HTTP charset
      // then look for HTML Meta charset, maybe re-decode
      // <META content="text/html; charset=gb2312" http-equiv=Content-Type>
      // <meta http-equiv="content-type" content="text/html;charset=Shift_JIS"
      // />

      Charset charSet;
      try
      {
        charSet = Charset.forName (cs);
      }
      catch (final UnsupportedCharsetException e)
      {
        getLogger ().warning ("Bad CharSet: " + cs);
        charSet = Charset.forName ("ISO-8859-1");
      }
      parser = new HtmlParser (charSet);
      filters = initFilters ();
    }
  }

  private void setupRepacking (String ce)
  {
    ce = ce.toLowerCase ();
    if (ce.equals ("gzip"))
    {
      gzListener = new GZListener ();
      gzu = new GZipUnpacker (gzListener, false);
    }
    else
      if (ce.equals ("deflate"))
      {
        gzListener = new GZListener ();
        gzu = new GZipUnpacker (gzListener, true);
      }
      else
      {
        getLogger ().warning ("Do not know how to handle encoding: " + ce);
      }
    if (gzu != null && !compress)
    {
      response.removeHeader ("Content-Encoding");
    }
  }

  private String tryToGetCharset ()
  {
    String cs = defaultCharSet;
    // Content-Type: text/html; charset=iso-8859-1
    final String ct = response.getHeader ("Content-Type");
    if (ct != null)
    {
      final String look = "charset=";
      int beginIndex = ct.indexOf (look);
      if (beginIndex > 0)
      {
        beginIndex += look.length ();
        String charSet = ct.substring (beginIndex).trim ();
        charSet = charSet.replace ("_", "").replace ("-", "");
        if (charSet.endsWith (";"))
          charSet = charSet.substring (0, charSet.length () - 1);
        if (charSet.equalsIgnoreCase ("iso88591"))
          cs = "ISO8859_1";
        if (charSet.equalsIgnoreCase ("utf8"))
          cs = "utf-8";
        else
          cs = charSet;
      }
    }
    return cs;
  }

  @Override
  protected boolean willCompress ()
  {
    return gzu != null || super.willCompress ();
  }

  private class GZListener implements GZipUnpackListener
  {
    private boolean gotData = false;
    private final byte [] buffer = new byte [4096];

    public void unpacked (final byte [] buf, final int off, final int len)
    {
      gotData = true;
      handleArray (buf, off, len);
    }

    public void clearDataFlag ()
    {
      gotData = false;
    }

    public boolean gotData ()
    {
      return gotData;
    }

    public void finished ()
    {
      gzu = null;
      gzListener = null;
      finishData ();
    }

    public byte [] getBuffer ()
    {
      return buffer;
    }

    public void failed (final Exception e)
    {
      FilterHandler.this.failed (e);
    }
  }

  @Override
  public Handler getNewInstance (final Connection con,
                                 final TrafficLoggerHandler tlh,
                                 final HttpHeader header,
                                 final HttpHeader webHeader,
                                 final ResourceSource content,
                                 final boolean mayCache,
                                 final boolean mayFilter,
                                 final long size)
  {
    final FilterHandler h = new FilterHandler (con,
                                               tlh,
                                               header,
                                               webHeader,
                                               content,
                                               mayCache,
                                               mayFilter,
                                               size,
                                               compress,
                                               repack,
                                               filterClasses);
    h.defaultCharSet = defaultCharSet;
    h.overrideCharSet = overrideCharSet;
    h.setupHandler ();
    return h;
  }

  @Override
  protected void writeDataToGZipper (final byte [] arr)
  {
    forwardArrayToHandler (arr, 0, arr.length);
  }

  @Override
  protected void modifyBuffer (final BufferHandle bufHandle)
  {
    if (!mayFilter)
    {
      super.modifyBuffer (bufHandle);
      return;
    }
    final ByteBuffer buf = bufHandle.getBuffer ();
    byte [] arr;
    int off = 0;
    final int len = buf.remaining ();
    if (buf.hasArray ())
    {
      arr = buf.array ();
      off = buf.position ();
    }
    else
    {
      arr = new byte [len];
      buf.get (arr);
    }
    bufHandle.possiblyFlush ();
    forwardArrayToHandler (arr, off, len);
  }

  private void forwardArrayToHandler (final byte [] arr, final int off, final int len)
  {
    if (gzu != null)
    {
      gzListener.clearDataFlag ();
      gzu.setInput (arr, off, len);
      // gzu may be null if we get into finished mode
      if (gzu != null && gzu.needsInput () && !gzListener.gotData ())
        waitForData ();
    }
    else
    {
      handleArray (arr, off, len);
    }
  }

  private void handleArray (byte [] arr, int off, int len)
  {
    if (restBlock != null)
    {
      final int rs = restBlock.length;
      final int newLen = len + rs;
      final byte [] buf = new byte [newLen];
      System.arraycopy (restBlock, 0, buf, 0, rs);
      System.arraycopy (arr, off, buf, rs, len);
      arr = buf;
      off = 0;
      len = newLen;
      restBlock = null;
    }
    parser.setText (arr, off, len);
    HtmlBlock currentBlock;
    try
    {
      currentBlock = parser.parse ();
      for (final HtmlFilter hf : filters)
      {
        hf.filterHtml (currentBlock);
        if (!hf.isCacheable ())
        {
          mayCache = false;
          removeCache ();
        }
      }

      final List <ByteBuffer> ls = currentBlock.getBlocks ();
      if (currentBlock.hasRests ())
      {
        // since the unpacking buffer is re used we need to store the
        // rest in a separate buffer.
        restBlock = currentBlock.getRestBlock ();
      }
      sendBlocks = ls.iterator ();
    }
    catch (final HtmlParseException e)
    {
      getLogger ().info ("Bad HTML: " + e.toString ());
      // out.write (arr);
      final ByteBuffer buf = ByteBuffer.wrap (arr, off, len);
      sendBlocks = Arrays.asList (buf).iterator ();
    }
    if (sendBlocks.hasNext ())
    {
      sendBlockBuffers ();
    }
    else
    {
      // no more blocks so wait for more data, either from
      // gzip or the net
      blockSent ();
    }
  }

  @Override
  public void blockSent ()
  {
    if (sendingRest)
    {
      super.finishData ();
    }
    else
      if (sendBlocks != null && sendBlocks.hasNext ())
      {
        sendBlockBuffers ();
      }
      else
        if (gzu != null && !gzu.needsInput ())
        {
          gzu.handleCurrentData ();
        }
        else
        {
          super.blockSent ();
        }
  }

  private void sendBlockBuffers ()
  {
    final ByteBuffer buf = sendBlocks.next ();
    final SimpleBufferHandle bh = new SimpleBufferHandle (buf);
    send (bh);
  }

  @Override
  protected void finishData ()
  {
    if (restBlock != null && restBlock.length > 0)
    {
      final ByteBuffer buf = ByteBuffer.wrap (restBlock);
      final SimpleBufferHandle bh = new SimpleBufferHandle (buf);
      restBlock = null;
      sendingRest = true;
      send (bh);
    }
    else
    {
      super.finishData ();
    }
  }

  /**
   * Initialize the filter we are using.
   *
   * @return a List of HtmlFilters.
   */
  private List <HtmlFilter> initFilters ()
  {
    final int fsize = filterClasses.size ();
    final List <HtmlFilter> fl = new ArrayList<> (fsize);

    for (int i = 0; i < fsize; i++)
    {
      final HtmlFilterFactory hff = filterClasses.get (i);
      fl.add (hff.newFilter (con, request, response));
    }
    return fl;
  }

  /**
   * Setup this class.
   *
   * @param prop
   *        the properties of this class.
   */
  @Override
  public void setup (final SMap prop, final HttpProxy proxy)
  {
    super.setup (prop, proxy);
    defaultCharSet = prop.getOrDefault ("defaultCharSet", "ISO-8859-1");
    overrideCharSet = prop.get ("overrideCharSet");
    final String rp = prop.getOrDefault ("repack", "false");
    repack = Boolean.parseBoolean (rp);
    final String fs = prop.getOrDefault ("filters", "");
    if ("".equals (fs))
      return;
    final String [] names = fs.split (",");
    for (final String classname : names)
    {
      try
      {
        final Class <? extends HtmlFilterFactory> cls = proxy.load3rdPartyClass (classname, HtmlFilterFactory.class);
        filterClasses.add (cls.newInstance ());
      }
      catch (final ClassNotFoundException e)
      {
        getLogger ().warning ("Could not find filter: '" + classname + "'");
      }
      catch (final InstantiationException e)
      {
        getLogger ().log (Level.WARNING, "Could not instanciate class: '" + classname + "'", e);
      }
      catch (final IllegalAccessException e)
      {
        getLogger ().log (Level.WARNING, "Could not get constructor for: '" + classname + "'", e);
      }
    }
  }
}
