package com.helger.rabbit.filter;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.helger.commons.url.SMap;
import com.helger.rabbit.filter.authenticate.AuthUserInfo;
import com.helger.rabbit.filter.authenticate.Authenticator;
import com.helger.rabbit.filter.authenticate.PlainFileAuthenticator;
import com.helger.rabbit.filter.authenticate.SQLAuthenticator;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.proxy.HttpGenerator;
import com.helger.rabbit.proxy.HttpProxy;

/**
 * This is a filter that requires users to use proxy-authentication.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ProxyAuth implements HttpFilter
{
  private final Logger logger = Logger.getLogger (getClass ().getName ());
  private Authenticator authenticator;
  private int cacheTime;
  private boolean oneIpOnly;
  private Pattern noAuthPattern;

  /** Username to user info */
  private final Map <String, AuthUserInfo> cache = new ConcurrentHashMap<> ();

  /**
   * Check that the user has been authenticated..
   *
   * @param socket
   *        the SocketChannel that made the request.
   * @param header
   *        the actual request made.
   * @param con
   *        the Connection handling the request.
   * @return null if everything is fine or a HttpHeader describing the error (a
   *         407).
   */
  public HttpHeader doHttpInFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    if (con.getMeta ())
      return null;
    if (noAuthRequired (header))
      return null;
    return handleAuthentication (header, con);
  }

  private HttpHeader handleAuthentication (final HttpHeader header, final Connection con)
  {
    final String username = con.getUserName ();
    final String token = authenticator.getToken (header, con);
    if (username == null || token == null)
      return getError (header, con);
    final SocketChannel channel = con.getChannel ();
    final AuthUserInfo ce = cache.get (username);
    if (hasValidCache (token, ce))
    {
      if (oneIpOnly)
      {
        final InetAddress ia = channel.socket ().getInetAddress ();
        if (!ce.correctSocketAddress (ia))
          return getError (header, con);
      }
      return null;
    }

    if (!authenticator.authenticate (username, token))
      return getError (header, con);
    if (cacheTime > 0)
      storeInCache (username, token, channel);
    return null;
  }

  private boolean noAuthRequired (final HttpHeader header)
  {
    if (noAuthPattern == null)
      return false;
    return noAuthPattern.matcher (header.getRequestURI ()).find ();
  }

  private boolean hasValidCache (final String token, final AuthUserInfo ce)
  {
    return ce != null && ce.stillValid () && ce.correctToken (token);
  }

  private void storeInCache (final String user, final String token, final SocketChannel channel)
  {
    final long timeout = System.currentTimeMillis () + 60000L * cacheTime;
    final InetAddress sa = channel.socket ().getInetAddress ();
    final AuthUserInfo ce = new AuthUserInfo (token, timeout, sa);
    cache.put (user, ce);
  }

  private HttpHeader getError (final HttpHeader header, final Connection con)
  {
    final HttpGenerator hg = con.getHttpGenerator ();
    String url = header.getRequestURI ();
    // connect is just "foo.bar.com:443"
    if (header.getMethod ().equals ("CONNECT"))
      url = "https://" + url;
    try
    {
      return hg.get407 (new URL (url), "internet");
    }
    catch (final MalformedURLException e)
    {
      logger.log (Level.WARNING, "Bad url: " + header.getRequestURI (), e);
      return hg.get400 (e);
    }
  }

  public HttpHeader doHttpOutFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    return null;
  }

  public HttpHeader doConnectFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    return handleAuthentication (header, con);
  }

  /**
   * Setup this class with the given properties.
   *
   * @param properties
   *        the new configuration of this class.
   */
  public void setup (final SMap properties, final HttpProxy proxy)
  {
    final String ct = properties.getOrDefault ("cachetime", "0");
    cacheTime = Integer.parseInt (ct);
    final String ra = properties.getOrDefault ("one_ip_only", "true");
    oneIpOnly = Boolean.parseBoolean (ra);
    final String allow = properties.get ("allow_without_auth");
    if (allow != null)
      noAuthPattern = Pattern.compile (allow);
    final String authType = properties.getOrDefault ("authenticator", "plain");
    if ("plain".equalsIgnoreCase (authType))
    {
      authenticator = new PlainFileAuthenticator (properties);
    }
    else
      if ("sql".equalsIgnoreCase (authType))
      {
        authenticator = new SQLAuthenticator (properties);
      }
      else
      {
        try
        {
          final Class <? extends Authenticator> clz = proxy.load3rdPartyClass (authType, Authenticator.class);
          authenticator = clz.newInstance ();
        }
        catch (final ClassNotFoundException e)
        {
          logger.warning ("Failed to find class: '" + authType + "'");
        }
        catch (final InstantiationException e)
        {
          logger.warning ("Failed to instantiate: '" + authType + "'");
        }
        catch (final IllegalAccessException e)
        {
          logger.warning ("Failed to instantiate: '" + authType + "': " + e);
        }
      }
  }
}
