package com.helger.rabbit.proxy;

import java.io.IOException;

import com.helger.rabbit.cache.Cache;
import com.helger.rabbit.cache.CacheEntry;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.FileResourceSource;
import com.helger.rnio.BufferHandler;
import com.helger.rnio.NioHandler;

/**
 * A resource that comes from the cache.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class CacheResourceSource extends FileResourceSource
{

  /**
   * Create a new CacheResourceSource.
   * 
   * @param cache
   *        the Cache that has the cached resource
   * @param entry
   *        the CacheEntry for the resource
   * @param tr
   *        the NioHandler to use for network and background tasks when serving
   *        this resource
   * @param bufHandler
   *        the BufferHandler to use for this resource
   * @throws IOException
   *         if the cached resource is not available
   */
  public CacheResourceSource (final Cache <HttpHeader, HttpHeader> cache,
                              final CacheEntry <HttpHeader, HttpHeader> entry,
                              final NioHandler tr,
                              final BufferHandler bufHandler) throws IOException
  {
    super (cache.getEntryName (entry.getId (), true, null), tr, bufHandler);
  }
}
