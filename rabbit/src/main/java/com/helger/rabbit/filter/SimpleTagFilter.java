package com.helger.rabbit.filter;

import java.util.List;

import com.helger.rabbit.html.HtmlBlock;
import com.helger.rabbit.html.Tag;
import com.helger.rabbit.html.Token;
import com.helger.rabbit.html.TokenType;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.proxy.Connection;

/**
 * A class that inserts some text and links at the top of a page. Useful for
 * inserting links to unfiltered page.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public abstract class SimpleTagFilter extends HtmlFilter
{

  // For the factory.
  public SimpleTagFilter ()
  {}

  /**
   * Create a new SimpleTagFilter for the given request, response pair.
   * 
   * @param con
   *        the Connection handling the request.
   * @param request
   *        the actual request made.
   * @param response
   *        the actual response being sent.
   */
  public SimpleTagFilter (final Connection con, final HttpHeader request, final HttpHeader response)
  {
    super (con, request, response);
  }

  /**
   * Iterate over all tags and call handleTag on them.
   * 
   * @param block
   *        the part of the html page we are filtering.
   */
  @Override
  public void filterHtml (final HtmlBlock block)
  {
    final List <Token> tokens = block.getTokens ();
    final int tsize = tokens.size ();
    for (int i = 0; i < tsize; i++)
    {
      final Token t = tokens.get (i);
      if (t.getType () == TokenType.TAG)
      {
        final Tag tag = t.getTag ();
        handleTag (tag, block, i);
      }
    }
  }

  /**
   * Handle a tag.
   * 
   * @param tag
   *        the Tag to handle.
   * @param block
   *        the current HtmlBlock
   * @param tokenIndex
   *        the index of the current Token
   */
  public abstract void handleTag (Tag tag, HtmlBlock block, int tokenIndex);
}
