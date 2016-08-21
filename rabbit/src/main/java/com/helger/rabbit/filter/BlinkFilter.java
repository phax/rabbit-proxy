package com.helger.rabbit.filter;

import com.helger.rabbit.html.HtmlBlock;
import com.helger.rabbit.html.Tag;
import com.helger.rabbit.html.TagType;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.proxy.Connection;

/**
 * A filter that removes the blink and /blink tags.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class BlinkFilter extends AbstractSimpleTagFilter
{

  // For the factory.
  public BlinkFilter ()
  {}

  /**
   * Create a new BlinkFilter for the given request, response pair.
   * 
   * @param con
   *        the Connection handling the request.
   * @param request
   *        the actual request made.
   * @param response
   *        the actual response being sent.
   */
  public BlinkFilter (final Connection con, final HttpHeader request, final HttpHeader response)
  {
    super (con, request, response);
  }

  public AbstractHtmlFilter newFilter (final Connection con, final HttpHeader request, final HttpHeader response)
  {
    return new BlinkFilter (con, request, response);
  }

  /**
   * Remove blink tags.
   * 
   * @param block
   *        the part of the html page we are filtering.
   */
  @Override
  public void handleTag (final Tag tag, final HtmlBlock block, final int tokenIndex)
  {
    final TagType tt = tag.getTagType ();
    if (tt == TagType.BLINK || tt == TagType.SBLINK)
      block.removeToken (tokenIndex);
  }
}
