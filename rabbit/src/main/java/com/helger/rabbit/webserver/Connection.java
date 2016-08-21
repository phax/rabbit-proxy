package com.helger.rabbit.webserver;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.helger.rabbit.http.HttpDateParser;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.FileResourceSource;
import com.helger.rabbit.httpio.HttpHeaderListener;
import com.helger.rabbit.httpio.HttpHeaderReader;
import com.helger.rabbit.httpio.HttpHeaderSender;
import com.helger.rabbit.httpio.HttpHeaderSentListener;
import com.helger.rabbit.httpio.ResourceSource;
import com.helger.rabbit.httpio.TransferHandler;
import com.helger.rabbit.httpio.TransferListener;
import com.helger.rabbit.io.BufferHandle;
import com.helger.rabbit.io.CacheBufferHandle;
import com.helger.rabbit.util.MimeTypeMapper;
import com.helger.rabbit.util.TrafficLogger;
import com.helger.rnio.NioHandler;
import com.helger.rnio.impl.Closer;

/**
 * A connection to a web client.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class Connection
{
  private final SimpleWebServer sws;
  private final SocketChannel sc;
  private BufferHandle clientBufferHandle;
  private boolean timeToClose = false;
  private ResourceSource resourceSource = null;

  private final Logger logger = Logger.getLogger (getClass ().getName ());

  /**
   * Create a new Connection for the given web server and socket channel.
   * 
   * @param sws
   *        the web server
   * @param sc
   *        the channel for the request
   */
  public Connection (final SimpleWebServer sws, final SocketChannel sc)
  {
    this.sws = sws;
    this.sc = sc;
  }

  /**
   * Set up a http reader to listen for http request.
   * 
   * @throws IOException
   *         if reading the request fails
   */
  public void readRequest () throws IOException
  {
    if (clientBufferHandle == null)
      clientBufferHandle = new CacheBufferHandle (sws.getBufferHandler ());
    final HttpHeaderListener requestListener = new RequestListener ();
    final HttpHeaderReader requestReader = new HttpHeaderReader (sc,
                                                                 clientBufferHandle,
                                                                 sws.getNioHandler (),
                                                                 sws.getTrafficLogger (),
                                                                 true,
                                                                 true,
                                                                 requestListener);
    requestReader.readHeader ();
  }

  private void shutdown ()
  {
    Closer.close (sc, logger);
  }

  private void handleRequest (final HttpHeader header)
  {
    final String method = header.getMethod ();
    if ("GET".equals (method) || "HEAD".equals (method))
    {
      String path = header.getRequestURI ();
      if (path == null || "".equals (path))
      {
        badRequest ();
        return;
      }
      try
      {
        if (!path.startsWith ("/"))
        {
          final URL u = new URL (path);
          path = u.getFile ();
        }
        if (path.endsWith ("/"))
          path += "index.html";
        path = path.substring (1);
        File f = new File (sws.getBaseDir (), path);
        f = f.getCanonicalFile ();
        if (isSafe (f) && f.exists () && f.isFile ())
        {
          final HttpHeader resp = getHeader ("HTTP/1.1 200 Ok");
          final String type = MimeTypeMapper.getMimeType (f.getAbsolutePath ());
          if (type != null)
            resp.setHeader ("Content-Type", type);
          resp.setHeader ("Content-Length", Long.toString (f.length ()));
          final Date d = new Date (f.lastModified ());
          resp.setHeader ("Last-Modified", HttpDateParser.getDateString (d));
          if ("HTTP/1.0".equals (header.getHTTPVersion ()))
            resp.setHeader ("Connection", "Keep-Alive");

          if (logger.isLoggable (Level.FINEST))
            logger.finest ("Connection; http response: " + resp);

          if ("GET".equals (method))
            resourceSource = new FileResourceSource (f, sws.getNioHandler (), sws.getBufferHandler ());
          sendResponse (resp);
        }
        else
        {
          notFound ();
        }
      }
      catch (final IOException e)
      {
        internalError ();
      }
    }
    else
    {
      methodNotAllowed ();
    }
  }

  private boolean isSafe (final File f) throws IOException
  {
    final File dir = sws.getBaseDir ();
    return f.getCanonicalPath ().startsWith (dir.getCanonicalPath ());
  }

  private void notFound ()
  {
    sendResponse (getHeader ("HTTP/1.1 404 Not Found"));
  }

  private void badRequest ()
  {
    sendBadResponse (getHeader ("HTTP/1.1 400 Bad Request"));
  }

  private void methodNotAllowed ()
  {
    sendBadResponse (getHeader ("HTTP/1.1 405 Method Not Allowed"));
  }

  private void internalError ()
  {
    sendBadResponse (getHeader ("HTTP/1.1 500 Internal Error"));
  }

  private void notImplemented ()
  {
    sendBadResponse (getHeader ("HTTP/1.1 501 Not Implemented"));
  }

  private void sendBadResponse (final HttpHeader response)
  {
    response.setHeader ("Content-type", "text/html");
    timeToClose = true;
    sendResponse (response);
  }

  private void sendResponse (final HttpHeader response)
  {
    try
    {
      final ResponseSentListener sentListener = new ResponseSentListener ();
      final HttpHeaderSender sender = new HttpHeaderSender (sc,
                                                            sws.getNioHandler (),
                                                            sws.getTrafficLogger (),
                                                            response,
                                                            false,
                                                            sentListener);
      sender.sendHeader ();
    }
    catch (final IOException e)
    {
      shutdown ();
    }
  }

  private void sendResource ()
  {
    final TransferListener transferDoneListener = new TransferDoneListener ();
    final TrafficLogger tl = sws.getTrafficLogger ();
    final NioHandler nh = sws.getNioHandler ();
    final TransferHandler th = new TransferHandler (nh, resourceSource, sc, tl, tl, transferDoneListener);
    th.transfer ();
  }

  private HttpHeader getHeader (final String statusLine)
  {
    final HttpHeader ret = new HttpHeader ();
    ret.setStatusLine (statusLine);
    ret.setHeader ("Server", sws.getClass ().getName ());
    ret.setHeader ("Date", HttpDateParser.getDateString (new Date ()));
    return ret;
  }

  private void closeOrContinue ()
  {
    if (timeToClose)
    {
      shutdown ();
    }
    else
    {
      try
      {
        readRequest ();
      }
      catch (final IOException e)
      {
        shutdown ();
      }
    }
  }

  private class AsyncBaseListener
  {
    public void timeout ()
    {
      shutdown ();
    }

    public void failed (final Exception e)
    {
      shutdown ();
    }
  }

  private class RequestListener extends AsyncBaseListener implements HttpHeaderListener
  {
    public void httpHeaderRead (final HttpHeader header,
                                final BufferHandle bh,
                                final boolean keepalive,
                                final boolean isChunked,
                                final long dataSize)
    {
      bh.possiblyFlush ();
      if (isChunked || dataSize > 0)
        notImplemented ();
      if (!keepalive)
        timeToClose = true;
      handleRequest (header);
    }

    public void closed ()
    {
      shutdown ();
    }
  }

  private class ResponseSentListener extends AsyncBaseListener implements HttpHeaderSentListener
  {
    public void httpHeaderSent ()
    {
      if (resourceSource != null)
        sendResource ();
      else
        closeOrContinue ();
    }
  }

  private class TransferDoneListener extends AsyncBaseListener implements TransferListener
  {
    public void transferOk ()
    {
      resourceSource.release ();
      closeOrContinue ();
    }
  }
}
