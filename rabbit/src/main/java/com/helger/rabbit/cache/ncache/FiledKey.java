package com.helger.rabbit.cache.ncache;

import java.io.File;
import java.io.IOException;

import com.helger.commons.equals.EqualsHelper;

/**
 * A class that stores cache keys in compressed form.
 *
 * @param <K>
 *        they key object type
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class FiledKey <K> extends FileData <K>
{
  private static final long serialVersionUID = 20050430;

  // the hashCode for the contained object.
  protected int hashCode;
  private long id;
  protected transient NCache <K, ?> cache;

  protected String getExtension ()
  {
    return "key";
  }

  protected <V> void setCache (final NCache <K, V> cache)
  {
    this.cache = cache;
  }

  protected <V> long storeKey (final NCache <K, V> cache, final long id, final K key) throws IOException
  {
    setCache (cache);
    hashCode = key.hashCode ();
    this.id = id;
    return writeData (getFileName (), cache.getKeyFileHandler (), key);
  }

  private File getFileName ()
  {
    return cache.getEntryName (id, true, getExtension ());
  }

  /**
   * Get the actual key object.
   *
   * @return the key object
   * @throws IOException
   *         if reading the data fails
   */
  public K getData () throws IOException
  {
    return readData (getFileName (), cache.getKeyFileHandler ());
  }

  /**
   * Get the unique id for this object.
   *
   * @return the id of this object
   */
  public long getId ()
  {
    return id;
  }

  /** Check if the given object is equal to the contained key. */
  @Override
  public boolean equals (final Object o)
  {
    if (o == this)
      return true;
    if (o == null || !getClass ().equals (o.getClass ()))
      return false;

    try
    {
      final K myData = getData ();
      Object rhs;
      if (o instanceof FiledKey <?>)
        rhs = ((FiledKey <?>) o).getData ();
      else
        rhs = o;

      return EqualsHelper.equals (myData, rhs);
    }
    catch (final IOException e)
    {
      throw new RuntimeException ("Failed to read contents", e);
    }
  }

  /** Get the hashCode for the contained key object. */
  @Override
  public int hashCode ()
  {
    return hashCode;
  }

  @Override
  public String toString ()
  {
    return "FiledKey: " + hashCode + ", " + getFileName ();
  }
}
