package com.helger.rabbit.filter;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.helger.rabbit.html.HtmlBlock;
import com.helger.rabbit.html.Tag;
import com.helger.rabbit.html.TagType;
import com.helger.rabbit.html.Token;
import com.helger.rabbit.html.TokenType;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.proxy.HttpProxy;
import com.helger.rabbit.util.Config;

/**
 * This class switches advertising images into another image.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class AdFilter extends HtmlFilter
{
  /** the image we replace ads with */
  private static final String ADREPLACER = "http://$proxy/FileSender/public/NoAd.gif";

  /** the actual imagelink. */
  private String adreplacer = null;

  /** The pattern. */
  private Pattern adPattern;

  /**
   * Create a new AdFilter factory
   */
  public AdFilter ()
  {
    // empty
  }

  /**
   * Create a new AdFilter for the given request, response pair.
   * 
   * @param con
   *        the Connection handling the request
   * @param request
   *        the actual request made.
   * @param response
   *        the actual response being sent.
   */
  public AdFilter (final Connection con, final HttpHeader request, final HttpHeader response)
  {
    super (con, request, response);
    int idx;
    final HttpProxy proxy = con.getProxy ();
    adreplacer = proxy.getConfig ().getProperty (getClass ().getName (), "adreplacer", ADREPLACER);
    while ((idx = adreplacer.indexOf ("$proxy")) > -1)
    {
      adreplacer = adreplacer.substring (0, idx) +
                   proxy.getHost ().getHostName () +
                   ":" +
                   proxy.getPort () +
                   adreplacer.substring (idx + "$proxy".length ());
    }
  }

  public HtmlFilter newFilter (final Connection con, final HttpHeader request, final HttpHeader response)
  {
    return new AdFilter (con, request, response);
  }

  /**
   * Check if the given tag ends the current a-tag. Some sites have broken html
   * (linuxtoday.com!).
   * 
   * @param tt
   *        the TagType to check
   * @return true if the tag is an end tag
   */
  private boolean isAEnder (final TagType tt)
  {
    return tt == TagType.SA || tt == TagType.STD || tt == TagType.STR;
  }

  /**
   * Removes advertising from the given block.
   * 
   * @param block
   *        the part of the html page we are filtering.
   */
  @Override
  public void filterHtml (final HtmlBlock block)
  {
    int astart;

    final List <Token> tokens = block.getTokens ();
    final int tsize = tokens.size ();
    for (int i = 0; i < tsize; i++)
    {
      final Token t = tokens.get (i);
      if (t.getType () == TokenType.TAG)
      {
        final Tag tag = t.getTag ();
        final TagType tagtype = tag.getTagType ();
        if (tagtype == TagType.A)
        {
          astart = i;
          final int ttsize = tokens.size ();
          for (; i < ttsize; i++)
          {
            final Token tk2 = tokens.get (i);
            if (tk2.getType () == TokenType.TAG)
            {
              final Tag tag2 = tk2.getTag ();
              final TagType t2tt = tag2.getTagType ();
              if (t2tt != null && isAEnder (t2tt))
                break;
              else
                if (t2tt != null && t2tt == TagType.IMG && isEvil (tag.getAttribute ("href")))
                  tag2.setAttribute ("src", adreplacer);
            }
          }
          if (i == tsize && astart < i)
          {
            block.setRest ((tokens.get (astart)).getStartIndex ());
          }
        }
        else
          if (tagtype == TagType.LAYER || tagtype == TagType.SCRIPT)
          {
            final String src = tag.getAttribute ("src");
            if (isEvil (src))
              tag.setAttribute ("src", adreplacer);
          }
      }
    }
  }

  /**
   * Check if a string is evil (that is its probably advertising).
   * 
   * @param str
   *        the String to check.
   * @return true if the given string seems to contain advertising links
   */
  public boolean isEvil (final String str)
  {
    if (str == null)
      return false;
    if (adPattern == null)
    {
      final Config conf = con.getProxy ().getConfig ();
      final String adLinks = conf.getProperty (getClass ().getName (), "adlinks", "[/.]ad[/.]");
      adPattern = Pattern.compile (adLinks);
    }
    final Matcher m = adPattern.matcher (str);
    return (m.find ());
  }
}
