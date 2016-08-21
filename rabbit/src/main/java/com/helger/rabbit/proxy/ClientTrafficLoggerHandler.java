package com.helger.rabbit.proxy;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.util.Config;
import com.helger.rabbit.util.TrafficLogger;

/**
 * A class to handle the client traffic loggers.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class ClientTrafficLoggerHandler
{
  private final List <ClientTrafficLogger> loggers;

  public ClientTrafficLoggerHandler (final Config config, final HttpProxy proxy)
  {
    final Logger log = Logger.getLogger (getClass ().getName ());
    final String filters = config.getProperty ("logging", "traffic_loggers", "");
    final String [] classNames = filters.split (",");
    loggers = new ArrayList <> (classNames.length);
    for (String clz : classNames)
    {
      clz = clz.trim ();
      if (clz.equals (""))
        continue;
      try
      {
        final Class <? extends ClientTrafficLogger> cls = proxy.load3rdPartyClass (clz, ClientTrafficLogger.class);
        final ClientTrafficLogger ctl = cls.newInstance ();
        ctl.setup (config.getProperties (clz), proxy);
        loggers.add (ctl);
      }
      catch (final ClassNotFoundException ex)
      {
        log.log (Level.WARNING, "Could not load traffic logger class: '" + clz + "'", ex);
      }
      catch (final InstantiationException ex)
      {
        log.log (Level.WARNING, "Could not instansiate traffic logger: '" + clz + "'", ex);
      }
      catch (final IllegalAccessException ex)
      {
        log.log (Level.WARNING, "Could not access traffic logger: '" + clz + "'", ex);
      }
    }
  }

  public void logTraffic (final String user,
                          final HttpHeader request,
                          final TrafficLogger client,
                          final TrafficLogger network,
                          final TrafficLogger cache,
                          final TrafficLogger proxy)
  {
    for (final ClientTrafficLogger ctl : loggers)
    {
      ctl.logTraffic (user, request, client, network, cache, proxy);
    }
  }
}
