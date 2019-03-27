package com.helger.rabbit.filter;

import java.net.MalformedURLException;
import java.net.URL;

import com.helger.rabbit.html.HtmlBlock;
import com.helger.rabbit.html.Tag;
import com.helger.rabbit.html.TagType;
import com.helger.rabbit.html.Token;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.util.Config;

/**
 * A class that inserts some text and links at the top of a page. Useful for
 * inserting links to unfiltered page.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class BodyFilter extends AbstractSimpleTagFilter
{
  private boolean done = false;

  /** the identifier for pages filtered with this proxy. */
  private static final String PREFIX = "This page is filtered by RabbIT ";
  /** the string to append after our advertising. */
  private static final String POSTFIX = "<br>";
  /** the link string. */
  private static final String LINK = "unfiltered page";

  // for the factory.
  public BodyFilter ()
  {}

  /**
   * Create a new BodyFilter for the given request, response pair.
   *
   * @param con
   *        the Connection handling the request.
   * @param request
   *        the actual request made.
   * @param response
   *        the actual response being sent.
   */
  public BodyFilter (final Connection con, final HttpHeader request, final HttpHeader response)
  {
    super (con, request, response);
  }

  public AbstractHtmlFilter newFilter (final Connection con, final HttpHeader request, final HttpHeader response)
  {
    return new BodyFilter (con, request, response);
  }

  /**
   * Insert some text at the top of the html page.
   *
   * @param block
   *        the part of the html page we are filtering.
   */
  @Override
  public void filterHtml (final HtmlBlock block)
  {
    if (!done)
    {
      super.filterHtml (block);
    }
  }

  @Override
  public void handleTag (final Tag tag, final HtmlBlock block, final int tokenIndex)
  {
    if (tag.getTagType () == TagType.BODY)
    {
      insertTokens (block, tokenIndex + 1);
      done = true;
    }
  }

  /**
   * Insert the links in an ordered fashion.
   *
   * @param block
   *        the html block were filtering.
   * @param nPos
   *        the position in the block were inserting stuff at.
   * @return the new position in the block.
   */
  protected int insertTokens (final HtmlBlock block, final int nPos)
  {
    final Config config = con.getProxy ().getConfig ();
    int pos = nPos;
    block.insertToken (new Token (config.getProperty (getClass ().getName (), "prefix", PREFIX)), pos++);
    if (config.getProperty (getClass ().getName (), "unfilteredlink", "true").toLowerCase ().equals ("true"))
    {
      final Tag a = new Tag ("A");
      try
      {
        final URL url = new URL (request.getRequestURI ());
        a.addArg ("HREF", _getHref (url));
        block.insertToken (new Token (a), pos++);
        block.insertToken (new Token (config.getProperty (getClass ().getName (), "link", LINK)), pos++);
        final Tag slasha = new Tag ("/A");
        block.insertToken (new Token (slasha), pos++);
      }
      catch (final MalformedURLException e)
      {
        // ignore
      }
    }
    block.insertToken (new Token (config.getProperty (getClass ().getName (), "postfix", POSTFIX)), pos++);
    return pos;
  }

  private static String _getHref (final URL url)
  {
    final StringBuilder sb = new StringBuilder ();
    sb.append ("\"");
    sb.append (url.getProtocol ());
    sb.append ("://noproxy.");
    sb.append (url.getHost ());
    sb.append ((url.getPort () > 0) ? ":" + url.getPort () : "");
    sb.append (url.getFile ());
    if (url.getRef () != null)
      sb.append ("#").append (url.getRef ());
    sb.append ("\"");
    return sb.toString ();
  }
}
