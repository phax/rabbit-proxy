package com.helger.rabbit.httpio;

import java.net.InetAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.helger.rabbit.dns.DNSHandler;
import com.helger.rabbit.io.IProxyChain;
import com.helger.rabbit.io.Resolver;
import com.helger.rnio.NioHandler;

/**
 * A proxy chain that connects directly to the local network and uses a chained
 * proxy to connect to the outside.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class InOutProxyChain implements IProxyChain
{
  private final Pattern insidePattern;
  private final Resolver directResolver;
  private final Resolver proxiedResolver;

  public InOutProxyChain (final String insideMatch,
                          final NioHandler nio,
                          final DNSHandler dnsHandler,
                          final InetAddress proxy,
                          final int port,
                          final String proxyAuth)
  {
    insidePattern = Pattern.compile (insideMatch);
    directResolver = new SimpleResolver (nio, dnsHandler);
    proxiedResolver = new ProxyResolver (proxy, port, proxyAuth);
  }

  public Resolver getResolver (final String url)
  {
    final Matcher m = insidePattern.matcher (url);
    if (m.find ())
      return directResolver;
    return proxiedResolver;
  }
}
