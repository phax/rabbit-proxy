package com.helger.rabbit.cache.ncache;

import org.slf4j.Logger;

/**
 * A key to use when searching the cache. This class only exists to trick
 * equals/hashCode that we have the same key.
 *
 * @param <V>
 *        the type of the data stored
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class MemoryHook <V> extends FiledHook <V>
{
  private static final long serialVersionUID = 20060606;
  private final V data;

  public MemoryHook (final V data)
  {
    this.data = data;
  }

  @Override
  public <K> V getData (final NCache <K, V> cache, final NCacheData <K, V> entry, final Logger LOGGER)
  {
    return data;
  }
}
