package com.helger.rabbit.meta;

import com.helger.rabbit.cache.CacheException;
import com.helger.rabbit.cache.ICache;
import com.helger.rabbit.http.HttpHeader;

/**
 * Clears the cache completely
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ClearCache extends BaseMetaHandler
{
  private boolean timeToClean = false;

  @Override
  protected String getPageHeader ()
  {
    return "Clearing cache";
  }

  /** Add the page information */
  @Override
  protected PageCompletion addPageInformation (final StringBuilder sb)
  {
    // Send the wait message on the first time.
    // Start the cleaning when the wait message has been sent.
    if (!timeToClean)
    {
      sb.append ("Please wait...<br>\n");
      timeToClean = true;
      return PageCompletion.PAGE_NOT_DONE;
    }
    final ICache <HttpHeader, HttpHeader> cache = con.getProxy ().getCache ();
    try
    {
      cache.clear ();
      sb.append ("<font color=\"blue\">done!</font>\n");
    }
    catch (final CacheException e)
    {
      failed (e);
    }
    return PageCompletion.PAGE_DONE;
  }
}
