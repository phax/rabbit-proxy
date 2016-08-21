package com.helger.rabbit.cache.utils;

import java.util.logging.Logger;

import com.helger.commons.url.SMap;
import com.helger.rabbit.cache.ICacheConfiguration;

/**
 * A base implementation of cache configuration.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public abstract class CacheConfigurationBase implements ICacheConfiguration
{
  private long maxSize = 0;
  private long cacheTime = 0;

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
    return cacheTime;
  }

  public synchronized void setCacheTime (final long newCacheTime)
  {
    cacheTime = newCacheTime;
  }

  public void setup (final Logger logger, final SMap config)
  {
    final String cmsize = config.getOrDefault ("maxsize", DEFAULT_SIZE);
    try
    {
      // size is in MB
      setMaxSize (Long.parseLong (cmsize) * 1024 * 1024);
    }
    catch (final NumberFormatException e)
    {
      logger.warning ("Bad number for cache maxsize: '" + cmsize + "'");
    }

    final String ctime = config.getOrDefault ("cachetime", DEFAULT_CACHE_TIME);
    try
    {
      // time is given in hours
      setCacheTime (Long.parseLong (ctime) * 1000 * 60 * 60);
    }
    catch (final NumberFormatException e)
    {
      logger.warning ("Bad number for cache cachetime: '" + ctime + "'");
    }
  }
}
