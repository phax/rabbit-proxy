package com.helger.rabbit.meta;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.helger.rabbit.cache.ICache;
import com.helger.rabbit.cache.ICacheConfiguration;
import com.helger.rabbit.cache.ICacheEntry;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.proxy.HtmlPage;

/**
 * A cache inspector.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class CacheStatus extends BaseMetaHandler
{
  private static final int NUMBER_OF_ENTRIES = 256;

  @Override
  protected String getPageHeader ()
  {
    return "Cache status";
  }

  @Override
  protected PageCompletion addPageInformation (final StringBuilder sb)
  {
    addStatus (sb);
    return PageCompletion.PAGE_DONE;
  }

  private void addStatus (final StringBuilder sb)
  {
    final ICache <HttpHeader, HttpHeader> cache = con.getProxy ().getCache ();
    long cursizemb = cache.getCurrentSize ();
    cursizemb /= (1024 * 1024);
    final ICacheConfiguration cc = cache.getCacheConfiguration ();
    long maxsizemb = cc.getMaxSize ();
    maxsizemb /= (1024 * 1024);
    long cachetimeh = cc.getCacheTime ();
    cachetimeh /= (1000 * 60 * 60);
    sb.append ("Cachedir: ").append (cc.getCacheDir ());
    sb.append (".<br>Cache capacity: ");
    sb.append (cache.getNumberOfEntries ()).append (" files");
    sb.append (".<br>\ncurrent Size: ").append (cursizemb);
    sb.append (" MB. (").append (cache.getCurrentSize ());
    sb.append (" bytes).<br>\nMax Size: ").append (maxsizemb);
    sb.append (" MB.<br>\nCachetime: ").append (cachetimeh);
    sb.append (" hours.<br>\n");
    sb.append ("<br>Partial listing of contents in cache, " + "select entryset:<br>\n");

    addPartSelection (sb, cache);
    addEntries (sb, cache);
  }

  private void addPartSelection (final StringBuilder sb, final ICache <HttpHeader, HttpHeader> cache)
  {
    final long entries = cache.getNumberOfEntries ();
    final long lim = (long) Math.ceil (entries / (double) NUMBER_OF_ENTRIES);
    for (long i = 0; i < lim; i++)
    {
      if (i > 0)
        sb.append (", ");
      long j = i * NUMBER_OF_ENTRIES;
      final long k = Math.min (j + NUMBER_OF_ENTRIES, entries);
      j++;
      sb.append ("<a href=\"CacheStatus?start=").append (j);
      sb.append ("\">").append (j).append ("-").append (k);
      sb.append ("</a>");
    }
  }

  private void addEntries (final StringBuilder sb, final ICache <HttpHeader, HttpHeader> cache)
  {
    sb.append (HtmlPage.getTableHeader (100, 1));
    sb.append (HtmlPage.getTableTopicRow ());
    sb.append ("<th>n</th><th>URL</th><th>filename</th><th>size</th>" + "<th>expires</th></tr>\n");

    final String s = htab.get ("start");
    int start = 0;
    if (s != null)
    {
      try
      {
        start = Integer.parseInt (s);
      }
      catch (final NumberFormatException ex)
      {
        ex.printStackTrace ();
      }
    }
    final int end = start + NUMBER_OF_ENTRIES - 1;
    long totalsize = 0;
    int count = 0;
    final DateFormat sdf = new SimpleDateFormat ("yyyyMMdd-HH:mm");
    final Date d = new Date ();
    for (final ICacheEntry <HttpHeader, HttpHeader> lister : cache.getEntries ())
    {
      count++; // 1-4 5-8
      if (count < start || count > end)
        continue;
      HttpHeader lheader = null;
      lheader = lister.getKey ();
      if (lheader == null)
        continue;
      // reading the data hook will cause lots of file reading...
      // HTTPHeader webheader = (HTTPHeader)lister.getDataHook (cache);
      String filev = lheader.getRequestURI ();
      if (filev.length () > 60)
        filev = filev.substring (0, 57) + "...";
      d.setTime (lister.getExpires ());
      sb.append ("<tr><td>");
      sb.append (count);
      sb.append ("</td><td><a href = \"");
      sb.append (lheader.getRequestURI ());
      sb.append ("\" target = cacheview>");
      sb.append (filev).append ("</a></td>");
      sb.append ("<td>");
      sb.append (cache.getEntryName (lister.getID (), true, null));
      sb.append ("</td><td align=\"right\">");
      sb.append (lister.getSize ());
      sb.append ("</td><td>").append (sdf.format (d));
      sb.append ("</td></tr>\n");
      totalsize += lister.getSize ();
    }
    sb.append ("<tr><td>&nbsp;</td><td><B>Total:</b></td><td>&nbsp;" + "</td><td align=\"right\">");
    sb.append (totalsize);
    sb.append ("</td><td>&nbsp;</td></tr>\n</table>\n");
  }
}
