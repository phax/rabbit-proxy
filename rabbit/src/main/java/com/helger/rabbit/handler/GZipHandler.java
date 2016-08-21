package com.helger.rabbit.handler;

import java.nio.ByteBuffer;

import com.helger.commons.url.SMap;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.IResourceSource;
import com.helger.rabbit.io.BufferHandle;
import com.helger.rabbit.io.SimpleBufferHandle;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.proxy.HttpProxy;
import com.helger.rabbit.proxy.TrafficLoggerHandler;
import com.helger.rabbit.zip.GZipPackListener;
import com.helger.rabbit.zip.GZipPacker;

/**
 * This handler compresses the data passing through it.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class GZipHandler extends BaseHandler
{
  protected boolean compress = true;
  private boolean isCompressing = false;
  private boolean compressionFinished = false;
  private boolean compressedDataFinished = false;
  private GZipPacker packer = null;

  /**
   * For creating the factory.
   */
  public GZipHandler ()
  {
    // empty
  }

  /**
   * Create a new GZipHandler for the given request.
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
   */
  public GZipHandler (final Connection con,
                      final TrafficLoggerHandler tlh,
                      final HttpHeader request,
                      final HttpHeader response,
                      final IResourceSource content,
                      final boolean mayCache,
                      final boolean mayFilter,
                      final long size,
                      final boolean compress)
  {
    super (con, tlh, request, response, content, mayCache, mayFilter, size);
    this.compress = compress;
  }

  protected void setupHandler ()
  {
    if (compress)
    {
      isCompressing = willCompress ();
      if (isCompressing)
      {
        response.removeHeader ("Content-Length");
        response.setHeader ("Content-Encoding", "gzip");
        if (!con.getChunking ())
          con.setKeepalive (false);
      }
      else
      {
        mayFilter = false;
      }
    }
  }

  protected boolean willCompress ()
  {
    String ce = response.getHeader ("Content-Encoding");
    if (ce == null)
      return true;
    ce = ce.toLowerCase ();
    return !(ce.equals ("gzip") || ce.equals ("deflate"));
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
    final GZipHandler h = new GZipHandler (con,
                                           tlh,
                                           header,
                                           webHeader,
                                           content,
                                           mayCache,
                                           mayFilter,
                                           size,
                                           compress && mayFilter);
    h.setupHandler ();
    return h;
  }

  /**
   * ®return true this handler modifies the content.
   */
  @Override
  public boolean changesContentSize ()
  {
    return true;
  }

  @Override
  protected void prepare ()
  {
    if (isCompressing)
    {
      final GZipPackListener pl = new PListener ();
      packer = new GZipPacker (pl);
      if (!packer.needsInput ())
        packer.handleCurrentData ();
      else
        super.prepare ();
    }
    else
    {
      super.prepare ();
    }
  }

  private class PListener implements GZipPackListener
  {
    private byte [] buffer;

    public byte [] getBuffer ()
    {
      if (buffer == null)
        buffer = new byte [4096];
      return buffer;
    }

    public void packed (final byte [] buf, final int off, final int len)
    {
      if (len > 0)
      {
        final ByteBuffer bb = ByteBuffer.wrap (buf, off, len);
        final BufferHandle bufHandle = new SimpleBufferHandle (bb);
        GZipHandler.super.bufferRead (bufHandle);
      }
      else
      {
        blockSent ();
      }
    }

    public void dataPacked ()
    {
      // do not really care...
    }

    public void finished ()
    {
      compressedDataFinished = true;
    }

    public void failed (final Exception e)
    {
      GZipHandler.this.failed (e);
    }
  }

  @Override
  protected void finishData ()
  {
    if (isCompressing)
    {
      packer.finish ();
      compressionFinished = true;
      sendEndBuffers ();
    }
    else
    {
      super.finishData ();
    }
  }

  private void sendEndBuffers ()
  {
    if (packer.finished ())
    {
      super.finishData ();
    }
    else
    {
      packer.handleCurrentData ();
    }
  }

  /**
   * Check if this handler supports direct transfers.
   *
   * @return this handler always return false.
   */
  @Override
  protected boolean mayTransfer ()
  {
    return false;
  }

  @Override
  public void blockSent ()
  {
    if (packer == null)
      super.blockSent ();
    else
      if (compressedDataFinished)
        super.finishData ();
      else
        if (compressionFinished)
          sendEndBuffers ();
        else
          if (packer.needsInput ())
            waitForData ();
          else
            packer.handleCurrentData ();
  }

  protected void waitForData ()
  {
    content.addBlockListener (this);
  }

  /**
   * Write the current block of data to the gzipper. If you override this method
   * you probably want to override the modifyBuffer(ByteBuffer) as well.
   *
   * @param arr
   *        the data to write to the gzip stream.
   */
  protected void writeDataToGZipper (final byte [] arr)
  {
    packer.setInput (arr, 0, arr.length);
    if (packer.needsInput ())
      waitForData ();
    else
      packer.handleCurrentData ();
  }

  /**
   * This method is used when we are not compressing data. This method will just
   * call "super.bufferRead (buf);"
   *
   * @param bufHandle
   *        the handle to the buffer that just was read.
   */
  protected void modifyBuffer (final BufferHandle bufHandle)
  {
    super.bufferRead (bufHandle);
  }

  protected void send (final BufferHandle bufHandle)
  {
    if (isCompressing)
    {
      final ByteBuffer buf = bufHandle.getBuffer ();
      final byte [] arr = buf.array ();
      final int pos = buf.position ();
      final int len = buf.remaining ();
      packer.setInput (arr, pos, len);
      if (!packer.needsInput ())
        packer.handleCurrentData ();
      else
        blockSent ();
    }
    else
    {
      super.bufferRead (bufHandle);
    }
  }

  @Override
  public void bufferRead (final BufferHandle bufHandle)
  {
    if (con == null)
    {
      // not sure why this can happen, client has closed connection?
      return;
    }
    if (isCompressing)
    {
      // we normally have direct buffers and we can not use
      // array() on them. Create a new byte[] and copy data into it.
      byte [] arr;
      final ByteBuffer buf = bufHandle.getBuffer ();
      totalRead += buf.remaining ();
      if (buf.isDirect ())
      {
        arr = new byte [buf.remaining ()];
        buf.get (arr);
      }
      else
      {
        arr = buf.array ();
        buf.position (buf.limit ());
      }
      bufHandle.possiblyFlush ();
      writeDataToGZipper (arr);
    }
    else
    {
      modifyBuffer (bufHandle);
    }
  }

  @Override
  public void setup (final SMap prop, final HttpProxy proxy)
  {
    super.setup (prop, proxy);
    if (prop != null)
    {
      final String comp = prop.getOrDefault ("compress", "true");
      compress = !comp.equalsIgnoreCase ("false");
    }
  }
}
