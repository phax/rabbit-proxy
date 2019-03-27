package com.helger.rabbit.proxy;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.util.Config;
import com.helger.rabbit.util.ITrafficLogger;

/**
 * A class to handle the client traffic loggers.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class ClientTrafficLoggerHandler
{
  private static final Logger LOGGER = LoggerFactory.getLogger (ClientTrafficLoggerHandler.class);

  private final List <ClientTrafficLogger> loggers;

  public ClientTrafficLoggerHandler (final Config config, final HttpProxy proxy)
  {
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
        LOGGER.warn ("Could not load traffic LOGGER class: '" + clz + "'", ex);
      }
      catch (final InstantiationException ex)
      {
        LOGGER.warn ("Could not instansiate traffic LOGGER: '" + clz + "'", ex);
      }
      catch (final IllegalAccessException ex)
      {
        LOGGER.warn ("Could not access traffic LOGGER: '" + clz + "'", ex);
      }
    }
  }

  public void logTraffic (final String user,
                          final HttpHeader request,
                          final ITrafficLogger client,
                          final ITrafficLogger network,
                          final ITrafficLogger cache,
                          final ITrafficLogger proxy)
  {
    for (final ClientTrafficLogger ctl : loggers)
    {
      ctl.logTraffic (user, request, client, network, cache, proxy);
    }
  }
}
