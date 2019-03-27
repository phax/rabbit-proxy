package com.helger.rabbit.cache.ncache;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;

import com.helger.rabbit.cache.ICache;

/**
 * A class to store the cache entrys data hook on file. A Http Header is a big
 * thing so it is nice to write it to disk.
 *
 * @param <V>
 *        the type of the data stored in files.
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class FiledHook <V> extends FileData <V>
{
  private static final long serialVersionUID = 20050430;

  protected String getExtension ()
  {
    return "hook";
  }

  private <K> File getFileName (final ICache <K, V> cache, final long id)
  {
    return cache.getEntryName (id, true, getExtension ());
  }

  /**
   * Get the hooked data.
   *
   * @param <K>
   *        the type of the keys used in the cache
   * @param cache
   *        the Caching reading the data
   * @param entry
   *        the CacheEntry that holds the data
   * @param LOGGER
   *        the Logger to use
   * @return the data read from the file cache
   * @throws IOException
   *         if reading the data fails
   */
  public <K> V getData (final NCache <K, V> cache,
                        final NCacheData <K, V> entry,
                        final Logger LOGGER) throws IOException
  {
    return readData (getFileName (cache, entry.getID ()), cache.getHookFileHandler ());
  }

  /**
   * Set the hooked data.
   *
   * @param <K>
   *        the type of the keys used in the cache
   * @param cache
   *        the Caching storing the data
   * @param id
   *        the id of the cache entry storing this data
   * @param fh
   *        the FileHandler used to do the data conversion
   * @param hook
   *        the data to store
   * @param LOGGER
   *        the Logger to use
   * @return the size of the file that was written
   * @throws IOException
   *         if reading the data fails
   */
  protected <K> long storeHook (final NCache <K, V> cache,
                                final long id,
                                final FileHandler <V> fh,
                                final V hook,
                                final Logger LOGGER) throws IOException
  {
    return writeData (getFileName (cache, id), fh, hook);
  }
}
