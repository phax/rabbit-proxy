package com.helger.rabbit.http;

import java.io.UnsupportedEncodingException;

/**
 * A http header with some predefined content
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class HttpHeaderWithContent extends HttpHeader
{
  private transient byte [] content;

  /**
   * Create a new HTTPHeader from scratch
   */
  public HttpHeaderWithContent ()
  {
    // empty
  }

  @Override
  protected void fillBuffer (final StringBuilder sb)
  {
    super.fillBuffer (sb);
    if (content != null)
      sb.append (content);
  }

  @Override
  public byte [] getBytes ()
  {
    final byte [] header = super.getBytes ();
    if (content == null)
      return header;
    final byte [] res = new byte [header.length + content.length];
    System.arraycopy (header, 0, res, 0, header.length);
    System.arraycopy (content, 0, res, header.length, content.length);
    return res;
  }

  /**
   * Set the Content for the request/response Mostly not used for responses. As
   * a side effect the &quot;Content-Length&quot; header is also set.
   * 
   * @param content
   *        the binary content.
   */
  public void setContent (final byte [] content)
  {
    this.content = content;
    setHeader ("Content-Length", "" + content.length);
  }

  /**
   * Set the Content for the request/response Mostly not used for responses. As
   * a side effect the &quot;Content-Length&quot; header is also set.
   * 
   * @param data
   *        the String to set
   * @param charset
   *        the character encoding to use when converting the string to bytes
   * @throws IllegalArgumentException
   *         if the charset is unknown
   */
  public void setContent (final String data, final String charset)
  {
    try
    {
      setContent (data.getBytes (charset));
    }
    catch (final UnsupportedEncodingException e)
    {
      throw new IllegalArgumentException ("Unknown encoding: " + charset, e);
    }
  }

  @Override
  public byte [] getContent ()
  {
    return content;
  }

}
