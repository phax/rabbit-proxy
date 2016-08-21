package com.helger.rabbit.proxy;

import java.io.IOException;

import com.helger.rabbit.cache.ICache;
import com.helger.rabbit.cache.ICacheEntry;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.FileResourceSource;
import com.helger.rnio.IBufferHandler;
import com.helger.rnio.INioHandler;

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
  public CacheResourceSource (final ICache <HttpHeader, HttpHeader> cache,
                              final ICacheEntry <HttpHeader, HttpHeader> entry,
                              final INioHandler tr,
                              final IBufferHandler bufHandler) throws IOException
  {
    super (cache.getEntryName (entry.getID (), true, null), tr, bufHandler);
  }
}
