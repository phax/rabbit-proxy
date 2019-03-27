package com.helger.rabbit.cache.ncache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.collection.attr.StringMap;
import com.helger.rabbit.cache.CacheException;
import com.helger.rabbit.cache.ICache;
import com.helger.rabbit.cache.ICacheConfiguration;
import com.helger.rabbit.cache.ICacheEntry;
import com.helger.rabbit.cache.utils.AbstractCacheConfigurationBase;
import com.helger.rabbit.cache.utils.CacheUtils;
import com.helger.rabbit.io.FileHelper;

/**
 * The NCache is like a Map in lookup/insert/delete The NCache is persistent
 * over sessions (saves itself to disk). The NCache is selfcleaning, that is it
 * removes old stuff.
 *
 * @param <K>
 *        the key type of the cache
 * @param <V>
 *        the data resource
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class NCache <K, V> implements ICache <K, V>, Runnable
{
  private static final String DIR = "/tmp/rabbit/cache"; // standard dir.
  private static final String DEFAULT_CLEAN_LOOP = "60"; // 1 minute

  private static final String CACHEINDEX = "cache.index"; // the indexfile.

  private static final Logger LOGGER = LoggerFactory.getLogger (NCache.class);

  private final Configuration configuration = new Configuration ();
  private boolean changed = false; // have we changed?
  private Thread cleaner = null; // remover of old stuff.
  private int cleanLoopTime = 60 * 1000; // sleeptime between cleanups.

  private long fileNo = 0;
  private long currentSize = 0;
  private File dir = null;
  private Map <FiledKey <K>, NCacheData <K, V>> htab = null;
  private List <NCacheData <K, V>> vec = null;

  private File tempdir = null;
  private final Object dirLock = new Object ();

  private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock ();
  private final Lock r = rwl.readLock ();
  private final Lock w = rwl.writeLock ();

  private final IFileHandler <K> fhk;
  private final IFileHandler <V> fhv;

  private volatile boolean running = true;

  /**
   * Create a cache that uses default values. Note that you must call start to
   * have the cache fully up.
   *
   * @param props
   *        the configuration of the cache
   * @param fhk
   *        the FileHandler for the cache keys
   * @param fhv
   *        the FileHandler for the cache values
   * @throws IOException
   *         if the cache file directory can not be configured
   */
  public NCache (final StringMap props, final IFileHandler <K> fhk, final IFileHandler <V> fhv) throws IOException
  {
    this.fhk = fhk;
    this.fhv = fhv;
    htab = new HashMap <> ();
    vec = new ArrayList <> ();
    setup (props);
  }

  /**
   * Start the thread that cleans the cache.
   */
  public void start ()
  {
    cleaner = new Thread (this, getClass ().getName () + ".cleaner");
    cleaner.setDaemon (true);
    cleaner.start ();
  }

  public ICacheConfiguration getCacheConfiguration ()
  {
    return configuration;
  }

  private class Configuration extends AbstractCacheConfigurationBase
  {
    public URL getCacheDir ()
    {
      r.lock ();
      try
      {
        if (dir == null)
          return null;
        return dir.toURI ().toURL ();
      }
      catch (final MalformedURLException e)
      {
        return null;
      }
      finally
      {
        r.unlock ();
      }
    }

    /**
     * Sets the cachedir. This will flush the cache and make it try to read in
     * the cache from the new dir.
     *
     * @param newDir
     *        the name of the new directory to use.
     * @throws IOException
     *         if the new cache file directory can not be configured
     */
    private void setCacheDir (final String newDir) throws IOException
    {
      w.lock ();
      try
      {
        // save old cachedir.
        if (dir != null)
          writeCacheIndex ();

        // does new dir exist?
        dir = new File (newDir);
        final File dirtest = dir;
        boolean readCache = true;
        if (!dirtest.exists ())
        {
          FileHelper.mkdirs (dirtest);
          if (!dirtest.exists ())
          {
            LOGGER.warn ("could not create cachedir: " + dirtest);
          }
          readCache = false;
        }
        else
          if (dirtest.isFile ())
          {
            LOGGER.warn ("Cachedir: " + dirtest + " is a file");
          }

        synchronized (dirLock)
        {
          tempdir = new File (dirtest, CacheUtils.TEMPDIR);
          if (!tempdir.exists ())
          {
            FileHelper.mkdirs (tempdir);
            if (!tempdir.exists ())
            {
              LOGGER.warn ("could not create cache tempdir: " + tempdir);
            }
          }
          else
            if (tempdir.isFile ())
            {
              LOGGER.warn ("Cache temp dir is a file: " + tempdir);
            }
        }
        if (readCache)
          // move to new dir.
          readCacheIndex ();
      }
      finally
      {
        w.unlock ();
      }
    }
  }

  /**
   * Get how long time the cleaner sleeps between cleanups.
   *
   * @return the number of millis between cleanups
   */
  public int getCleanLoopTime ()
  {
    return cleanLoopTime;
  }

  /**
   * Set how long time the cleaner sleeps between cleanups.
   *
   * @param newCleanLoopTime
   *        the number of miliseconds to sleep.
   */
  public void setCleanLoopTime (final int newCleanLoopTime)
  {
    cleanLoopTime = newCleanLoopTime;
  }

  /**
   * Get the current size of the cache
   *
   * @return the current size of the cache in bytes.
   */
  public long getCurrentSize ()
  {
    r.lock ();
    try
    {
      return currentSize;
    }
    finally
    {
      r.unlock ();
    }
  }

  /**
   * Get the current number of entries in the cache.
   *
   * @return the current number of entries in the cache.
   */
  public long getNumberOfEntries ()
  {
    r.lock ();
    try
    {
      return htab.size ();
    }
    finally
    {
      r.unlock ();
    }
  }

  /**
   * Check that the data hook exists.
   *
   * @param e
   *        the NCacheEntry to check
   * @return true if the cache data is valid, false otherwise
   */
  private boolean checkHook (final NCacheData <K, V> e)
  {
    final FiledHook <V> hook = e.getDataHook ();
    if (hook != null)
    {
      final File entryName = getEntryName (e.getID (), true, "hook");
      if (!entryName.exists ())
        return false;
    }
    // no hook is legal.
    return true;
  }

  /**
   * Get the CacheEntry assosiated with given object.
   *
   * @param k
   *        the key.
   * @return the CacheEntry or null (if not found).
   */
  public ICacheEntry <K, V> getEntry (final K k) throws CacheException
  {
    final NCacheData <K, V> cacheEntry = getCurrentData (k);
    if (cacheEntry != null && !checkHook (cacheEntry))
    {
      // bad entry...
      remove (k);
    }
    /*
     * If you want to implement LRU or something like that: if (cacheEntry !=
     * null) cacheEntry.setVisited (System.currentTimeMillis ());
     */
    return getEntry (cacheEntry);
  }

  public File getEntryName (final long id, final boolean real, final String extension)
  {
    return CacheUtils.getEntryName (dir, id, real, extension);
  }

  /**
   * Reserve space for a CacheEntry with key o.
   *
   * @param k
   *        the key for the CacheEntry.
   * @return a new CacheEntry initialized for the cache.
   */
  public ICacheEntry <K, V> newEntry (final K k)
  {
    long newId = 0;
    // allocate the id for the new entry.
    w.lock ();
    try
    {
      newId = fileNo;
      fileNo++;
    }
    finally
    {
      w.unlock ();
    }
    final long now = System.currentTimeMillis ();
    final long expires = now + configuration.getCacheTime ();
    return new NCacheEntry <> (newId, now, expires, 0, k, null);
  }

  /**
   * Get the file handler for the keys.
   *
   * @return the FileHandler for the key objects
   */
  IFileHandler <K> getKeyFileHandler ()
  {
    return fhk;
  }

  /**
   * Get the file handler for the values.
   *
   * @return the FileHandler for the values
   */
  IFileHandler <V> getHookFileHandler ()
  {
    return fhv;
  }

  /**
   * Insert a CacheEntry into the cache.
   *
   * @param ent
   *        the CacheEntry to store.
   */
  public void addEntry (final ICacheEntry <K, V> ent) throws CacheException
  {
    if (ent == null)
      return;
    final NCacheEntry <K, V> nent = (NCacheEntry <K, V>) ent;
    addEntry (nent);
  }

  private void addEntry (final NCacheEntry <K, V> ent) throws CacheException
  {
    File cfile = getEntryName (ent.getID (), false, null);
    if (!cfile.exists ())
      return;

    final File newName = getEntryName (ent.getID (), true, null);
    final File cacheDir = newName.getParentFile ();
    synchronized (dirLock)
    {
      ensureCacheDirIsValid (cacheDir);
      if (!cfile.renameTo (newName))
        LOGGER.error ("Failed to renamve file from: " + cfile.getAbsolutePath () + " to" + newName.getAbsolutePath ());
    }
    cfile = newName;
    final NCacheData <K, V> data = getData (ent, cfile);
    w.lock ();
    try
    {
      remove (ent.getKey ());
      htab.put (data.getKey (), data);
      currentSize += data.getSize () + data.getKeySize () + data.getHookSize ();
      vec.add (data);
    }
    finally
    {
      w.unlock ();
    }

    changed = true;
  }

  private void ensureCacheDirIsValid (final File f)
  {
    if (f.exists ())
    {
      if (f.isFile ())
        LOGGER.warn ("Wanted cachedir is a file: " + f);
      // good situation...
    }
    else
    {
      try
      {
        FileHelper.mkdirs (f);
      }
      catch (final IOException e)
      {
        LOGGER.warn ("Could not create directory: " + f, e);
      }
    }
  }

  /**
   * Signal that a cache entry have changed.
   */
  public void entryChanged (final ICacheEntry <K, V> ent, final K newKey, final V newHook) throws CacheException
  {
    final NCacheData <K, V> data = getCurrentData (ent.getKey ());
    if (data == null)
    {
      Thread.dumpStack ();
      LOGGER.warn ("Failed to find changed entry so ignoring: " + ent.getID ());
      return;
    }
    try
    {
      data.updateExpireAndSize (ent);
      final long id = ent.getID ();
      final FiledWithSize <FiledKey <K>> fkws = storeKey (newKey, id);
      data.setKey (fkws.t, fkws.size);
      final FiledWithSize <FiledHook <V>> fhws = storeHook (newHook, id);
      data.setDataHook (fhws.t, fhws.size);
    }
    catch (final IOException e)
    {
      throw new CacheException ("Failed to update entry: entry: " + ent + ", newKey: " + newKey, e);
    }
    finally
    {
      changed = true;
    }
  }

  private NCacheData <K, V> getCurrentData (final K key)
  {
    final MemoryKey <K> mkey = new MemoryKey <> (key);
    r.lock ();
    try
    {
      return htab.get (mkey);
    }
    finally
    {
      r.unlock ();
    }
  }

  private void removeHook (final File base, final String extension) throws IOException
  {
    final String hookName = base.getName () + extension;
    // remove possible hook before file...
    final File hfile = new File (base.getParentFile (), hookName);
    if (hfile.exists ())
      FileHelper.delete (hfile);
  }

  /**
   * Remove the Entry with key k from the cache.
   *
   * @param k
   *        the key for the CacheEntry.
   */
  public void remove (final K k) throws CacheException
  {
    NCacheData <K, V> r;
    w.lock ();
    try
    {
      if (k == null)
      {
        // Odd, but seems to happen. Probably removed
        // by someone else before enumeration gets to it.
        return;
      }
      final FiledKey <K> fk = new MemoryKey <> (k);
      r = htab.get (fk);
      if (r != null)
      {
        // remove entries while it is still in htab.
        vec.remove (r);
        currentSize -= (r.getSize () + r.getKeySize () + r.getHookSize ());
        htab.remove (fk);
      }
    }
    finally
    {
      w.unlock ();
    }

    if (r != null)
    {
      // this removes the key => htab.remove can not work..
      final File entryName = getEntryName (r.getID (), true, null);
      try
      {
        removeHook (entryName, ".hook");
        removeHook (entryName, ".key");
        r.setDataHook (null, 0);
        final File cfile = entryName;
        if (cfile.exists ())
        {
          final File p = cfile.getParentFile ();
          FileHelper.delete (cfile);
          // Until NT does rename in a nice manner check for tempdir.
          synchronized (dirLock)
          {
            if (p.exists () && !p.equals (tempdir))
            {
              final String ls[] = p.list ();
              if (ls != null && ls.length == 0)
                FileHelper.delete (p);
            }
          }
        }
      }
      catch (final IOException e)
      {
        throw new CacheException ("Failed to remove file, key: " + k, e);
      }
    }
  }

  /**
   * Clear the Cache from files.
   */
  public void clear () throws CacheException
  {
    ArrayList <FiledKey <K>> ls;
    w.lock ();
    try
    {
      ls = new ArrayList <> (htab.keySet ());
      for (final FiledKey <K> k : ls)
      {
        try
        {
          remove (k.getData ());
        }
        catch (final IOException e)
        {
          throw new CacheException ("Failed to remove entry, key: " + k, e);
        }
      }
      vec.clear (); // just to be safe.
      currentSize = 0;
      changed = true;
    }
    finally
    {
      w.unlock ();
    }
  }

  /**
   * Get the CacheEntries in the cache. Note! some entries may be invalid if you
   * have a corruct cache.
   *
   * @return a Collection of the CacheEntries.
   */
  public Iterable <NCacheEntry <K, V>> getEntries ()
  {
    // Defensive copy so that nothing happen when the user iterates
    r.lock ();
    try
    {
      return new NCacheIterator (htab.values ());
    }
    finally
    {
      r.unlock ();
    }
  }

  private class NCacheIterator implements Iterable <NCacheEntry <K, V>>, Iterator <NCacheEntry <K, V>>
  {
    private final Iterator <NCacheData <K, V>> dataIterator;

    public NCacheIterator (final Collection <NCacheData <K, V>> c)
    {
      dataIterator = new ArrayList <> (c).iterator ();
    }

    public Iterator <NCacheEntry <K, V>> iterator ()
    {
      return this;
    }

    public NCacheEntry <K, V> next ()
    {
      try
      {
        return getEntry (dataIterator.next ());
      }
      catch (final CacheException e)
      {
        throw new RuntimeException ("Failed to get entry", e);
      }
    }

    public boolean hasNext ()
    {
      return dataIterator.hasNext ();
    }

    public void remove ()
    {
      throw new UnsupportedOperationException ();
    }
  }

  /**
   * Read the info from an old cache.
   */
  private void readCacheIndex ()
  {
    try
    {
      final File index = new File (dir, CACHEINDEX);
      if (index.exists ())
        readCacheIndex (index);
      else
        LOGGER.info ("No cache index found: " + index + ", treating as empty cache");
    }
    catch (final IOException e)
    {
      LOGGER.warn ("Couldnt read " +
                   dir +
                   File.separator +
                   CACHEINDEX +
                   ". This is bad (but not serius).\nTreating as empty. ",
                   e);
    }
    catch (final ClassNotFoundException e)
    {
      LOGGER.error ("Couldn't find classes", e);
    }
  }

  @SuppressWarnings ("unchecked")
  private void readCacheIndex (final File index) throws IOException, ClassNotFoundException
  {
    try (final FileInputStream fis = new FileInputStream (index);
        final ObjectInputStream is = new ObjectInputStream (new GZIPInputStream (fis)))
    {
      final long fileNo = is.readLong ();
      final long currentSize = is.readLong ();
      final int size = is.readInt ();
      final Map <FiledKey <K>, NCacheData <K, V>> htab = new HashMap <> ((int) (size * 1.2));
      for (int i = 0; i < size; i++)
      {
        final FiledKey <K> fk = (FiledKey <K>) is.readObject ();
        fk.setCache (this);
        final NCacheData <K, V> entry = (NCacheData <K, V>) is.readObject ();
        htab.put (fk, entry);
      }
      final List <NCacheData <K, V>> vec = (List <NCacheData <K, V>>) is.readObject ();

      // Only set internal state if we managed to get it all.
      this.fileNo = fileNo;
      this.currentSize = currentSize;
      this.htab = htab;
      this.vec = vec;
    }
  }

  /**
   * Make sure that the cache is written to the disk.
   */
  public void flush ()
  {
    writeCacheIndex ();
  }

  /**
   * Store the cache to disk so we can reuse it later.
   */
  private void writeCacheIndex ()
  {
    final String name = dir + File.separator + CACHEINDEX;
    try (final FileOutputStream fos = new FileOutputStream (name);
        final GZIPOutputStream gzos = new GZIPOutputStream (fos);
        final ObjectOutputStream os = new ObjectOutputStream (gzos))
    {
      r.lock ();
      try
      {
        os.writeLong (fileNo);
        os.writeLong (currentSize);
        os.writeInt (htab.size ());
        for (final Map.Entry <FiledKey <K>, NCacheData <K, V>> me : htab.entrySet ())
        {
          os.writeObject (me.getKey ());
          os.writeObject (me.getValue ());
        }
        os.writeObject (vec);
      }
      finally
      {
        r.unlock ();
      }
    }
    catch (final IOException e)
    {
      LOGGER.warn ("Couldnt write " + dir + File.separator + CACHEINDEX + ", This is serious!\n", e);
    }
  }

  /**
   * Loop in a cleaning loop.
   */
  public void run ()
  {
    Thread.currentThread ().setPriority (Thread.MIN_PRIORITY);
    while (running)
    {
      try
      {
        Thread.sleep (cleanLoopTime);
      }
      catch (final InterruptedException e)
      {
        // System.err.println ("Cache interrupted");
      }
      if (!running)
        continue;

      // actually for a busy cache this will lag...
      // but I dont care for now...
      final long milis = System.currentTimeMillis ();
      Map <FiledKey <K>, NCacheData <K, V>> hc;
      r.lock ();
      try
      {
        hc = new HashMap <> (htab);
      }
      finally
      {
        r.unlock ();
      }
      for (final Map.Entry <FiledKey <K>, NCacheData <K, V>> ce : hc.entrySet ())
      {
        try
        {
          final long exp = ce.getValue ().getExpires ();
          if (exp < milis)
            removeKey (ce.getKey ());
        }
        catch (final IOException e)
        {
          LOGGER.warn ("Failed to remove expired entry", e);
        }
        catch (final CacheException e)
        {
          LOGGER.warn ("Failed to remove expired entry", e);
        }
      }

      // IF SIZE IS TO BIG REMOVE A RANDOM AMOUNT OF OBJECTS.
      // What we have to be careful about: we must not remove the same
      // elements two times in a row, this method remove the "oldest" in
      // a sense.

      final long maxSize = configuration.getMaxSize ();
      if (getCurrentSize () > maxSize)
        changed = true;
      while (getCurrentSize () > maxSize)
      {
        w.lock ();
        try
        {
          removeKey (vec.get (0).getKey ());
        }
        catch (final IOException e)
        {
          LOGGER.warn ("Failed to remove entry", e);
        }
        catch (final CacheException e)
        {
          LOGGER.warn ("Failed to remove entry", e);
        }
        finally
        {
          w.unlock ();
        }
      }

      if (changed)
      {
        writeCacheIndex ();
        changed = false;
      }
    }
  }

  private void removeKey (final FiledKey <K> fk) throws IOException, CacheException
  {
    fk.setCache (this);
    remove (fk.getData ());
  }

  public void stop ()
  {
    running = false;
    if (cleaner != null)
    {
      try
      {
        cleaner.interrupt ();
        cleaner.join ();
      }
      catch (final InterruptedException e)
      {
        // ignore
      }
    }
  }

  /**
   * Configure the cache system from the given config.
   *
   * @param config
   *        the properties describing the cache settings.
   * @throws IOException
   *         if the new cache can not be configured correctly
   */
  public void setup (@Nullable final StringMap config) throws IOException
  {
    final StringMap aRealConfig = config == null ? new StringMap () : config;
    final String cachedir = aRealConfig.getOrDefault ("directory", DIR);
    configuration.setCacheDir (cachedir);
    configuration.setup (aRealConfig, LOGGER);
    final String ct = aRealConfig.getOrDefault ("cleanloop", DEFAULT_CLEAN_LOOP);
    try
    {
      setCleanLoopTime (Integer.parseInt (ct) * 1000); // in seconds.
    }
    catch (final NumberFormatException e)
    {
      LOGGER.warn ("Bad number for cache cleanloop: '" + ct + "'");
    }
  }

  private NCacheEntry <K, V> getEntry (final NCacheData <K, V> data) throws CacheException
  {
    if (data == null)
      return null;
    try
    {
      final FiledKey <K> key = data.getKey ();
      key.setCache (this);
      final K keyData = key.getData ();
      final V hook = data.getDataHook ().getData (this, data, LOGGER);
      return new NCacheEntry <> (data.getID (),
                                 data.getCacheTime (),
                                 data.getExpires (),
                                 data.getSize (),
                                 keyData,
                                 hook);
    }
    catch (final IOException e)
    {
      throw new CacheException ("Failed to get: entry: " + data, e);
    }
  }

  private NCacheData <K, V> getData (final NCacheEntry <K, V> entry, final File cacheFile) throws CacheException
  {
    final long id = entry.getID ();
    final long size = cacheFile.length ();
    try
    {
      final FiledWithSize <FiledKey <K>> fkws = storeKey (entry.getKey (), id);
      final FiledWithSize <FiledHook <V>> fhws = storeHook (entry.getDataHook (), id);
      return new NCacheData <> (id,
                                entry.getCacheTime (),
                                entry.getExpires (),
                                size,
                                fkws.t,
                                fkws.size,
                                fhws.t,
                                fhws.size);
    }
    catch (final IOException e)
    {
      // TODO: do we need to clean anything up?
      throw new CacheException ("Failed to store data", e);
    }
  }

  private FiledWithSize <FiledKey <K>> storeKey (final K realKey, final long id) throws IOException
  {
    final FiledKey <K> fk = new FiledKey <> ();
    final long size = fk.storeKey (this, id, realKey);
    return new FiledWithSize <> (fk, size);
  }

  private FiledWithSize <FiledHook <V>> storeHook (final V hook, final long id) throws IOException
  {
    if (hook == null)
      return null;
    final FiledHook <V> fh = new FiledHook <> ();
    final long size = fh.storeHook (this, id, getHookFileHandler (), hook, LOGGER);
    return new FiledWithSize <> (fh, size);
  }

  private static class FiledWithSize <T>
  {
    private final T t;
    private final long size;

    public FiledWithSize (final T t, final long size)
    {
      this.t = t;
      this.size = size;
    }
  }
}
