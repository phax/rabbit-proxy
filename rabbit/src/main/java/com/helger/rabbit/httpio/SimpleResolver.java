package com.helger.rabbit.httpio;

import java.net.URL;

import com.helger.rabbit.dns.DNSHandler;
import com.helger.rabbit.io.InetAddressListener;
import com.helger.rabbit.io.Resolver;
import com.helger.rnio.NioHandler;
import com.helger.rnio.impl.DefaultTaskIdentifier;

/**
 * A simple resolver that uses the given dns handler.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class SimpleResolver implements Resolver
{
  private final DNSHandler dnsHandler;
  private final NioHandler nio;

  /**
   * Create a new Resolver that does normal DNS lookups.
   * 
   * @param nio
   *        the NioHandler to use for running background tasks
   * @param dnsHandler
   *        the DNSHandler to use for the DNS lookup
   */
  public SimpleResolver (final NioHandler nio, final DNSHandler dnsHandler)
  {
    this.dnsHandler = dnsHandler;
    this.nio = nio;
  }

  public void getInetAddress (final URL url, final InetAddressListener listener)
  {
    final String groupId = getClass ().getSimpleName ();
    nio.runThreadTask (new ResolvRunner (dnsHandler, url, listener),
                       new DefaultTaskIdentifier (groupId, url.toString ()));
  }

  public int getConnectPort (final int port)
  {
    return port;
  }

  public boolean isProxyConnected ()
  {
    return false;
  }

  public String getProxyAuthString ()
  {
    return null;
  }
}
