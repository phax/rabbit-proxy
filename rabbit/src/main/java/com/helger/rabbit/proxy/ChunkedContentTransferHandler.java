package com.helger.rabbit.proxy;

import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.BlockListener;
import com.helger.rabbit.httpio.BlockSender;
import com.helger.rabbit.httpio.BlockSentListener;
import com.helger.rabbit.httpio.ChunkDataFeeder;
import com.helger.rabbit.httpio.ChunkEnder;
import com.helger.rabbit.httpio.ChunkHandler;
import com.helger.rabbit.io.BufferHandle;

/**
 * A handler that transfers chunked request resources. Will chunk data to the
 * real server or fail. Note that we can only do this if we know that the
 * upstream server is HTTP/1.1 compatible. How do we determine if upstream is
 * HTTP/1.1 compatible? If we can not then we have to add a Content-Length
 * header and not chunk, That means we have to buffer the full resource.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class ChunkedContentTransferHandler extends ResourceHandlerBase
                                    implements ChunkDataFeeder, BlockListener, BlockSentListener
{

  private boolean sentEndChunk = false;
  private final ChunkHandler chunkHandler;

  public ChunkedContentTransferHandler (final Connection con,
                                        final BufferHandle bufHandle,
                                        final TrafficLoggerHandler tlh)
  {
    super (con, bufHandle, tlh);
    chunkHandler = new ChunkHandler (this, con.getProxy ().getStrictHttp ());
    chunkHandler.setBlockListener (this);
  }

  public void modifyRequest (final HttpHeader header)
  {
    header.setHeader ("Transfer-Encoding", "chunked");
  }

  @Override
  void sendBuffer ()
  {
    chunkHandler.handleData (bufHandle);
  }

  public void bufferRead (final BufferHandle bufHandle)
  {
    fireResourceDataRead (bufHandle);
    if (wc != null)
    {
      final BlockSender bs = new BlockSender (wc.getChannel (),
                                              con.getNioHandler (),
                                              tlh.getNetwork (),
                                              bufHandle,
                                              true,
                                              this);
      bs.write ();
    }
    else
    {
      blockSent ();
    }
  }

  public void finishedRead ()
  {
    sentEndChunk = true;
    if (wc != null)
    {
      final ChunkEnder ce = new ChunkEnder ();
      ce.sendChunkEnding (wc.getChannel (), con.getNioHandler (), tlh.getNetwork (), this);
    }
    else
    {
      blockSent ();
    }
  }

  public void register ()
  {
    waitForRead ();
  }

  public void readMore ()
  {
    if (!bufHandle.isEmpty ())
      bufHandle.getBuffer ().compact ();
    register ();
  }

  public void blockSent ()
  {
    if (sentEndChunk)
      listener.clientResourceTransferred ();
    else
      doTransfer ();
  }
}
