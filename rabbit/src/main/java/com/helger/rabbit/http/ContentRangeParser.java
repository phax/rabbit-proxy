package com.helger.rabbit.http;

import java.util.StringTokenizer;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A class that parses content range headers.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ContentRangeParser
{
  private static final Logger LOGGER = LoggerFactory.getLogger (ContentRangeParser.class);
  private long start;
  private long end;
  private long total;
  private boolean valid = false;

  /**
   * Try to parse the given content range.
   *
   * @param cr
   *        the Content-Range header.
   */
  public ContentRangeParser (@Nullable final String scr)
  {
    String cr = scr;
    if (cr != null)
    {
      if (cr.startsWith ("bytes "))
        cr = cr.substring (6);
      final StringTokenizer st = new StringTokenizer (cr, "-/");
      if (st.countTokens () == 3)
      {
        try
        {
          start = Long.parseLong (st.nextToken ());
          end = Long.parseLong (st.nextToken ());
          final String length = st.nextToken ();
          if ("*".equals (length))
            total = -1;
          else
            total = Long.parseLong (length);
          valid = true;
        }
        catch (final NumberFormatException e)
        {
          LOGGER.warn ("bad content range: " + e + " for string: '" + cr + "'", e);
        }
      }
    }
  }

  /**
   * Check if the content range was valid.
   *
   * @return true if the parsed content range was valid
   */
  public boolean isValid ()
  {
    return valid;
  }

  /**
   * Get the start index
   *
   * @return the start index of the range
   */
  public long getStart ()
  {
    return start;
  }

  /**
   * Get the end index.
   *
   * @return the end index of the range
   */
  public long getEnd ()
  {
    return end;
  }

  /**
   * Get the total size of the resource.
   *
   * @return the resource size if know or -1 if unknown ('*' was used in the
   *         content range).
   */
  public long getTotal ()
  {
    return total;
  }
}
