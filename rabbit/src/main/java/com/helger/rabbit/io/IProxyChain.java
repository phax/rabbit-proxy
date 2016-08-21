package com.helger.rabbit.io;

/**
 * Interface to be able to handle different connection strategies depending on
 * where we want to connect. This makes it possible to setup a system where we
 * connect directly to computers on the local network, but connect through the
 * company proxy when trying to access the internet.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public interface IProxyChain
{
  /**
   * Get the Resolver to use for the given url
   * 
   * @param url
   *        the address to connect to.
   * @return the Resolver to use
   */
  Resolver getResolver (String url);
}
