package com.helger.rabbit.proxy;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ProxyClassLoaderHelper
{
  private static final Logger LOGGER = LoggerFactory.getLogger (ProxyClassLoaderHelper.class);

  public ClassLoader get3rdPartyClassLoader (final String dirLine)
  {
    final String [] dirs = dirLine.split (File.pathSeparator);
    final List <URL> urls = new ArrayList <> ();
    final FileFilter jarFilter = new JarFilter ();
    for (final String dir : dirs)
    {
      try
      {
        final File d = new File (dir);
        if (!d.exists ())
        {
          LOGGER.warn (d.getCanonicalPath () + " does not exist, skipping it from " + "3:rd party list");
          continue;
        }
        if (!d.isDirectory ())
        {
          LOGGER.warn (d.getCanonicalPath () + " is not a directory, skipping it from " + "3:rd party list");
          continue;
        }
        for (final File f : d.listFiles (jarFilter))
          urls.add (f.toURI ().toURL ());
      }
      catch (final IOException e)
      {
        LOGGER.error ("Failed to setup classloading", e);
      }
    }
    final URL [] urlArray = urls.toArray (new URL [urls.size ()]);
    return new URLClassLoader (urlArray);
  }

  private static class JarFilter implements FileFilter
  {
    public boolean accept (final File path)
    {
      final String name = path.getName ();
      return name.toLowerCase ().endsWith (".jar");
    }
  }
}
