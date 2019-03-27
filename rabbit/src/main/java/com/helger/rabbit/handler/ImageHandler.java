package com.helger.rabbit.handler;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.collection.attr.StringMap;
import com.helger.commons.io.stream.StreamHelper;
import com.helger.rabbit.handler.convert.ExternalProcessConverter;
import com.helger.rabbit.handler.convert.ImageConverter;
import com.helger.rabbit.handler.convert.JavaImageConverter;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.BlockListener;
import com.helger.rabbit.httpio.FileResourceSource;
import com.helger.rabbit.httpio.IResourceSource;
import com.helger.rabbit.io.BufferHandle;
import com.helger.rabbit.io.FileHelper;
import com.helger.rabbit.io.SimpleBufferHandle;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.proxy.HttpProxy;
import com.helger.rabbit.proxy.TrafficLoggerHandler;
import com.helger.rabbit.zip.GZipUnpackListener;
import com.helger.rabbit.zip.GZipUnpacker;
import com.helger.rnio.ITaskIdentifier;
import com.helger.rnio.impl.DefaultTaskIdentifier;

/**
 * This handler first downloads the image runs convert on it and then serves the
 * smaller image.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ImageHandler extends BaseHandler
{
  private static final Logger LOGGER = LoggerFactory.getLogger (ImageHandler.class);

  private StringMap config = new StringMap ();
  private boolean doConvert = true;
  private int minSizeToConvert = 2000;

  private boolean converted = false;
  protected File convertedFile = null;
  private ImageConverter imageConverter;

  /**
   * For creating the factory.
   */
  public ImageHandler ()
  {
    // empty
  }

  /**
   * Create a new ImageHandler for the given request.
   *
   * @param con
   *        the Connection handling the request.
   * @param tlh
   *        the LOGGER for the data traffic
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
   * @param config
   *        the configuration of this handler
   * @param doConvert
   *        image comprssion will only be attempted if true
   * @param minSizeToConvert
   *        images less than this many bytes are not compressed
   * @param imageConverter
   *        the actual converter to use
   */
  public ImageHandler (final Connection con,
                       final TrafficLoggerHandler tlh,
                       final HttpHeader request,
                       final HttpHeader response,
                       final IResourceSource content,
                       final boolean mayCache,
                       final boolean mayFilter,
                       final long size,
                       final StringMap config,
                       final boolean doConvert,
                       final int minSizeToConvert,
                       final ImageConverter imageConverter)
  {
    super (con, tlh, request, response, content, mayCache, mayFilter, size);
    if (size == -1)
      con.setKeepalive (false);
    con.setChunking (false);
    this.config = config;
    this.doConvert = doConvert;
    this.minSizeToConvert = minSizeToConvert;
    this.imageConverter = imageConverter;
  }

  @Override
  public IHandler getNewInstance (final Connection con,
                                  final TrafficLoggerHandler tlh,
                                  final HttpHeader header,
                                  final HttpHeader webHeader,
                                  final IResourceSource content,
                                  final boolean mayCache,
                                  final boolean mayFilter,
                                  final long size)
  {
    return new ImageHandler (con,
                             tlh,
                             header,
                             webHeader,
                             content,
                             mayCache,
                             mayFilter,
                             size,
                             getConfig (),
                             getDoConvert (),
                             getMinSizeToConvert (),
                             imageConverter);
  }

  /**
   * ®return true this handler modifies the content.
   */
  @Override
  public boolean changesContentSize ()
  {
    return true;
  }

  /**
   * Images needs to be cacheable to be compressed.
   *
   * @return true
   */
  @Override
  protected boolean mayCacheFromSize ()
  {
    return true;
  }

  /**
   * Check if this handler may force the cached resource to be less than the
   * cache max size.
   *
   * @return false
   */
  @Override
  protected boolean mayRestrictCacheSize ()
  {
    return false;
  }

  /**
   * Try to convert the image before letting the superclass handle it.
   */
  @Override
  public void handle ()
  {
    tryconvert ();
  }

  @Override
  protected void addCache ()
  {
    if (!converted)
      super.addCache ();
    // if we get here then we have converted the image
    // and do not want a cache...
  }

  private void removeConvertedFile ()
  {
    if (convertedFile != null && convertedFile.exists ())
    {
      deleteFile (convertedFile);
      convertedFile = null;
    }
  }

  /**
   * clear up the mess we made (remove intermediate files etc).
   */
  @Override
  protected void finish (final boolean good)
  {
    try
    {
      removeConvertedFile ();
    }
    finally
    {
      super.finish (good);
    }
  }

  /**
   * Remove the cachestream and the cache entry.
   */
  @Override
  protected void removeCache ()
  {
    super.removeCache ();
    removeConvertedFile ();
  }

  /**
   * Try to convert the image. This is done like this: <xmp> super.addCache ();
   * readImage(); convertImage(); cacheChannel = null; </xmp> We have to use the
   * cachefile to convert the image, and if we convert it we dont want to write
   * the file to the cache later on.
   */
  protected void tryconvert ()
  {
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug (request.getRequestURI () +
                    ": doConvert: " +
                    doConvert +
                    ", mayFilter: " +
                    mayFilter +
                    ", mayCache: " +
                    mayCache +
                    ", size: " +
                    size +
                    ", minSizeToConvert: " +
                    minSizeToConvert);
    // TODO: if the image size is unknown (chunked) we will have -1 > 2000
    // TODO: perhaps we should add something to handle that.
    if (doConvert && mayFilter && mayCache && size > minSizeToConvert)
    {
      super.addCache ();
      // check if cache setup worked.
      if (cacheChannel == null)
        super.handle ();
      else
        readImage ();
    }
    else
    {
      super.handle ();
    }
  }

  /**
   * Read in the image
   */
  protected void readImage ()
  {
    final String enc = response.getHeader ("Content-Encoding");
    final boolean unzip = "gzip".equalsIgnoreCase (enc);
    if (unzip)
      response.removeHeader ("Content-Encoding");
    content.addBlockListener (new ImageReader (unzip));
  }

  private ITaskIdentifier getTaskIdentifier (final Object o, final String method)
  {
    final String gid = o.getClass ().getSimpleName () + "." + method;
    return new DefaultTaskIdentifier (gid, request.getRequestURI ());
  }

  private class ImageReader implements BlockListener, GZipUnpackListener
  {
    private final boolean unzip;
    private GZipUnpacker gzu;
    private byte [] buffer;

    public ImageReader (final boolean unzip)
    {
      this.unzip = unzip;
      if (unzip)
      {
        gzu = new GZipUnpacker (this, false);
        buffer = new byte [4096];
      }
    }

    public void bufferRead (final BufferHandle bufHandle)
    {
      final ITaskIdentifier ti = getTaskIdentifier (this, "bufferRead");
      con.getNioHandler ().runThreadTask ( () -> {
        if (unzip)
        {
          unpackData (bufHandle);
        }
        else
        {
          writeImageData (bufHandle);
        }
      }, ti);
    }

    public byte [] getBuffer ()
    {
      return buffer;
    }

    private void unpackData (final BufferHandle bufHandle)
    {
      final ByteBuffer buf = bufHandle.getBuffer ();
      totalRead += buf.remaining ();
      byte [] arr;
      int off = 0;
      final int len = buf.remaining ();
      if (buf.hasArray ())
      {
        arr = buf.array ();
        off = buf.position ();
        buf.position (buf.limit ());
      }
      else
      {
        arr = new byte [len];
        buf.get (arr);
      }
      gzu.setInput (arr, off, len);
      bufHandle.possiblyFlush ();
    }

    public void unpacked (final byte [] arr, final int off, final int len)
    {
      final ByteBuffer buf = ByteBuffer.wrap (arr, off, len);
      final BufferHandle bh = new SimpleBufferHandle (buf);
      writeImageData (bh);
    }

    public void finished ()
    {
      finishedRead ();
    }

    private void writeImageData (final BufferHandle bufHandle)
    {
      try
      {
        final ByteBuffer buf = bufHandle.getBuffer ();
        writeCache (buf);
        totalRead += buf.remaining ();
        buf.position (buf.limit ());
        bufHandle.possiblyFlush ();
        if (gzu == null)
        {
          content.addBlockListener (this);
        }
        else
        {
          if (gzu.needsInput ())
            content.addBlockListener (this);
          else
            gzu.handleCurrentData ();
        }
      }
      catch (final IOException e)
      {
        failed (e);
      }
    }

    public void finishedRead ()
    {
      try
      {
        if (size > 0 && totalRead != size)
          setPartialContent (size);
        cacheChannel.close ();
        cacheChannel = null;
        convertImage ();
      }
      catch (final IOException e)
      {
        failed (e);
      }
    }

    public void failed (final Exception cause)
    {
      ImageHandler.this.failed (cause);
    }

    public void timeout ()
    {
      ImageHandler.this.failed (new IOException ("Timeout"));
    }
  }

  /**
   * Convert the image into a small low quality image (normally a jpeg).
   */
  protected void convertImage ()
  {
    final ITaskIdentifier ti = getTaskIdentifier (this, "convertImage");
    con.getNioHandler ().runThreadTask ( () -> {
      try
      {
        convertAndGetBest ();
        converted = true;
        ImageHandler.super.handle ();
      }
      catch (final IOException e)
      {
        failed (e);
      }
    }, ti);
  }

  private void convertAndGetBest () throws IOException
  {
    final HttpProxy proxy = con.getProxy ();
    final File imageFile = proxy.getCache ().getEntryName (entry.getID (), false, null);
    final String imageName = imageFile.getName ();

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug (request.getRequestURI () + ": Trying to convert image: " + imageName);
    final ImageConversionResult icr = internalConvertImage (imageFile, imageName);
    try
    {
      convertedFile = selectImage (imageFile, icr);
    }
    finally
    {
      if (icr.convertedFile != null && icr.convertedFile.exists ())
        deleteFile (icr.convertedFile);
      convertedFile = null;
      if (icr.typeFile != null && icr.typeFile.exists ())
        deleteFile (icr.typeFile);
    }

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug (request.getRequestURI () + ": OrigSize: " + icr.origSize + ", convertedSize: " + icr.convertedSize);
    size = icr.convertedSize > 0 ? icr.convertedSize : icr.origSize;
    response.setHeader ("Content-length", "" + size);
    final double ratio = (double) icr.convertedSize / icr.origSize;
    final String sRatio = String.format ("%.3f", Double.valueOf (ratio));
    con.setExtraInfo ("imageratio:" + icr.convertedSize + "/" + icr.origSize + "=" + sRatio);
    content.release ();
    content = new FileResourceSource (imageFile, con.getNioHandler (), con.getProxy ().getBufferHandler ());
  }

  private static class ImageConversionResult
  {
    public final long origSize;
    public final long convertedSize;
    public final File convertedFile;
    public final File typeFile;

    public ImageConversionResult (final long origSize, final File convertedFile, final File typeFile)
    {
      this.origSize = origSize;
      this.convertedFile = convertedFile;
      this.typeFile = typeFile;
      if (convertedFile.exists ())
        this.convertedSize = convertedFile.length ();
      else
        convertedSize = 0;
    }

    @Override
    public String toString ()
    {
      return getClass ().getSimpleName () +
             "{origSize: " +
             origSize +
             ", convertedSize: " +
             convertedSize +
             ", convertedFile: " +
             convertedFile +
             ", typeFile: " +
             typeFile +
             "}";
    }
  }

  /**
   * Perform the actual image conversion.
   *
   * @param input
   *        the File holding the source image
   * @param entryName
   *        the filename of the cache entry to use.
   * @return the conversion result
   * @throws IOException
   *         if image compression fails
   */
  private ImageConversionResult internalConvertImage (final File input, final String entryName) throws IOException
  {
    final long origSize = size;
    final File p = input.getParentFile ();
    final File convertedFile = new File (p, entryName + ".c");
    final File typeFile = new File (p, entryName + ".type");
    imageConverter.convertImage (input, convertedFile, request.getRequestURI ());
    return new ImageConversionResult (origSize, convertedFile, typeFile);
  }

  /**
   * Make sure that the cache entry is the smallest image.
   *
   * @param entry
   *        the file holding the source image
   * @param icr
   *        the image compression result
   * @return the File to use
   * @throws IOException
   *         if image selection fails
   */
  private File selectImage (final File entry, final ImageConversionResult icr) throws IOException
  {
    File convertedFile = icr.convertedFile;
    if (icr.convertedSize > 0 && icr.origSize > icr.convertedSize)
    {
      final String ctype = checkFileType (icr.typeFile);
      response.setHeader ("Content-Type", ctype);
      /**
       * We need to remove the existing file first for windows system, they will
       * not overwrite files in a move. Spotted by: Michael Mlivoncic
       */
      if (entry.exists ())
      {
        if (LOGGER.isDebugEnabled ())
          LOGGER.debug (request.getRequestURI () + ": deleting old entry: " + entry);
        FileHelper.delete (entry);
      }
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug (request.getRequestURI () +
                      ": Trying to move converted file: " +
                      icr.convertedFile +
                      " => " +
                      entry);
      if (icr.convertedFile.renameTo (entry))
        convertedFile = null;
      else
        LOGGER.warn ("rename failed: " + convertedFile.getName () + " => " + entry);
    }
    return convertedFile;
  }

  protected String checkFileType (final File typeFile) throws IOException
  {
    String ctype = "image/jpeg";
    if (typeFile != null && typeFile.exists () && typeFile.length () > 0)
    {
      BufferedReader br = null;
      try
      {
        br = new BufferedReader (new FileReader (typeFile));
        ctype = br.readLine ();
      }
      finally
      {
        final Closeable c = br;
        StreamHelper.close (c);
      }
    }
    return ctype;
  }

  /**
   * Śet the convert flag
   *
   * @param doConvert
   *        if true then image conversion will be tried
   */
  public void setDoConvert (final boolean doConvert)
  {
    this.doConvert = doConvert;
  }

  /**
   * Get the convert flag
   *
   * @return true if image conversion should be used
   */
  public boolean getDoConvert ()
  {
    return doConvert;
  }

  /**
   * Get the current configuration of this handler
   *
   * @return the current configuration
   */
  public StringMap getConfig ()
  {
    return config;
  }

  /**
   * Only try to convert images larger than this size
   *
   * @return the minimum size of images to run conversion on
   */
  public int getMinSizeToConvert ()
  {
    return minSizeToConvert;
  }

  @Override
  public void setup (final StringMap prop, final HttpProxy proxy)
  {
    super.setup (prop, proxy);
    if (prop == null)
      return;
    config = prop;
    setDoConvert (true);
    minSizeToConvert = Integer.parseInt (prop.getOrDefault ("min_size", "2000"));
    final String converterType = prop.getOrDefault ("converter_type", "external");
    if (converterType.equalsIgnoreCase ("external"))
    {
      imageConverter = new ExternalProcessConverter (prop);
      if (!imageConverter.canConvert ())
      {
        LOGGER.warn ("imageConverter: " + imageConverter + " can not convert images, using java.");
        imageConverter = null;
      }
    }
    if (imageConverter == null)
      imageConverter = new JavaImageConverter (prop);
  }
}
