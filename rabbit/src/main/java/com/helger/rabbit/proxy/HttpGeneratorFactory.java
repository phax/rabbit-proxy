package com.helger.rabbit.proxy;

import com.helger.commons.collection.attr.StringMap;

/**
 * The factory for the HttpGenerators used by rabbit.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public interface HttpGeneratorFactory
{
  /**
   * Create a HttpGenerator for the given connection
   *
   * @param serverIdentity
   *        the identity of the server
   * @param connection
   *        the Connection handling the request
   * @return a HttpGenerator
   */
  HttpGenerator create (String serverIdentity, Connection connection);

  /**
   * Setup this factory.
   *
   * @param props
   *        the config parameters
   */
  void setup (StringMap props);
}
