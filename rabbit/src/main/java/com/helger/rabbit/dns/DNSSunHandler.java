package com.helger.rabbit.dns;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;

import com.helger.commons.collection.attr.StringMap;

/**
 * A DNS handler using the standard java packages.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class DNSSunHandler implements IDNSHandler
{
  public void setup (final StringMap config)
  {
    // empty.
  }

  public InetAddress getInetAddress (final URL url) throws UnknownHostException
  {
    return InetAddress.getByName (url.getHost ());
  }

  public InetAddress getInetAddress (final String host) throws UnknownHostException
  {
    return InetAddress.getByName (host);
  }
}
