package com.helger.rabbit.handler;

import java.nio.ByteBuffer;

import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.BlockSender;
import com.helger.rabbit.httpio.IResourceSource;
import com.helger.rabbit.io.BufferHandle;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.proxy.MultiPartPipe;
import com.helger.rabbit.proxy.TrafficLoggerHandler;

/**
 * This class handles multipart responses, this handler does not filter the
 * resource.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class MultiPartHandler extends BaseHandler
{
  private MultiPartPipe mpp = null;

  /**
   * Create a new MultiPartHandler factory.
   */
  public MultiPartHandler ()
  {
    // empty
  }

  /**
   * Create a new BaseHansler for the given request.
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
   */
  public MultiPartHandler (final Connection con,
                           final TrafficLoggerHandler tlh,
                           final HttpHeader request,
                           final HttpHeader response,
                           final IResourceSource content)
  {
    super (con, tlh, request, response, content, false, false, -1);
    con.setChunking (false);

    // Content-Type: multipart/byteranges; boundary=B-mmrokjxyjnwsfcefrvcg\r\n
    final String ct = response.getHeader ("Content-Type");
    mpp = new MultiPartPipe (ct);
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
    return new MultiPartHandler (con, tlh, header, webHeader, content);
  }

  /**
   * We may remove trailers, so we may modify the content. ®return true this
   * handler modifies the content.
   */
  @Override
  public boolean changesContentSize ()
  {
    return true;
  }

  @Override
  protected void send ()
  {
    content.addBlockListener (this);
  }

  /*
   * A Typical case: The header is already read: <xmp> HTTP/1.1 206 Partial
   * Content\r\n Connection: keep-alive\r\n Date: Sun, 05 Feb 2006 15:02:20
   * GMT\r\n Content-Type: multipart/byteranges;
   * boundary=B-cbwbjaxizibtumtuxtti\r\n \r\n </xmp> Then comes the data: <xmp>
   * \r\n --B-cbwbjaxizibtumtuxtti\r\n Content-Range: bytes 0-5/105\r\n \r\n
   * body-y\r\n --B-cbwbjaxizibtumtuxtti\r\n Content-Range: bytes 7-10/105\r\n
   * \r\n jqka\r\n --B-cbwbjaxizibtumtuxtti--\r\n </xmp>
   */
  /*
   * For now we only try to read lines and if we find the ending line we stop.
   * This is not a fully correct handling, but it seems to work well enough.
   */
  @Override
  public void bufferRead (final BufferHandle bufHandle)
  {
    final ByteBuffer buf = bufHandle.getBuffer ();
    mpp.parseBuffer (buf);
    final BlockSender bs = new BlockSender (con.getChannel (),
                                            con.getNioHandler (),
                                            tlh.getClient (),
                                            bufHandle,
                                            con.getChunking (),
                                            this);
    bs.write ();
  }
}
