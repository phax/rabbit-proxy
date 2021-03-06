package com.helger.rabbit.httpio;

import com.helger.rabbit.dns.IDNSHandler;
import com.helger.rabbit.io.IProxyChain;
import com.helger.rabbit.io.Resolver;
import com.helger.rnio.INioHandler;

/**
 * A default implementation of a ProxyChain that always return the same
 * SimpleResolver.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class SimpleProxyChain implements IProxyChain
{
  private final Resolver resolver;

  /**
   * Create a new Proxy chain that always uses direct connections.
   * 
   * @param nio
   *        the NioHandler to use for running background tasks
   * @param dnsHandler
   *        the DNSHandler to use for DNS lookups
   */
  public SimpleProxyChain (final INioHandler nio, final IDNSHandler dnsHandler)
  {
    resolver = new SimpleResolver (nio, dnsHandler);
  }

  public Resolver getResolver (final String url)
  {
    return resolver;
  }
}
