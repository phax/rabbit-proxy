package com.helger.rabbit.httpio;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.io.BufferHandle;
import com.helger.rabbit.io.SimpleBufferHandle;
import com.helger.rabbit.util.TrafficLogger;
import com.helger.rnio.INioHandler;

/**
 * A handler that writes http headers
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class HttpHeaderSender implements BlockSentListener
{
  private final boolean fullURI;
  private final HttpHeaderSentListener sender;
  private final BlockSender bs;

  /**
   * @param channel
   *        the SocketChannel to write the header to
   * @param nioHandler
   *        the NioHandler to use to wait for write ready
   * @param tl
   *        the statics gatherer to use
   * @param header
   *        the HttpHeader to send
   * @param fullURI
   *        if false then try to change header.uri into just the file
   * @param sender
   *        the listener that will be notified when the header has been sent (or
   *        sending has failed
   * @throws IOException
   *         if the header can not be converted to network data
   */
  public HttpHeaderSender (final SocketChannel channel,
                           final INioHandler nioHandler,
                           final TrafficLogger tl,
                           final HttpHeader header,
                           final boolean fullURI,
                           final HttpHeaderSentListener sender) throws IOException
  {
    this.fullURI = fullURI;
    this.sender = sender;
    final BufferHandle bh = new SimpleBufferHandle (getBuffer (header));
    bs = new BlockSender (channel, nioHandler, tl, bh, false, this);
  }

  /**
   * Send the header
   */
  public void sendHeader ()
  {
    bs.write ();
  }

  private ByteBuffer getBuffer (final HttpHeader header) throws IOException
  {
    final String uri = header.getRequestURI ();
    try
    {
      if (header.isRequest () && !header.isSecure () && !fullURI && uri.charAt (0) != '/')
      {
        final URL url = new URL (uri);
        String file = url.getFile ();
        if (file.equals (""))
          file = "/";
        header.setRequestURI (file);
      }
      final byte [] bytes = header.getBytes ();
      return ByteBuffer.wrap (bytes);
    }
    finally
    {
      header.setRequestURI (uri);
    }
  }

  public void timeout ()
  {
    sender.timeout ();
  }

  public void failed (final Exception cause)
  {
    sender.failed (cause);
  }

  public void blockSent ()
  {
    sender.httpHeaderSent ();
  }
}
