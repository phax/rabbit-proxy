package com.helger.rabbit.meta;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.helger.commons.io.stream.StreamHelper;
import com.helger.commons.url.SMap;
import com.helger.rabbit.http.HttpDateParser;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.HttpHeaderSender;
import com.helger.rabbit.httpio.HttpHeaderSentListener;
import com.helger.rabbit.httpio.TransferHandler;
import com.helger.rabbit.httpio.TransferListener;
import com.helger.rabbit.httpio.Transferable;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.util.MimeTypeMapper;
import com.helger.rabbit.util.ITrafficLogger;

/**
 * A file resource handler.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class FileSender implements MetaHandler, HttpHeaderSentListener
{
  private Connection con;
  private ITrafficLogger tlClient;
  private ITrafficLogger tlProxy;
  private FileInputStream fis;
  private FileChannel fc;
  private long length;
  private final Logger logger = Logger.getLogger (getClass ().getName ());

  public void handle (final HttpHeader request,
                      final SMap htab,
                      final Connection con,
                      final ITrafficLogger tlProxy,
                      final ITrafficLogger tlClient) throws IOException
  {
    this.con = con;
    this.tlProxy = tlProxy;
    this.tlClient = tlClient;

    final String file = htab.get ("argstring");
    if (file == null)
      throw (new IllegalArgumentException ("no file given."));
    if (file.indexOf ("..") >= 0) // file is un-url-escaped
      throw (new IllegalArgumentException ("Bad filename given"));

    String filename = "htdocs/" + file;
    if (filename.endsWith ("/"))
      filename = filename + "index.html";
    filename = filename.replace ('/', File.separatorChar);

    final File fle = new File (filename);
    if (!fle.exists ())
    {
      // remove htdocs
      do404 (filename.substring (7));
      return;
    }

    // TODO: check etag/if-modified-since and handle it.
    final HttpHeader response = con.getHttpGenerator ().getHeader ();
    setMime (filename, response);

    length = fle.length ();
    response.setHeader ("Content-Length", Long.toString (length));
    con.setContentLength (response.getHeader ("Content-Length"));
    final Date lm = new Date (fle.lastModified () - con.getProxy ().getOffset ());
    response.setHeader ("Last-Modified", HttpDateParser.getDateString (lm));
    try
    {
      fis = new FileInputStream (filename);
    }
    catch (final IOException e)
    {
      throw (new IllegalArgumentException ("Could not open file: '" + file + "'."));
    }
    sendHeader (response);
  }

  private void setMime (final String filename, final HttpHeader response)
  {
    // TODO: better filename mapping.
    final String type = MimeTypeMapper.getMimeType (filename);
    if (type != null)
      response.setHeader ("Content-type", type);
  }

  private void do404 (final String filename) throws IOException
  {
    final HttpHeader response = con.getHttpGenerator ().get404 (filename);
    sendHeader (response);
  }

  private void sendHeader (final HttpHeader header) throws IOException
  {
    final HttpHeaderSender hhs = new HttpHeaderSender (con.getChannel (),
                                                       con.getNioHandler (),
                                                       tlClient,
                                                       header,
                                                       true,
                                                       this);
    hhs.sendHeader ();
  }

  /**
   * Write the header and the file to the output.
   */
  private void channelTransfer (final long length)
  {
    final TransferListener ftl = new FileTransferListener ();
    final TransferHandler th = new TransferHandler (con.getNioHandler (),
                                                    new FCTransferable (length),
                                                    con.getChannel (),
                                                    tlProxy,
                                                    tlClient,
                                                    ftl);
    th.transfer ();
  }

  private class FCTransferable implements Transferable
  {
    private final long length;

    public FCTransferable (final long length)
    {
      this.length = length;
    }

    public long transferTo (final long position, final long count, final WritableByteChannel target) throws IOException
    {
      return fc.transferTo (position, count, target);
    }

    public long length ()
    {
      return length;
    }
  }

  private class FileTransferListener implements TransferListener
  {
    public void transferOk ()
    {
      closeFile ();
      con.logAndTryRestart ();
    }

    public void failed (final Exception cause)
    {
      closeFile ();
      FileSender.this.failed (cause);
    }
  }

  private void closeFile ()
  {
    final Closeable c = fc;
    StreamHelper.close (c);
    final Closeable c1 = fis;
    StreamHelper.close (c1);
  }

  public void httpHeaderSent ()
  {
    if (fis != null)
    {
      fc = fis.getChannel ();
      channelTransfer (length);
    }
    else
    {
      con.logAndTryRestart ();
    }
  }

  public void failed (final Exception e)
  {
    closeFile ();
    logger.log (Level.WARNING, "Exception when handling meta", e);
    con.logAndClose ();
  }

  public void timeout ()
  {
    closeFile ();
    logger.warning ("Timeout when handling meta.");
    con.logAndClose ();
  }
}
