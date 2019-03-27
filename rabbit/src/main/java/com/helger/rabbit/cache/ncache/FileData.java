package com.helger.rabbit.cache.ncache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A class to store cache data to a file.
 *
 * @param <T>
 *        the data type stored on disk
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class FileData <T> implements Serializable
{
  /**
   * Read the data from disk.
   *
   * @param name
   *        the name of the file to read the data from
   * @param fh
   *        the FileHandler that will do the data convesion
   * @throws IOException
   *         if file reading fails
   * @return the object read
   */
  protected T readData (final File name, final IFileHandler <T> fh) throws IOException
  {
    if (!name.exists ())
      return null;

    try (final FileInputStream fis = new FileInputStream (name); final InputStream is = new GZIPInputStream (fis))
    {
      return fh.read (is);
    }
  }

  protected long writeData (final File name,
                            final IFileHandler <T> fh,
                            final T data) throws IOException
  {

    try (final FileOutputStream fos = new FileOutputStream (name); final OutputStream os = new GZIPOutputStream (fos))
    {
      fh.write (os, data);
      return name.length ();
    }
  }
}
