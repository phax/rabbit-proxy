package com.helger.rabbit.html;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is used to describe a piece of a HTML page. A block is composed of
 * Tokens and a rest (unparseable data, unfinished tags etc).
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class HtmlBlock
{

  private final List <Token> tokens = new ArrayList <> ();
  private int currentToken = 0;
  private final Charset cs;

  private final char [] realpage;
  private final int length;
  private int restStart = -1;
  private final byte [] decodeRest;

  /**
   * Create a HtmlBLock from the given byte array.
   * 
   * @param page
   *        the byte array that is the real page
   * @param length
   *        the number of chars that may be used
   * @param cs
   *        the Charset
   * @param decodeRest
   *        the remaining bytes of the decode
   */
  public HtmlBlock (final char [] page, final int length, final Charset cs, final byte [] decodeRest)
  {
    if (page == null)
      throw new IllegalArgumentException ("page part may not be null");
    this.realpage = page;
    this.length = length;
    this.cs = cs;
    this.decodeRest = decodeRest;
    restStart = length;
  }

  /**
   * Set the rest of the page to start at given position.
   * 
   * @param reststart
   *        the new index of the rest of the page.
   */
  public void setRest (final int reststart)
  {
    if (reststart > length)
      throw new IllegalArgumentException ("reststart: " + reststart + " may not be bigger than " + "length: " + length);
    this.restStart = reststart;
  }

  /**
   * Get the number of characters that the rest is.
   * 
   * @return the length of the rest.
   */
  private int restSize ()
  {
    final int dcr = decodeRest != null ? decodeRest.length : 0;
    return length - restStart + dcr;
  }

  /**
   * Check if this block has any rest data, that is bytes that will not be used.
   * 
   * @return true if there is any decode rest bytes
   */
  public boolean hasRests ()
  {
    return restSize () > 0 || (decodeRest != null && decodeRest.length > 0);
  }

  /**
   * Get the rest as a byte[]
   * 
   * @return the bytes that were not used in the parsing
   */
  public byte [] getRestBlock ()
  {
    byte [] pr = null;
    if (restSize () > 0)
    {
      final String rest = new String (realpage, restStart, length - restStart);
      pr = rest.getBytes (cs);
    }
    if (pr == null)
      return decodeRest;
    if (decodeRest == null)
      return pr;
    /* both are non null, have to add them */
    final byte [] r = new byte [pr.length + decodeRest.length];
    System.arraycopy (pr, 0, r, 0, pr.length);
    System.arraycopy (decodeRest, 0, r, pr.length, decodeRest.length);
    return r;
  }

  /**
   * Add a Token to this block.
   * 
   * @param t
   *        the Token to add.
   */
  public void addToken (final Token t)
  {
    tokens.add (t);
  }

  /**
   * Does this block have more tokens?
   * 
   * @return true if there is unfetched tokens, false otherwise.
   */
  public boolean hasMoreTokens ()
  {
    return (tokens.size () > currentToken);
  }

  /**
   * Get the next Token.
   * 
   * @return the next Token or null if there are no more tokens.
   */
  public Token nextToken ()
  {
    if (hasMoreTokens ())
      return tokens.get (currentToken++);
    return null;
  }

  /**
   * Get a List of the Tokens.
   * 
   * @return a List with the Tokens for this block.
   */
  public List <Token> getTokens ()
  {
    return tokens;
  }

  /**
   * Insert a token at given position.
   * 
   * @param t
   *        the Token to insert.
   * @param pos
   *        the position to insert the token at.
   */
  public void insertToken (final Token t, final int pos)
  {
    t.setChanged (true);
    if (pos < tokens.size ())
    {
      final Token moved = tokens.get (pos);
      t.setStartIndex (moved.getStartIndex ());
      tokens.add (pos, t);
    }
    else
    {
      t.setStartIndex (length - 1);
      tokens.add (t);
    }
  }

  /**
   * Remove a Token at the given position.
   * 
   * @param pos
   *        the position of the token to remove.
   */
  public void removeToken (final int pos)
  {
    final Token t = tokens.get (pos);
    t.empty ();
    t.setChanged (true);
  }

  /**
   * Get a String representation of this block.
   * 
   * @return a String with the content of this block.
   */
  @Override
  public String toString ()
  {
    final StringBuilder res = new StringBuilder ();
    int start = 0;
    final int tsize = tokens.size ();
    for (int i = 0; i < tsize; i++)
    {
      final Token t = tokens.get (i);
      if (t.getChanged ())
      {
        res.append (new String (realpage, start, t.getStartIndex () - start));
        res.append (t.toString ());
        if (tokens.size () > i + 1)
          start = tokens.get (i + 1).getStartIndex ();
        else
          start = length - 1;
      }
    }
    if (start < restStart - 1)
      res.append (new String (realpage, start, restStart - start));
    return res.toString ();
  }

  /**
   * Get the bytes for this block
   * 
   * @return the ByteBuffers holding the bytes for this block
   */
  public List <ByteBuffer> getBlocks ()
  {
    final List <ByteBuffer> bufs = new ArrayList <> ();
    int start = 0;
    final int tsize = tokens.size ();
    for (int i = 0; i < tsize; i++)
    {
      final Token t = tokens.get (i);
      if (t.getChanged ())
      {
        final int d = t.getStartIndex () - start;
        if (d > 0 && realpage != null)
        {
          final String s = new String (realpage, start, d);
          bufs.add (ByteBuffer.wrap (s.getBytes (cs)));
        }
        final String sb = t.toString ();
        if (sb.length () > 0)
        {
          final byte [] b = sb.getBytes (cs);
          bufs.add (ByteBuffer.wrap (b, 0, b.length));
        }
        if (tokens.size () > i + 1)
          start = tokens.get (i + 1).getStartIndex ();
        else
          start = length;
      }
    }
    if (start < restStart)
    {
      // Seems we have a bug and occasionally get:
      // StringIndexOutOfBoundsException: String index out of range: 2154
      // try to figure out why
      final int length = restStart - start;
      if (start > realpage.length)
        throw getBlockError (start, length);
      if (length < 0)
        throw getBlockError (start, length);
      if (start > realpage.length - length)
        throw getBlockError (start, length);
      final String s = new String (realpage, start, length);
      bufs.add (ByteBuffer.wrap (s.getBytes (cs)));
    }
    return bufs;
  }

  private IllegalArgumentException getBlockError (final int start, final int length)
  {
    return new IllegalArgumentException ("Bad block parameters: " +
                                         "realpage.length: " +
                                         realpage.length +
                                         ", start: " +
                                         start +
                                         ", length: " +
                                         length);
  }
}
