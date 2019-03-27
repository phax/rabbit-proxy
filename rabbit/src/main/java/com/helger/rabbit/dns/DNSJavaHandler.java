package com.helger.rabbit.dns;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.Address;
import org.xbill.DNS.Cache;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Lookup;

import com.helger.commons.collection.attr.StringMap;

/**
 * A DNS handler using the dnsjava packages
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class DNSJavaHandler implements IDNSHandler
{
  private static final Logger LOGGER = LoggerFactory.getLogger (DNSJavaHandler.class);

  /** Do any neccessary setup. */
  public void setup (StringMap config)
  {
    if (config == null)
      config = new StringMap ();
    final String ct = config.getOrDefault ("dnscachetime", "8").trim ();
    int time = 8 * 3600;
    try
    {
      time = Integer.parseInt (ct) * 3600;
    }
    catch (final NumberFormatException e)
    {
      LOGGER.warn ("bad number for dnscachetime: '" + ct + "', using: " + (time / 3600) + " hours");
    }
    final Cache dnsCache = Lookup.getDefaultCache (DClass.IN);
    dnsCache.setMaxCache (time);
    dnsCache.setMaxNCache (time);
  }

  /** Look up an internet address. */
  public InetAddress getInetAddress (final URL url) throws UnknownHostException
  {
    return getInetAddress (url.getHost ());
  }

  public InetAddress getInetAddress (final String host) throws UnknownHostException
  {
    return Address.getByName (host);
  }
}
