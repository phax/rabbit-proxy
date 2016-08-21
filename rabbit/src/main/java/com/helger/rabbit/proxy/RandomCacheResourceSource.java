package com.helger.rabbit.proxy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import com.helger.rabbit.cache.ICache;
import com.helger.rabbit.http.Header;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.http.MultipartHeader;
import com.helger.rabbit.httpio.BlockListener;
import com.helger.rabbit.io.Range;
import com.helger.rnio.IBufferHandler;
import com.helger.rnio.INioHandler;

/**
 * A resource that gets ranges from the cache. This resource will read data from
 * disk so it may block.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class RandomCacheResourceSource extends CacheResourceSource
{
  private final String contentType;
  private final List <Range> ranges;
  private int currentRange = 0;
  private final String separator = "THIS_STRING_SEPARATES";
  private boolean startBlock = true;
  private long currentIndex = 0;
  private final long totalSize;

  private enum State
  {
    SEND_HEADER,
    SEND_DATA
  }

  private State state = State.SEND_HEADER;

  /**
   * Create a new ResourceSource that will get a list of ranges from a cached
   * resource.
   * 
   * @param cache
   *        the Cache holding the resource
   * @param rh
   *        the RequestHandler for the request
   * @param tr
   *        the NioHandler to use for network and background tasks
   * @param bufHandler
   *        the BufferHandler to use when serving the resource
   * @param ranges
   *        the wanted ranges
   * @param totalSize
   *        the total size of the wanted ranges
   * @throws IOException
   *         if the cached resource can not be read
   */
  public RandomCacheResourceSource (final ICache <HttpHeader, HttpHeader> cache,
                                    final RequestHandler rh,
                                    final INioHandler tr,
                                    final IBufferHandler bufHandler,
                                    final List <Range> ranges,
                                    final long totalSize) throws IOException
  {
    super (cache, rh.getEntry (), tr, bufHandler);
    final HttpHeader oldresp = rh.getDataHook ();
    contentType = oldresp.getHeader ("Content-Type");
    this.ranges = ranges;
    this.totalSize = totalSize;
  }

  /**
   * FileChannels can only be partially used so go with blocks.
   * 
   * @return false
   */
  @Override
  public boolean supportsTransfer ()
  {
    return false;
  }

  private boolean getNextSingleBufferBlock (final ByteBuffer buffer) throws IOException
  {
    if (currentRange > 0)
      return false;
    final Range r = ranges.get (currentRange);
    updateBufferAndPosition (buffer, r);
    return true;
  }

  /**
   * Fill the buffer with data for the current range. If the range is fully
   * handled then currentRange will be incremented.
   * 
   * @param buffer
   *        the ByteBuffer to fill with data
   * @param r
   *        the range to fill data for
   * @throws IOException
   *         if reading the resource fails
   */
  private void updateBufferAndPosition (final ByteBuffer buffer, final Range r) throws IOException
  {
    if (startBlock)
    {
      fc.position (r.getStart ());
      currentIndex = r.getStart ();
      startBlock = false;
    }
    fillBufferWithData (r, buffer);
    // if something fishy happen we abort...
    // inclusive, so we expect 1 more than end.
    if (r.size () == 0 || buffer.position () == 0 || currentIndex > r.getEnd ())
      currentRange++;
  }

  private void fillBufferWithData (final Range r, final ByteBuffer buffer) throws IOException
  {
    final int maxBytesThisRead = (int) Math.min (r.size (), buffer.capacity ());
    if (maxBytesThisRead < buffer.capacity ())
      buffer.limit (maxBytesThisRead);
    final int read = fc.read (buffer);
    currentIndex += read;
  }

  private boolean getNextMultipleBufferBlock (final ByteBuffer buffer) throws IOException
  {
    if (currentRange > ranges.size ())
      return false;
    if (currentRange == ranges.size ())
    {
      // CRLF should be optional according to BNF, but add it
      // since the rfc say it should be there.
      buffer.clear ();
      buffer.put ((Header.CRLF + "--" + separator + "--" + Header.CRLF).getBytes ());
      currentRange++;
      return true;
    }

    final int r1 = currentRange;
    final Range r = ranges.get (currentRange);
    if (state == State.SEND_HEADER)
    {
      buffer.clear ();
      writeHeader (buffer);
      startBlock = true;
      state = State.SEND_DATA;
    }
    else
      if (state == State.SEND_DATA)
      {
        updateBufferAndPosition (buffer, r);
        if (currentRange != r1)
          state = State.SEND_HEADER;
      }
    return true;
  }

  /**
   * Write the current MultipartHeader to the buffer.
   * 
   * @param buffer
   *        the ByteBuffer to write the multipart header to
   * @throws IOException
   *         if the header can not be converted to US-ASCII
   */
  private void writeHeader (final ByteBuffer buffer) throws IOException
  {
    final MultipartHeader h = new MultipartHeader (Header.CRLF + "--" + separator);
    final Range r = ranges.get (currentRange);
    if (contentType != null)
      h.setHeader ("Content-Type", contentType);
    h.setHeader ("Content-Range", "bytes " + r.getStart () + "-" + r.getEnd () + "/" + totalSize);
    buffer.put (h.toString ().getBytes ("US-ASCII"));
  }

  private boolean getNextBuffer (final ByteBuffer buffer) throws IOException
  {
    if (ranges.size () > 1)
    {
      return getNextMultipleBufferBlock (buffer);
    }
    return getNextSingleBufferBlock (buffer);
  }

  @Override
  public void addBlockListener (final BlockListener listener)
  {
    try
    {
      final ByteBuffer buffer = bufHandle.getBuffer ();
      if (getNextBuffer (buffer))
      {
        buffer.flip ();
        listener.bufferRead (bufHandle);
      }
      else
      {
        bufHandle.possiblyFlush ();
        listener.finishedRead ();
      }
    }
    catch (final IOException e)
    {
      listener.failed (e);
    }
  }
}
