package com.helger.rabbit.meta;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.helger.commons.url.SMap;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.BlockSender;
import com.helger.rabbit.httpio.BlockSentListener;
import com.helger.rabbit.httpio.ChunkEnder;
import com.helger.rabbit.io.BufferHandle;
import com.helger.rabbit.io.SimpleBufferHandle;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.proxy.HtmlPage;
import com.helger.rabbit.util.TrafficLogger;

/**
 * A base class for meta handlers. This meta handler will send a http header
 * that say that the content is chunked. Then
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public abstract class BaseMetaHandler implements MetaHandler, BlockSentListener
{
  protected HttpHeader request;
  protected SMap htab;
  protected Connection con;
  protected TrafficLogger tlProxy;
  protected TrafficLogger tlClient;
  private boolean first = true;
  protected final Logger logger = Logger.getLogger (getClass ().getName ());

  private static enum Mode
  {
    SEND_HEADER,
    SEND_DATA,
    CLEANUP
  }

  private Mode mode = Mode.SEND_HEADER;

  /** The states of the generated page */
  public static enum PageCompletion
  {
    /**
     * Used to signal that the page is not yet finished and that more content
     * will be added.
     */
    PAGE_NOT_DONE,
    /** Used to signal that the page is finished */
    PAGE_DONE;
  }

  public void handle (final HttpHeader request,
                      final SMap htab,
                      final Connection con,
                      final TrafficLogger tlProxy,
                      final TrafficLogger tlClient) throws IOException
  {
    this.request = request;
    this.htab = htab;
    this.con = con;
    this.tlProxy = tlProxy;
    this.tlClient = tlClient;
    final HttpHeader response = con.getHttpGenerator ().getHeader ();
    response.setHeader ("Transfer-Encoding", "Chunked");
    final byte [] b2 = response.toString ().getBytes ("ASCII");
    final ByteBuffer buffer = ByteBuffer.wrap (b2);
    final BufferHandle bh = new SimpleBufferHandle (buffer);
    final BlockSender bs = new BlockSender (con.getChannel (), con.getNioHandler (), tlClient, bh, false, this);
    bs.write ();
  }

  public void blockSent ()
  {
    try
    {
      switch (mode)
      {
        case CLEANUP:
          cleanup ();
          break;
        case SEND_DATA:
          endChunking ();
          break;
        case SEND_HEADER:
          buildAndSendData ();
          break;
        default:
          failed (new RuntimeException ("Odd mode: " + mode));
      }
    }
    catch (final IOException e)
    {
      failed (e);
    }
  }

  protected void cleanup ()
  {
    con.logAndTryRestart ();
  }

  protected void endChunking ()
  {
    mode = Mode.CLEANUP;
    final ChunkEnder ce = new ChunkEnder ();
    ce.sendChunkEnding (con.getChannel (), con.getNioHandler (), tlClient, this);
  }

  protected void buildAndSendData () throws IOException
  {
    final StringBuilder sb = new StringBuilder (2048);
    if (first)
    {
      sb.append (HtmlPage.getPageHeader (con, getPageHeader ()));
      first = false;
    }
    if (addPageInformation (sb) == PageCompletion.PAGE_DONE)
    {
      sb.append ("\n</body></html>");
      mode = Mode.SEND_DATA;
    }
    final byte [] b1 = sb.toString ().getBytes ("ASCII");
    final ByteBuffer data = ByteBuffer.wrap (b1);
    final BufferHandle bh = new SimpleBufferHandle (data);
    final BlockSender bs = new BlockSender (con.getChannel (), con.getNioHandler (), tlClient, bh, true, this);
    bs.write ();
  }

  /**
   * Get the page header name
   *
   * @return the html for the page header
   */
  protected abstract String getPageHeader ();

  /**
   * Add the page information
   *
   * @param sb
   *        The page being build.
   * @return the current status of the page.
   */
  protected abstract PageCompletion addPageInformation (StringBuilder sb);

  public void failed (final Exception e)
  {
    logger.log (Level.WARNING, "Exception when handling meta", e);
    con.logAndClose ();
  }

  public void timeout ()
  {
    logger.warning ("Timeout when handling meta.");
    con.logAndClose ();
  }
}
