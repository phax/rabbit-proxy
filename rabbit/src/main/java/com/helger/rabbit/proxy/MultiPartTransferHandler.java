package com.helger.rabbit.proxy;

import java.nio.ByteBuffer;

import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.BlockSender;
import com.helger.rabbit.httpio.BlockSentListener;
import com.helger.rabbit.io.BufferHandle;
import com.helger.rabbit.io.SimpleBufferHandle;

/**
 * A handler that transfers request resources with multipart data. Will send the
 * multipart upstream. Note that we can only do this if we know that the
 * upstream server is HTTP/1.1 compatible. How do we determine if upstream is
 * HTTP/1.1 compatible? If we can not then we have to add a Content-Length
 * header, That means we have to buffer the full resource.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class MultiPartTransferHandler extends ResourceHandlerBase implements BlockSentListener
{
  private MultiPartPipe mpp = null;

  public MultiPartTransferHandler (final Connection con,
                                   final BufferHandle bufHandle,
                                   final TrafficLoggerHandler tlh,
                                   final String ctHeader)
  {
    super (con, bufHandle, tlh);
    mpp = new MultiPartPipe (ctHeader);
  }

  public void modifyRequest (final HttpHeader header)
  {
    // nothing.
  }

  @Override
  void sendBuffer ()
  {
    final ByteBuffer buffer = bufHandle.getBuffer ();
    final ByteBuffer sendBuffer = buffer.slice ();
    final BufferHandle sbh = new SimpleBufferHandle (sendBuffer);
    mpp.parseBuffer (sendBuffer);
    fireResourceDataRead (sbh);
    if (wc != null)
    {
      final BlockSender bs = new BlockSender (wc.getChannel (),
                                              con.getNioHandler (),
                                              tlh.getNetwork (),
                                              sbh,
                                              false,
                                              this);
      bs.write ();
    }
    else
    {
      blockSent ();
    }
  }

  public void blockSent ()
  {
    if (!mpp.isFinished ())
      doTransfer ();
    else
      listener.clientResourceTransferred ();
  }
}
