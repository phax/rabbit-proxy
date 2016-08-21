package com.helger.rabbit.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A class that starts up proxies.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ProxyStarter
{

  private static final String DEFAULT_CONFIG = "conf/rabbit.conf";

  /**
   * Create the ProxyStarter and let it parse the command line arguments.
   *
   * @param args
   *        the command line arguments
   */
  public static void main (final String [] args)
  {
    final ProxyStarter ps = new ProxyStarter ();
    ps.start (args);
  }

  /**
   * print out the helptext to the user.
   */
  private void printHelp ()
  {
    try
    {
      final byte [] b = new byte [4096];
      int i;
      try (final InputStream f = ProxyStarter.class.getResourceAsStream ("/Help.txt"))
      {
        while ((i = f.read (b)) > 0)
          System.out.write (b, 0, i);
      }
    }
    catch (final IOException e)
    {
      System.err.println ("Could not read help text: " + e);
    }
  }

  private void start (final String [] args)
  {
    final List <String> configs = new ArrayList<> ();
    for (int i = 0; i < args.length; i++)
    {
      if (args[i].equals ("-?") || args[i].equals ("-h") || args[i].equals ("--help"))
      {
        printHelp ();
        return;
      }
      else
        if (args[i].equals ("-f") || args[i].equals ("--file"))
        {
          i++;
          if (args.length > i)
          {
            configs.add (args[i]);
          }
          else
          {
            System.err.println ("Missing config file on command line");
            return;
          }
        }
        else
          if (args[i].equals ("-v") || args[i].equals ("--version"))
          {
            System.out.println (HttpProxy.VERSION);
            return;
          }
    }
    if (configs.size () == 0)
      configs.add (DEFAULT_CONFIG);
    for (final String conf : configs)
      startProxy (conf);
  }

  private void startProxy (final String conf)
  {
    try
    {
      final HttpProxy p = new HttpProxy ();
      p.setConfig (conf);
      p.start ();
    }
    catch (final IOException e)
    {
      System.err.println ("failed to configure proxy, ignoring: " + e);
      e.printStackTrace ();
    }
  }
}
