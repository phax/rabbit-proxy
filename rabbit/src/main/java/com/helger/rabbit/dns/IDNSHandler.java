package com.helger.rabbit.dns;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;

import com.helger.commons.collection.attr.StringMap;

/**
 * A DNS handler.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public interface IDNSHandler
{
  /**
   * Do any neccessary setup.
   *
   * @param config
   *        the properties for this handler
   */
  void setup (StringMap config);

  /**
   * Look up an internet address.
   *
   * @param url
   *        the url to get the host from
   * @return the InetAddress of the url
   * @throws UnknownHostException
   *         if the lookup fails
   */
  InetAddress getInetAddress (URL url) throws UnknownHostException;

  /**
   * Look up an internet address.
   *
   * @param host
   *        the name of the host to lookup
   * @return the InetAddress for the given host
   * @throws UnknownHostException
   *         if the lookup fails
   */
  InetAddress getInetAddress (String host) throws UnknownHostException;
}
