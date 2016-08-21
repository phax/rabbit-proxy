package com.helger.rabbit.filter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.helger.commons.io.stream.StreamHelper;
import com.helger.commons.url.SMap;
import com.helger.rabbit.util.IPAccess;

/**
 * This is a class that filters access based on ip address.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class AccessFilter implements IIPAccessFilter
{
  private List <IPAccess> allowed = new ArrayList<> ();
  private List <IPAccess> denied = new ArrayList<> ();
  private static final String DEFAULTCONFIG = "conf/access";

  private final Logger logger = Logger.getLogger (getClass ().getName ());

  /**
   * Filter based on a socket.
   *
   * @param s
   *        the SocketChannel to check.
   * @return true if the Socket should be allowed, false otherwise.
   */
  public boolean doIPFiltering (final SocketChannel s)
  {
    int l = denied.size ();
    for (int i = 0; i < l; i++)
      if (denied.get (i).inrange (s.socket ().getInetAddress ()))
        return false;

    l = allowed.size ();
    for (int i = 0; i < l; i++)
      if (allowed.get (i).inrange (s.socket ().getInetAddress ()))
        return true;
    return false;
  }

  /**
   * Setup this class.
   *
   * @param properties
   *        the Properties to get the settings from.
   */
  public void setup (final SMap properties)
  {
    final String file = properties.getOrDefault ("accessfile", DEFAULTCONFIG);
    loadAccess (file);
  }

  /**
   * Read the data (accesslists) from a file.
   *
   * @param filename
   *        the name of the file to read from.
   */
  private void loadAccess (String filename)
  {
    filename = filename.replace ('/', File.separatorChar);

    try (final FileInputStream is = new FileInputStream (filename);
         final Reader r = new InputStreamReader (is, "UTF-8"))
    {
      try
      {
        loadAccess (r);
      }
      finally
      {
        StreamHelper.close (r);
      }
    }
    catch (final IOException e)
    {
      logger.log (Level.WARNING, "Accessfile '" + filename + "' not found: no one allowed", e);
    }
  }

  /**
   * Loads in the accessess allowed from the given Reader
   *
   * @param r
   *        the Reader were data is available
   */
  public void loadAccess (final Reader r) throws IOException
  {
    final List <IPAccess> allowed = new ArrayList<> ();
    final List <IPAccess> denied = new ArrayList<> ();
    try (final LineNumberReader br = new LineNumberReader (r))
    {
      String line;
      while ((line = br.readLine ()) != null)
      {
        // remove comments....
        final int index = line.indexOf ('#');
        if (index >= 0)
          line = line.substring (0, index);
        line = line.trim ();
        if (line.equals (""))
          continue;
        boolean accept = true;
        if (line.charAt (0) == '-')
        {
          accept = false;
          line = line.substring (1);
        }
        final StringTokenizer st = new StringTokenizer (line);
        if (st.countTokens () != 2)
        {
          logger.warning ("Bad line in accessconf:" + br.getLineNumber ());
          continue;
        }
        final String low = st.nextToken ();
        final InetAddress lowip = getInetAddress (low, logger, br);
        final String high = st.nextToken ();
        final InetAddress highip = getInetAddress (high, logger, br);

        if (lowip != null && highip != null)
        {
          if (accept)
            allowed.add (new IPAccess (lowip, highip));
          else
            denied.add (new IPAccess (lowip, highip));
        }
      }
    }
    this.allowed = allowed;
    this.denied = denied;
  }

  private InetAddress getInetAddress (final String text, final Logger logger, final LineNumberReader br)
  {
    InetAddress ip = null;
    try
    {
      ip = InetAddress.getByName (text);
    }
    catch (final UnknownHostException e)
    {
      logger.warning ("Bad host: " + text + " at line:" + br.getLineNumber ());
    }
    return ip;
  }

  /**
   * Get the list of allowed ips
   */
  public List <IPAccess> getAllowList ()
  {
    return allowed;
  }

  /**
   * Get the list of denied ips
   */
  public List <IPAccess> getDenyList ()
  {
    return denied;
  }
}
