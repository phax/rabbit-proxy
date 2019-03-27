package com.helger.rabbit.httpio;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.rabbit.http.Header;
import com.helger.rabbit.http.HttpHeader;

/**
 * A parser of http headers
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class HttpHeaderParser implements LineListener
{
  private static final Logger LOGGER = LoggerFactory.getLogger (HttpHeaderParser.class);

  private static final ByteBuffer HTTP_IDENTIFIER = ByteBuffer.wrap (new byte [] { (byte) 'H',
                                                                                   (byte) 'T',
                                                                                   (byte) 'T',
                                                                                   (byte) 'P',
                                                                                   (byte) '/' });

  private static final ByteBuffer EXTRA_LAST_CHUNK = ByteBuffer.wrap (new byte [] { (byte) '0',
                                                                                    (byte) '\r',
                                                                                    (byte) '\n',
                                                                                    (byte) '\r',
                                                                                    (byte) '\n' });

  private final boolean request;
  private final boolean strictHttp;
  private final LineReader lr;
  private HttpHeader header;
  private Header head;
  private boolean append = false;
  private boolean headerRead = false;

  /**
   * Create a new HttpHeaderParser
   *
   * @param request
   *        if true try to read a request, if false try to read a response
   * @param strictHttp
   *        if true http headers will be strictly parsed, if false http newlines
   *        may be single \n
   */
  public HttpHeaderParser (final boolean request, final boolean strictHttp)
  {
    this.request = request;
    this.strictHttp = strictHttp;
    lr = new LineReader (strictHttp);
  }

  /**
   * Restore the parser to its initial state
   */
  public void reset ()
  {
    header = null;
    head = null;
    append = false;
    headerRead = false;
  }

  /**
   * Get the current header
   *
   * @return the header as it looks at this moment
   */
  public HttpHeader getHeader ()
  {
    return header;
  }

  /**
   * Read the data from the buffer and try to build a http header.
   *
   * @param buffer
   *        the ByteBuffer to parse
   * @return true if a full header was read, false if more data is needed.
   */
  public boolean handleBuffer (final ByteBuffer buffer)
  {
    if (!request && header == null && !verifyResponse (buffer))
      return true;
    while (!headerRead && buffer.hasRemaining ())
      lr.readLine (buffer, this);
    return headerRead;
  }

  /**
   * Verify that the response starts with "HTTP/" Failure to verify response
   * means that we should treat all of data as content, that is like HTTP/0.9.
   *
   * @param buffer
   *        the ByteBuffer to parse
   * @return true if the response starts correctly
   */
  private boolean verifyResponse (final ByteBuffer buffer)
  {
    // some broken web servers (apache/2.0.4x) send multiple last-chunks
    if (buffer.remaining () > 4 && matchBuffer (buffer, EXTRA_LAST_CHUNK))
    {
      LOGGER.warn ("Found a last-chunk, trying to ignore it.");
      buffer.position (buffer.position () + EXTRA_LAST_CHUNK.capacity ());
      return verifyResponse (buffer);
    }

    if (buffer.remaining () > 4 && !matchBuffer (buffer, HTTP_IDENTIFIER))
    {
      LOGGER.warn ("http response header with odd start:" + getBufferStartString (buffer, 5));
      // Create a http/0.9 response...
      header = new HttpHeader ();
      return true;
    }

    return true;
  }

  private boolean matchBuffer (final ByteBuffer buffer, final ByteBuffer test)
  {
    final int len = test.remaining ();
    if (buffer.remaining () < len)
      return false;
    final int pos = buffer.position ();
    for (int i = 0; i < len; i++)
      if (buffer.get (pos + i) != test.get (i))
        return false;
    return true;
  }

  private String getBufferStartString (final ByteBuffer buffer, final int size)
  {
    try
    {
      final int pos = buffer.position ();
      final byte [] arr = new byte [size];
      buffer.get (arr);
      buffer.position (pos);
      return new String (arr, "ASCII");
    }
    catch (final UnsupportedEncodingException e)
    {
      return "unable to get ASCII: " + e.toString ();
    }
  }

  /** Handle a newly read line. */
  public void lineRead (final String line)
  {
    if (line.length () == 0)
    {
      headerRead = header != null;
      return;
    }

    if (header == null)
    {
      header = new HttpHeader ();
      header.setRequestLine (line);
      headerRead = false;
      return;
    }

    if (header.isDot9Request ())
    {
      headerRead = true;
      return;
    }

    char c;
    if (header.size () == 0 && line.length () > 0 && ((c = line.charAt (0)) == ' ' || c == '\t'))
    {
      header.setReasonPhrase (header.getReasonPhrase () + line);
      headerRead = false;
      return;
    }

    readHeader (line);
    headerRead = false;
  }

  private void readHeader (final String msg)
  {
    if (msg == null)
    {
      final String err = "Couldnt read headers, connection must be closed";
      throw (new BadHttpHeaderException (err));
    }
    char c = msg.charAt (0);
    if (c == ' ' || c == '\t' || append)
    {
      if (head != null)
      {
        head.append (msg);
        append = checkQuotes (head.getValue ());
      }
      else
      {
        final String ex = "Malformed header: msg: " + msg;
        throw (new BadHttpHeaderException (ex));
      }
      return;
    }
    final int i = msg.indexOf (':');
    if (i < 0)
    {
      switch (msg.charAt (0))
      {
        case 'h':
        case 'H':
          if (msg.toLowerCase ().startsWith ("http/"))
          {
            /*
             * ignoring header since it looks like a duplicate responseline
             */
            return;
          }
          // fallthrough
        default:
          throw (new BadHttpHeaderException ("Malformed header:" + msg));
      }
    }
    int j = i;
    while (j > 0 && ((c = msg.charAt (j - 1)) == ' ' || c == '\t'))
      j--;
    // ok, the header may be empty, so trim away whites.
    String value = msg.substring (i + 1);

    /*
     * there are some sites with broken headers like
     * http://docs1.excite.com/functions.js which returns lines such as this
     * (20040416) /robo msg is: 'Cache-control: must-revalidate"' so we only
     * check for append when in strict mode...
     */
    if (strictHttp)
      append = checkQuotes (value);
    if (!append)
      value = value.trim ();
    head = new Header (msg.substring (0, j), value);
    header.addHeader (head);
  }

  private boolean checkQuotes (final String v)
  {
    int q = v.indexOf ('"');
    if (q == -1)
      return false;
    boolean halfquote = false;
    final int l = v.length ();
    for (; q < l; q++)
    {
      final char c = v.charAt (q);
      if (c == '\\')
        q++; // skip one...
      else
        if (c == '"')
          halfquote = !halfquote;
    }
    return halfquote;
  }
}
