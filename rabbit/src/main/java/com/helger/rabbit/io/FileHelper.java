package com.helger.rabbit.io;

import java.io.File;
import java.io.IOException;

/**
 * Helper class for common file operations.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class FileHelper
{
  /**
   * Try to delete a file.
   * 
   * @param toDelete
   *        the file to delete
   * @throws NullPointerException
   *         if toDelete is null
   * @throws IOException
   *         if file deletion failed
   */
  public static void delete (final File toDelete) throws IOException
  {
    if (toDelete == null)
      throw new NullPointerException ("Can not delete null file");
    final boolean ok = toDelete.delete ();
    if (!ok)
      throw new IOException ("Failed to delete file: " + toDelete.getAbsolutePath ());
  }

  /**
   * Try to create the given directory and all parent directories that are
   * needed.
   * 
   * @param newDir
   *        the directory to create
   * @throws IOException
   *         if directory could not be created
   */
  public static void mkdirs (final File newDir) throws IOException
  {
    if (newDir == null)
      throw new NullPointerException ("can not create null directory");
    final boolean ok = newDir.mkdirs ();
    if (!ok)
      throw new IOException ("Failed to create directory: " + newDir.getAbsolutePath ());
  }
}
