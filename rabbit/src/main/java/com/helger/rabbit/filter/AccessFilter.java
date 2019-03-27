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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.annotation.ReturnsMutableObject;
import com.helger.commons.collection.attr.StringMap;
import com.helger.rabbit.util.IPAccess;

/**
 * This is a class that filters access based on ip address.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class AccessFilter implements IIPAccessFilter
{
  private static final Logger LOGGER = LoggerFactory.getLogger (AccessFilter.class);

  private List <IPAccess> allowed = new ArrayList <> ();
  private List <IPAccess> denied = new ArrayList <> ();
  private static final String DEFAULTCONFIG = "conf/access";

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
  public void setup (final StringMap properties)
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
  private void loadAccess (final String filename)
  {
    final String sRealFilename = filename.replace ('/', File.separatorChar);

    try (final FileInputStream is = new FileInputStream (sRealFilename);
        final Reader r = new InputStreamReader (is, StandardCharsets.UTF_8))
    {
      loadAccess (r);
    }
    catch (final IOException e)
    {
      LOGGER.warn ("Accessfile '" + sRealFilename + "' not found: no one allowed", e);
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
    final List <IPAccess> allowed = new ArrayList <> ();
    final List <IPAccess> denied = new ArrayList <> ();
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
          LOGGER.warn ("Bad line in accessconf:" + br.getLineNumber ());
          continue;
        }
        final String low = st.nextToken ();
        final InetAddress lowip = _getInetAddress (low, LOGGER, br);
        final String high = st.nextToken ();
        final InetAddress highip = _getInetAddress (high, LOGGER, br);

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

  private static InetAddress _getInetAddress (final String text, final Logger LOGGER, final LineNumberReader br)
  {
    InetAddress ip = null;
    try
    {
      ip = InetAddress.getByName (text);
    }
    catch (final UnknownHostException e)
    {
      LOGGER.warn ("Bad host: " + text + " at line:" + br.getLineNumber ());
    }
    return ip;
  }

  /**
   * Get the list of allowed ips
   */
  @Nonnull
  @ReturnsMutableObject
  public List <IPAccess> getAllowList ()
  {
    return allowed;
  }

  /**
   * Get the list of denied ips
   */
  @Nonnull
  @ReturnsMutableObject
  public List <IPAccess> getDenyList ()
  {
    return denied;
  }
}
