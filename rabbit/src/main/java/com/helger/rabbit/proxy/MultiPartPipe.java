package com.helger.rabbit.proxy;

import java.nio.ByteBuffer;
import java.util.StringTokenizer;

import com.helger.rabbit.httpio.LineListener;
import com.helger.rabbit.httpio.LineReader;

/**
 * A helper class for dealing with multipart data.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class MultiPartPipe
{
  private String boundary;
  private boolean endFound = false;

  /**
   * Create a new MultiPartPipe
   * 
   * @param ctHeader
   *        the content type header hodling the boundary
   */
  public MultiPartPipe (final String ctHeader)
  {
    final StringTokenizer st = new StringTokenizer (ctHeader, " =\n\r\t;");
    while (st.hasMoreTokens ())
    {
      final String t = st.nextToken ();
      if (t.equals ("boundary") && st.hasMoreTokens ())
      {
        boundary = st.nextToken ();
        break;
      }
    }
    if (boundary == null)
      throw new IllegalArgumentException ("failed to find multipart " + "boundary in: '" + ctHeader + "'");
  }

  /**
   * Parse the buffer, will set the position and the limit.
   * 
   * @param buf
   *        the ByteBuffer to parse
   */
  public void parseBuffer (final ByteBuffer buf)
  {
    final int pos = buf.position ();
    final LineReader lr = new LineReader (true);
    final LineHandler lh = new LineHandler (buf);
    do
    {
      lr.readLine (buf, lh);
    } while (!endFound && buf.hasRemaining ());

    // send the block.
    buf.position (pos);
  }

  /**
   * Check if the multipart data has been fully handled.
   * 
   * @return true if all multipart data has been handled
   */
  public boolean isFinished ()
  {
    return endFound;
  }

  private class LineHandler implements LineListener
  {
    private final ByteBuffer buf;

    public LineHandler (final ByteBuffer buf)
    {
      this.buf = buf;
    }

    // check for end line and if it is found we limit the buffer to
    // this position.
    public void lineRead (final String line)
    {
      if (line.startsWith ("--") && line.endsWith ("--") && line.substring (2, line.length () - 2).equals (boundary))
      {
        buf.limit (buf.position ());
        endFound = true;
      }
    }
  }
}
