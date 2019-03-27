package com.helger.rabbit.httpio;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.slf4j.Logger;

import com.helger.commons.collection.attr.StringMap;
import com.helger.rabbit.dns.IDNSHandler;
import com.helger.rabbit.io.IProxyChain;
import com.helger.rabbit.io.ProxyChainFactory;
import com.helger.rnio.INioHandler;

/**
 * A factory that creates InOutProxyChain:s.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class InOutProxyChainFactory implements ProxyChainFactory
{
  public IProxyChain getProxyChain (final StringMap props,
                                    final INioHandler nio,
                                    final IDNSHandler dnsHandler,
                                    final Logger aLogger)
  {
    final String insideMatch = props.get ("inside_match");
    final String pname = props.getOrDefault ("proxyhost", "").trim ();
    final String pport = props.getOrDefault ("proxyport", "").trim ();
    final String pauth = props.get ("proxyauth");

    try
    {
      final InetAddress proxy = dnsHandler.getInetAddress (pname);
      try
      {
        final int port = Integer.parseInt (pport);
        return new InOutProxyChain (insideMatch, nio, dnsHandler, proxy, port, pauth);
      }
      catch (final NumberFormatException e)
      {
        aLogger.error ("Strange proxyport: '" + pport + "', will not chain");
      }
    }
    catch (final UnknownHostException e)
    {
      aLogger.error ("Unknown proxyhost: '" + pname + "', will not chain");
    }
    return null;
  }
}
