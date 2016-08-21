package com.helger.rabbit.filter;

import com.helger.rabbit.html.HtmlBlock;
import com.helger.rabbit.html.Tag;
import com.helger.rabbit.html.TagType;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.proxy.Connection;

/**
 * This filter removes the &quot;<tt>lowsrc=some_image.gif</tt>&quot; attributes
 * from the &lt;img&gt; tags.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class LowresImageFilter extends AbstractSimpleTagFilter
{

  // For the factory.
  public LowresImageFilter ()
  {}

  /**
   * Create a new LowresImageFilter for the given request, response pair.
   * 
   * @param con
   *        the Connection handling the request.
   * @param request
   *        the actual request made.
   * @param response
   *        the actual response being sent.
   */
  public LowresImageFilter (final Connection con, final HttpHeader request, final HttpHeader response)
  {
    super (con, request, response);
  }

  public AbstractHtmlFilter newFilter (final Connection con, final HttpHeader request, final HttpHeader response)
  {
    return new LowresImageFilter (con, request, response);
  }

  /**
   * remove the lowres tags.
   * 
   * @param block
   *        the part of the html page we are filtering.
   */
  @Override
  public void handleTag (final Tag tag, final HtmlBlock block, final int tokenIndex)
  {
    if (tag.getTagType () == TagType.IMG)
      tag.removeAttribute ("lowsrc");
  }
}
