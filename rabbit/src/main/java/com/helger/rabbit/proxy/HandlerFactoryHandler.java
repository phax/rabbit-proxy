package com.helger.rabbit.proxy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.collection.attr.StringMap;
import com.helger.rabbit.handler.IHandlerFactory;
import com.helger.rabbit.util.Config;

/**
 * A class to handle mime type handler factories.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class HandlerFactoryHandler
{
  private static final Logger LOGGER = LoggerFactory.getLogger (HandlerFactoryHandler.class);

  private final List <HandlerInfo> handlers;
  private final List <HandlerInfo> cacheHandlers;

  public HandlerFactoryHandler (final StringMap handlersProps,
                                final StringMap cacheHandlersProps,
                                final Config config,
                                final HttpProxy proxy)
  {
    handlers = loadHandlers (handlersProps, config, proxy);
    cacheHandlers = loadHandlers (cacheHandlersProps, config, proxy);
  }

  private static class HandlerInfo
  {
    public final String mime;
    public final Pattern pattern;
    public final IHandlerFactory factory;

    public HandlerInfo (final String mime, final IHandlerFactory factory)
    {
      this.mime = mime;
      this.pattern = Pattern.compile (mime, Pattern.CASE_INSENSITIVE);
      this.factory = factory;
    }

    public boolean accept (final String mime)
    {
      final Matcher m = pattern.matcher (mime);
      return m.matches ();
    }

    @Override
    public String toString ()
    {
      return getClass ().getSimpleName () + "{" + mime + ", " + factory + "}";
    }
  }

  /**
   * Load a set of handlers.
   *
   * @param handlersProps
   *        the properties for the handlers
   * @param config
   *        the Config to get handler properties from
   * @param proxy
   *        the HttpProxy loading the Handler
   * @return a Map with mimetypes as keys and Handlers as values.
   */
  protected List <HandlerInfo> loadHandlers (final StringMap handlersProps, final Config config, final HttpProxy proxy)
  {
    final List <HandlerInfo> hhandlers = new ArrayList <> ();
    if (handlersProps == null)
      return hhandlers;
    for (final String handler : handlersProps.keySet ())
    {
      IHandlerFactory hf;
      final String id = handlersProps.get (handler).trim ();
      hf = setupHandler (id, config, handler, proxy);
      hhandlers.add (new HandlerInfo (handler, hf));
    }
    return hhandlers;
  }

  private IHandlerFactory setupHandler (final String id,
                                        final Config config,
                                        final String handler,
                                        final HttpProxy proxy)
  {
    String className = id;
    IHandlerFactory hf = null;
    try
    {
      final int i = id.indexOf ('*');
      if (i >= 0)
        className = id.substring (0, i);
      final Class <? extends IHandlerFactory> cls = proxy.load3rdPartyClass (className, IHandlerFactory.class);
      hf = cls.newInstance ();
      hf.setup (config.getProperties (id), proxy);
    }
    catch (final ClassNotFoundException ex)
    {
      LOGGER.warn ("Could not load class: '" + className + "' for handlerfactory '" + handler + "'", ex);
    }
    catch (final InstantiationException ie)
    {
      LOGGER.warn ("Could not instanciate factory class: '" + className + "' for handler '" + handler + "'", ie);
    }
    catch (final IllegalAccessException iae)
    {
      LOGGER.warn ("Could not instanciate factory class: '" + className + "' for handler '" + handler + "'", iae);
    }
    return hf;
  }

  IHandlerFactory getHandlerFactory (final String mime)
  {
    for (final HandlerInfo hi : handlers)
    {
      if (hi.accept (mime))
        return hi.factory;
    }
    return null;
  }

  IHandlerFactory getCacheHandlerFactory (final String mime)
  {
    for (final HandlerInfo hi : cacheHandlers)
    {
      if (hi.accept (mime))
        return hi.factory;
    }
    return null;
  }
}
