package com.helger.rabbit.proxy;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.rabbit.filter.IIPAccessFilter;
import com.helger.rabbit.util.Config;

/**
 * An access controller based on socket channels.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class SocketAccessController
{
  private static final Logger LOGGER = LoggerFactory.getLogger (SocketAccessController.class);

  /** the filters, a List of classes (in given order) */
  private List <IIPAccessFilter> accessfilters = new ArrayList <> ();

  /**
   * Create a new SocketAccessController that will use a list of internal
   * filters.
   *
   * @param filters
   *        a comma separated list of filters to use
   * @param config
   *        the Config to get the internal filters properties from
   * @param proxy
   *        the HttpProxy using this access controller
   */
  public SocketAccessController (final String filters, final Config config, final HttpProxy proxy)
  {
    accessfilters = new ArrayList <> ();
    loadAccessFilters (filters, accessfilters, config, proxy);
  }

  private void loadAccessFilters (final String filters,
                                  final List <IIPAccessFilter> accessfilters,
                                  final Config config,
                                  final HttpProxy proxy)
  {
    final StringTokenizer st = new StringTokenizer (filters, ",");
    String classname = "";
    while (st.hasMoreElements ())
    {
      try
      {
        classname = st.nextToken ().trim ();
        final Class <? extends IIPAccessFilter> cls = proxy.load3rdPartyClass (classname, IIPAccessFilter.class);
        final IIPAccessFilter ipf = cls.newInstance ();
        ipf.setup (config.getProperties (classname));
        accessfilters.add (ipf);
      }
      catch (final ClassNotFoundException ex)
      {
        LOGGER.warn ("Could not load class: '" + classname + "'", ex);
      }
      catch (final InstantiationException ex)
      {
        LOGGER.warn ("Could not instansiate: '" + classname + "'", ex);
      }
      catch (final IllegalAccessException ex)
      {
        LOGGER.warn ("Could not instansiate: '" + classname + "'", ex);
      }
    }
  }

  private List <IIPAccessFilter> getAccessFilters ()
  {
    return Collections.unmodifiableList (accessfilters);
  }

  /**
   * Check if the given channel is allowed access.
   *
   * @param sc
   *        the channel to check
   * @return true if the channel is allowed access, false otherwise
   */
  public boolean checkAccess (final SocketChannel sc)
  {
    for (final IIPAccessFilter filter : getAccessFilters ())
    {
      if (filter.doIPFiltering (sc))
        return true;
    }
    return false;
  }
}
