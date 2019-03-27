package com.helger.rabbit.httpio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.rabbit.io.BufferHandle;
import com.helger.rabbit.io.ConnectionHandler;
import com.helger.rabbit.io.WebConnection;
import com.helger.rabbit.util.ITrafficLogger;
import com.helger.rnio.INioHandler;
import com.helger.rnio.IReadHandler;

/**
 * A resource source that gets the data from a WebConnection
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class WebConnectionResourceSource implements IResourceSource, IReadHandler, ChunkDataFeeder
{
  private static final Logger LOGGER = LoggerFactory.getLogger (WebConnectionResourceSource.class);

  private final ConnectionHandler con;
  private final INioHandler nioHandler;
  private final WebConnection wc;
  private final BufferHandle bufHandle;
  private final ITrafficLogger tl;
  private BlockListener listener;
  private final boolean isChunked;
  private final long dataSize;
  private long totalRead = 0;
  private int currentMark = 0;
  private ChunkHandler chunkHandler;
  private Long timeout;
  private int fullReads = 0;

  /**
   * Create a new ConnectionResourceSource that gets the data from the network.
   *
   * @param con
   *        the Connection handling the request
   * @param nioHandler
   *        the NioHandler to use for network and background tasks
   * @param wc
   *        the WebConection connected to the upstream server
   * @param bufHandle
   *        the BufferHandle to use
   * @param tl
   *        the TrafficLogger to use for network statistics
   * @param isChunked
   *        flag indicating if the upstream data is chunked or not
   * @param dataSize
   *        the size of the data, may be -1 if size is unknown
   * @param strictHttp
   *        if true strict http will be used when communcating with the upstream
   *        server
   */
  public WebConnectionResourceSource (final ConnectionHandler con,
                                      final INioHandler nioHandler,
                                      final WebConnection wc,
                                      final BufferHandle bufHandle,
                                      final ITrafficLogger tl,
                                      final boolean isChunked,
                                      final long dataSize,
                                      final boolean strictHttp)
  {
    this.con = con;
    this.nioHandler = nioHandler;
    this.wc = wc;
    this.bufHandle = bufHandle;
    this.tl = tl;
    this.isChunked = isChunked;
    if (isChunked)
      chunkHandler = new ChunkHandler (this, strictHttp);
    this.dataSize = dataSize;
  }

  public String getDescription ()
  {
    return "WebConnectionResourceSource: length: " +
           dataSize +
           ", read: " +
           totalRead +
           ", chunked: " +
           isChunked +
           ", address: " +
           wc.getAddress ();
  }

  /**
   * FileChannels can not be used, will always return false.
   *
   * @return false
   */
  public boolean supportsTransfer ()
  {
    return false;
  }

  public long length ()
  {
    return dataSize;
  }

  public long transferTo (final long position, final long count, final WritableByteChannel target) throws IOException
  {
    throw new IllegalStateException ("transferTo can not be used.");
  }

  public void addBlockListener (final BlockListener listener)
  {
    if (this.listener != null)
      throw new RuntimeException ("Trying to overwrite block listener: " + this.listener + " with: " + listener);
    this.listener = listener;
    if (isChunked)
      chunkHandler.setBlockListener (listener);

    if (dataSize > -1 && totalRead >= dataSize)
    {
      cleanupAndFinish ();
    }
    else
      if (bufHandle.isEmpty ())
      {
        register ();
      }
      else
      {
        handleBlock ();
      }
  }

  public void finishedRead ()
  {
    cleanupAndFinish ();
  }

  private void cleanupAndFinish ()
  {
    listener.finishedRead ();
  }

  public void register ()
  {
    timeout = nioHandler.getDefaultTimeout ();
    nioHandler.waitForRead (wc.getChannel (), this);
  }

  private void handleBlock ()
  {
    final BlockListener bl = listener;
    listener = null;
    if (isChunked)
    {
      chunkHandler.handleData (bufHandle);
      totalRead = chunkHandler.getTotalRead ();
    }
    else
    {
      final ByteBuffer buffer = bufHandle.getBuffer ();
      totalRead += buffer.remaining ();
      bl.bufferRead (bufHandle);
    }
    bufHandle.possiblyFlush ();
  }

  public void readMore ()
  {
    if (!bufHandle.isEmpty ())
    {
      final ByteBuffer buffer = bufHandle.getBuffer ();
      buffer.compact ();
      currentMark = buffer.position ();
      // we need to flip here so that any buffer growning
      // sees the correct buffer handling
      buffer.flip ();
    }
    register ();
  }

  public void read ()
  {
    final boolean useBig = fullReads > 4;
    final ByteBuffer buffer = useBig ? bufHandle.getLargeBuffer () : bufHandle.getBuffer ();

    buffer.position (currentMark); // keep our saved data.
    int limit = buffer.capacity ();
    if (dataSize > 0 && !isChunked)
    {
      limit = currentMark + (int) Math.min (limit - currentMark, dataSize - totalRead);
    }
    buffer.limit (limit);
    try
    {
      final int read = wc.getChannel ().read (buffer);
      currentMark = 0;
      if (read == 0)
      {
        bufHandle.possiblyFlush ();
        register ();
      }
      else
        if (read == -1)
        {
          bufHandle.possiblyFlush ();
          cleanupAndFinish ();
        }
        else
        {
          tl.read (read);
          if (read == buffer.capacity ())
            fullReads++;
          buffer.flip ();
          handleBlock ();
        }
    }
    catch (final IOException e)
    {
      listener.failed (e);
    }
  }

  public boolean useSeparateThread ()
  {
    return false;
  }

  public void closed ()
  {
    if (listener != null)
    {
      listener.failed (new IOException ("channel closed"));
      listener = null;
    }
    else
    {
      LOGGER.error ("Got close but no listener to tell!");
    }
  }

  public void timeout ()
  {
    if (listener != null)
    {
      listener.timeout ();
      listener = null;
    }
    else
    {
      LOGGER.error ("Got timeout but no listener to tell!");
    }
  }

  public Long getTimeout ()
  {
    return timeout;
  }

  public void release ()
  {
    if (!bufHandle.isEmpty () && wc.getKeepalive () && (dataSize < 0 || totalRead != dataSize))
      wc.setKeepalive (false);
    if (!wc.getKeepalive () && !bufHandle.isEmpty ())
    {
      // empty the buffer so we can reuse it.
      final ByteBuffer buffer = bufHandle.getBuffer ();
      buffer.position (buffer.limit ());
    }

    bufHandle.possiblyFlush ();
    con.releaseConnection (wc);
  }
}
