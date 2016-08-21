package com.helger.rabbit.filter;

import com.helger.rabbit.html.HtmlBlock;
import com.helger.rabbit.html.Tag;
import com.helger.rabbit.html.TagType;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.proxy.Connection;

/**
 * This class removes background images from html pages.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class BackgroundFilter extends AbstractSimpleTagFilter
{

  // for the factory part.
  public BackgroundFilter ()
  {}

  /**
   * Create a new BackgroundFilter for the given request, response pair.
   * 
   * @param con
   *        the Connection handling the request.
   * @param request
   *        the actual request made.
   * @param response
   *        the actual response being sent.
   */
  public BackgroundFilter (final Connection con, final HttpHeader request, final HttpHeader response)
  {
    super (con, request, response);
  }

  public AbstractHtmlFilter newFilter (final Connection con, final HttpHeader request, final HttpHeader response)
  {
    return new BackgroundFilter (con, request, response);
  }

  /**
   * Remove background images from the given block.
   * 
   * @param tag
   *        the current Tag
   */
  @Override
  public void handleTag (final Tag tag, final HtmlBlock block, final int tokenIndex)
  {
    final TagType type = tag.getTagType ();
    if (type == TagType.BODY || type == TagType.TABLE || type == TagType.TR || type == TagType.TD)
    {
      tag.removeAttribute ("background");
    }
  }
}
