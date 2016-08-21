package com.helger.rabbit.html;

/**
 * This exception indicates an error in the parsing of an HTML block.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class HtmlParseException extends Exception
{
  private static final long serialVersionUID = 20050430;

  /**
   * Create a new HtmlParseException with the given string.
   * 
   * @param s
   *        the reason for the exception.
   */
  public HtmlParseException (final String s)
  {
    super (s);
  }

  /**
   * Create a new HtmlParseException with the given Throwable.
   * 
   * @param t
   *        the reason for the exception.
   */
  public HtmlParseException (final Throwable t)
  {
    super (t.toString ());
  }
}
