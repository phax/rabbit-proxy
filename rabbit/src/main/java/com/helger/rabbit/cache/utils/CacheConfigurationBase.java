package com.helger.rabbit.cache.utils;

import org.slf4j.Logger;

import com.helger.commons.CGlobal;
import com.helger.commons.collection.attr.StringMap;
import com.helger.rabbit.cache.ICacheConfiguration;

/**
 * A base implementation of cache configuration.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public abstract class CacheConfigurationBase implements ICacheConfiguration
{
  private long maxSize = 0;
  private long cacheTimeMS = 0;

  private static final String DEFAULT_SIZE = "10"; // 10 MB.
  private static final String DEFAULT_CACHE_TIME = "24"; // 1 day.

  public synchronized long getMaxSize ()
  {
    return maxSize;
  }

  public synchronized void setMaxSize (final long newMaxSize)
  {
    maxSize = newMaxSize;
  }

  public synchronized long getCacheTime ()
  {
    return cacheTimeMS;
  }

  public synchronized void setCacheTime (final long newCacheTime)
  {
    cacheTimeMS = newCacheTime;
  }

  public void setup (final Logger aLogger, final StringMap config)
  {
    final String cmsize = config.getOrDefault ("maxsize", DEFAULT_SIZE);
    try
    {
      // size is in MB
      setMaxSize (Long.parseLong (cmsize) * CGlobal.BYTES_PER_MEGABYTE);
    }
    catch (final NumberFormatException e)
    {
      aLogger.warn ("Bad number for cache maxsize: '" + cmsize + "'");
    }

    final String ctime = config.getOrDefault ("cachetime", DEFAULT_CACHE_TIME);
    try
    {
      // time is given in hours
      setCacheTime (Long.parseLong (ctime) * CGlobal.MILLISECONDS_PER_HOUR);
    }
    catch (final NumberFormatException e)
    {
      aLogger.warn ("Bad number for cache cachetime: '" + ctime + "'");
    }
  }
}
