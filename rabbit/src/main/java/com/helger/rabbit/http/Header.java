package com.helger.rabbit.http;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import com.helger.commons.io.stream.StreamHelper;
import com.helger.rabbit.io.IStorable;
import com.helger.rabbit.util.StringCache;

/**
 * This class holds a single header value, that is a &quot;type: some text&quot;
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class Header implements IStorable
{
  private String type;
  private String value;

  /** The String consisting of \r and \n */
  public static final String CRLF = "\r\n";

  /** The string cache we are using. */
  private static final StringCache stringCache = StringCache.getSharedInstance ();

  private static String getCachedString (final String s)
  {
    return stringCache.getCachedString (s);
  }

  /** Used for externalization. */
  public Header ()
  {
    // empty
  }

  /**
   * Create a new header
   *
   * @param type
   *        the type of this header
   * @param value
   *        the actual value
   */
  public Header (final String type, final String value)
  {
    this.type = getCachedString (type);
    this.value = getCachedString (value);
  }

  /**
   * Get the type of this header.
   *
   * @return the type of this header
   */
  public String getType ()
  {
    return type;
  }

  /**
   * Get the value of this header.
   *
   * @return the value of this header
   */
  public String getValue ()
  {
    return value;
  }

  /**
   * Set the value of this header to the new value given.
   *
   * @param newValue
   *        the new value
   */
  public void setValue (final String newValue)
  {
    value = newValue;
  }

  @Override
  public boolean equals (final Object o)
  {
    if (o instanceof Header)
    {
      return (((Header) o).type.equalsIgnoreCase (type));
    }
    return false;
  }

  @Override
  public int hashCode ()
  {
    return type.hashCode ();
  }

  /**
   * Update the value by appending the given string to it.
   *
   * @param s
   *        the String to append to the current value
   */
  public void append (final String s)
  {
    value += CRLF + s;
    value = getCachedString (value);
  }

  public void write (final DataOutput out) throws IOException
  {
    StreamHelper.writeSafeUTF (out, type);
    StreamHelper.writeSafeUTF (out, value);
  }

  public void read (final DataInput in) throws IOException
  {
    type = getCachedString (StreamHelper.readSafeUTF (in));
    value = getCachedString (StreamHelper.readSafeUTF (in));
  }
}
