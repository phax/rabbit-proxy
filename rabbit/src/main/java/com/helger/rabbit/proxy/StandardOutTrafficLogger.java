package com.helger.rabbit.proxy;

import com.helger.commons.url.SMap;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.util.TrafficLogger;

/**
 * A simple ClientTrafficLogger that just writes simple network usage to
 * standard out.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class StandardOutTrafficLogger implements ClientTrafficLogger
{

  public void logTraffic (final String user,
                          final HttpHeader request,
                          final TrafficLogger client,
                          final TrafficLogger network,
                          final TrafficLogger cache,
                          final TrafficLogger proxy)
  {
    System.out.println ("user: " +
                        user +
                        ", url: " +
                        request.getRequestURI () +
                        ", client read: " +
                        client.read () +
                        ", client write: " +
                        client.write () +
                        ", network read: " +
                        network.read () +
                        ", network write: " +
                        network.write ());
  }

  public void setup (final SMap properties, final HttpProxy proxy)
  {
    // empty
  }
}
