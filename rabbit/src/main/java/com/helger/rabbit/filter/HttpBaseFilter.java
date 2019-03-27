package com.helger.rabbit.filter;

import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.base64.Base64;
import com.helger.commons.collection.attr.StringMap;
import com.helger.commons.string.StringHelper;
import com.helger.http.basicauth.BasicAuthClientCredentials;
import com.helger.http.basicauth.HttpBasicAuth;
import com.helger.rabbit.http.HttpDateParser;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.http.HttpHeaderWithContent;
import com.helger.rabbit.io.IProxyChain;
import com.helger.rabbit.io.Resolver;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.proxy.HttpProxy;
import com.helger.rabbit.util.SimpleUserHandler;

/**
 * This is a class that filter http headers to make them nice. This filter sets
 * up username and password if supplied and also sets up keepalive.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class HttpBaseFilter implements IHttpFilter
{
  /** Constant for requests that want an unfiltered resource. */
  public static final String NOPROXY = "http://noproxy.";
  private static final BigInteger ZERO = BigInteger.ZERO;
  private static final BigInteger ONE = BigInteger.ONE;
  private static final Logger LOGGER = LoggerFactory.getLogger (HttpBaseFilter.class);

  private final List <String> removes = new ArrayList <> ();
  private boolean cookieId = false;
  private final SimpleUserHandler userHandler = new SimpleUserHandler ();

  /**
   * We got a proxy authentication, handle it...
   *
   * @param uap
   *        the authentication string.
   * @param con
   *        the Connection.
   */
  private void handleProxyAuthentication (final String uap, final Connection con)
  {
    // guess we should handle digest here also.. :-/
    if (uap.startsWith ("Basic "))
    {
      final BasicAuthClientCredentials aCred = HttpBasicAuth.getBasicAuthClientCredentials (uap);
      if (aCred != null)
      {
        con.setUserName (aCred.getUserName ());
        con.setPassword (aCred.getPassword ());
      }
    }
  }

  /**
   * Handle the authentications. If we have a proxy-authentication we set the
   * connections username and password. We also rewrite authentications in the
   * URL to a standard header, since java does not handle them.
   *
   * @param header
   *        the Request.
   * @param con
   *        the Connection.
   */
  private void handleAuthentications (final HttpHeader header, final Connection con)
  {
    handleAuthentications (header, con, "Proxy-Authorization");
  }

  /**
   * Handle the authentications. If we have a authentication token of the given
   * type we set the connections username and password. We also rewrite
   * authentications in the URL to a standard header, since java does not handle
   * them.
   *
   * @param header
   *        the Request.
   * @param con
   *        the Connection.
   * @param type
   *        the authentication type "Proxy-Authentication" or "Authorization"
   */
  private void handleAuthentications (final HttpHeader header, final Connection con, final String type)
  {
    final String uap = header.getHeader (type);
    if (uap != null)
      handleProxyAuthentication (uap, con);

    /*
     * Java URL:s doesn't handle user/pass in the URL as in rfc1738:
     * //<user>:<password>@<host>:<port>/<url-path> Convert these to an
     * Authorization header and remove from URI.
     */
    final String requestURI = header.getRequestURI ();

    int s3, s4, s5;
    if ((s3 = requestURI.indexOf ("//")) >= 0 &&
        (s4 = requestURI.indexOf ('/', s3 + 2)) >= 0 &&
        (s5 = requestURI.indexOf ('@', s3 + 2)) >= 0 &&
        s5 < s4)
    {
      final String userPass = requestURI.substring (s3 + 2, s5);
      header.setHeader ("Authorization", "Basic " + Base64.safeEncode (userPass, StandardCharsets.UTF_8));

      header.setRequestURI (requestURI.substring (0, s3 + 2) + requestURI.substring (s5 + 1));
    }
  }

  /**
   * Check if this is a noproxy request, and if so handle it.
   *
   * @param requri
   *        the requested resource.
   * @param header
   *        the actual request.
   * @param con
   *        the Connection.
   * @return the new request URI
   */
  private String handleNoProxyRequest (final String requri, final HttpHeader header, final Connection con)
  {
    final String sRealRequri = "http://" + requri.substring (NOPROXY.length ());
    header.setRequestURI (sRealRequri);
    con.setMayUseCache (false);
    con.setMayCache (false);
    con.setFilteringNotAllowed ();
    return sRealRequri;
  }

  /**
   * Check that the requested URL is valid and if it is a meta request.
   *
   * @param requri
   *        the requested resource.
   * @param header
   *        the actual request.
   * @param con
   *        the Connection.
   * @return null if the request is allowed or an error response header
   */
  private HttpHeader handleURLSetup (final String requri, final HttpHeader header, final Connection con)
  {
    try
    {
      // is this request to our self?
      final HttpProxy proxy = con.getProxy ();
      boolean proxyRequest = true;
      String sRealRequri = requri;
      if (StringHelper.startsWith (sRealRequri, '/'))
      {
        proxyRequest = false;
        handleAuthentications (header, con, "Authorization");
        sRealRequri = "http://" + proxy.getHost ().getHostName () + ":" + proxy.getPort () + sRealRequri;
        header.setRequestURI (sRealRequri);
      }
      final URL url = new URL (sRealRequri);
      header.setHeader ("Host", url.getPort () > -1 ? url.getHost () + ":" + url.getPort () : url.getHost ());
      final int urlport = url.getPort ();
      // This could give a DNS-error if no DNS is available.
      // And since we have not decided if we should proxy it
      // up the chain yet, do string comparison..
      // InetAddress urlhost = InetAddress.getByName (url.getHost ());
      final String uhost = url.getHost ();
      if (proxy.isSelf (uhost, urlport))
      {
        con.setMayUseCache (false);
        con.setMayCache (false);
        con.setFilteringNotAllowed ();
        if (!userHandler.isValidUser (con.getUserName (), con.getPassword ()) && !isPublic (url))
        {
          HttpHeader err;
          final String realm = uhost + ":" + urlport;
          if (proxyRequest)
            err = con.getHttpGenerator ().get407 (url, realm);

          else
            err = con.getHttpGenerator ().get401 (url, realm);
          return err;
        }
        con.setMeta ();
      }
    }
    catch (final MalformedURLException e)
    {
      return con.getHttpGenerator ().get400 (e);
    }
    return null;
  }

  /**
   * Remove all "Connection" tokens from the header.
   *
   * @param header
   *        the HttpHeader that needs to be cleaned.
   */
  private void removeConnectionTokens (final HttpHeader header)
  {
    final List <String> cons = header.getHeaders ("Connection");
    final int l = cons.size ();
    for (int i = 0; i < l; i++)
    {
      final String val = cons.get (i);
      /* ok, split it... */
      int s;
      int start = 0;
      while (start < val.length ())
      {
        while (val.length () > start + 1 && (val.charAt (start) == ' ' || val.charAt (start) == ','))
          start++;
        if (val.length () > start + 1 && val.charAt (start) == '"')
        {
          start++;
          s = val.indexOf ('"', start);
          while (s >= -1 && val.charAt (s - 1) == '\\' && val.length () > s + 1)
            s = val.indexOf ('"', s + 1);
          if (s == -1)
            s = val.length ();
          String t = val.substring (start, s).trim ();
          /* ok, unquote the value... */
          final StringBuilder sb = new StringBuilder (t.length ());
          for (int c = 0; c < t.length (); c++)
          {
            final char z = t.charAt (c);
            if (z != '\\')
              sb.append (z);
          }
          t = sb.toString ();
          header.removeHeader (t);
          s = val.indexOf (',', s + 1);
          if (s == -1)
            start = val.length ();
          else
            start = s + 1;
        }
        else
        {
          s = val.indexOf (',', start + 1);
          if (s == -1)
            s = val.length ();
          final String t = val.substring (start, s).trim ();
          header.removeHeader (t);
          start = s + 1;
        }
      }
    }
  }

  private HttpHeader checkMaxForwards (final Connection con, final HttpHeader header, final String val)
  {
    try
    {
      final BigInteger bi = new BigInteger (val);
      if (bi.equals (ZERO))
      {
        if (header.getMethod ().equals ("TRACE"))
        {
          final HttpHeaderWithContent ret = con.getHttpGenerator ().get200 ();
          ret.setContent (header.toString (), "UTF-8");
          return ret;
        }
        final HttpHeader ret = con.getHttpGenerator ().get200 ();
        ret.setHeader ("Allow", "GET,HEAD,POST,OPTIONS,TRACE");
        ret.setHeader ("Content-Length", "0");
        return ret;
      }
      final BigInteger b3 = bi.subtract (ONE);
      header.setHeader ("Max-Forwards", b3.toString ());
    }
    catch (final NumberFormatException e)
    {
      LOGGER.warn ("Bad number for Max-Forwards: '" + val + "'");
    }
    return null;
  }

  public HttpHeader doHttpInFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    // ok, no real header then don't do a thing.
    if (header.isDot9Request ())
    {
      con.setMayCache (false);
      con.setMayUseCache (false);
      con.setKeepalive (false);
      con.setChunking (false);
      return null;
    }

    handleAuthentications (header, con);

    boolean maychunk;
    boolean mayKeepAlive;

    final String requestVersion = header.getHTTPVersion ().toUpperCase ();
    if (requestVersion.equals ("HTTP/1.1"))
    {
      final String host = header.getHeader ("Host");
      if (host == null)
      {
        final Exception exe = new Exception ("No host header set in HTTP/1.1 request");
        return con.getHttpGenerator ().get400 (exe);
      }
      maychunk = true;
      String closeit = header.getHeader ("Proxy-Connection");
      if (closeit == null)
        closeit = header.getHeader ("Connection");
      mayKeepAlive = (closeit == null || !closeit.equalsIgnoreCase ("close"));
    }
    else
    {
      header.setHTTPVersion ("HTTP/1.1");
      maychunk = false;
      // stupid netscape to not follow the standards,
      // only "Connection" should be used...
      String keepalive = header.getHeader ("Proxy-Connection");
      mayKeepAlive = (keepalive != null && keepalive.equalsIgnoreCase ("Keep-Alive"));
      if (!mayKeepAlive)
      {
        keepalive = header.getHeader ("Connection");
        mayKeepAlive = (keepalive != null && keepalive.equalsIgnoreCase ("Keep-Alive"));
      }
    }

    boolean useCached = true;
    boolean cacheAllowed = true;
    // damn how many system that use cookies with id's
    /*
     * System.out.println ("auth: " + header.getHeader ("authorization") +
     * ", cookie:" + header.getHeader ("cookie") + ", Pragma: " +
     * header.getHeader ("Pragma") + ", Cache: " + header.getHeader
     * ("Cache-Control"));
     */
    // String cached = header.getHeader ("Pragma");
    List <String> ccs = header.getHeaders ("Cache-Control");
    for (String cached : ccs)
    {
      cached = cached.trim ().toLowerCase ();
      if (cached.equals ("no-store"))
      {
        useCached = false;
        cacheAllowed = false;
      }
      else
        if (cached.equals ("no-cache"))
        {
          useCached = false;
        }
        else
          if (cached.equals ("no-transform"))
          {
            useCached = false; // cache is transformed.
            cacheAllowed = false; // dont store, no point.
            con.setFilteringNotAllowed ();
          }
    }

    ccs = header.getHeaders ("Pragma");
    for (String cached : ccs)
    {
      cached = cached.trim ().toLowerCase ();
      if (cached.equals ("no-cache"))
      {
        useCached = false;
      }
    }

    final String method = header.getMethod ().trim ();
    if (!method.equals ("GET") && !method.equals ("HEAD"))
    {
      useCached = false;
      cacheAllowed = false;
      // mayKeepAlive = false;
    }
    else
      if (method.equals ("HEAD"))
      {
        maychunk = false;
      }
    con.setChunking (maychunk);

    final String mf = header.getHeader ("Max-Forwards");
    if (mf != null)
    {
      final HttpHeader ret = checkMaxForwards (con, header, mf);
      if (ret != null)
      {
        return ret;
      }
    }

    final String auths = header.getHeader ("authorization");
    if (auths != null)
    {
      useCached = false; // dont use cached files,
      cacheAllowed = false; // and dont cache it.
    }
    else
      if (cookieId)
      {
        final String cookie = header.getHeader ("cookie");
        String lccookie;
        if (cookie != null && // cookie-passwords suck.
            (((lccookie = cookie.toLowerCase ()).indexOf ("password") >= 0) || (lccookie.indexOf ("id") >= 0)))
        {
          useCached = false; // dont use cached files,
          cacheAllowed = false; // and dont cache it.
        }
      }
    con.setMayUseCache (useCached);
    con.setMayCache (cacheAllowed);
    con.setKeepalive (mayKeepAlive);

    String requri = header.getRequestURI ();
    if (requri.toLowerCase ().startsWith (NOPROXY))
      requri = handleNoProxyRequest (requri, header, con);

    final HttpHeader headerr = handleURLSetup (requri, header, con);
    if (headerr != null)
      return headerr;

    removeConnectionTokens (header);
    for (final String r : removes)
      header.removeHeader (r);

    final IProxyChain proxyChain = con.getProxy ().getProxyChain ();
    final Resolver resolver = proxyChain.getResolver (requri);
    if (resolver.isProxyConnected ())
    {
      final String auth = resolver.getProxyAuthString ();
      // it should look like this (using RabbIT:RabbIT):
      // Proxy-authorization: Basic UmFiYklUOlJhYmJJVA==
      if (auth != null && !auth.isEmpty ())
        header.setHeader ("Proxy-Authorization", "Basic " + Base64.safeEncode (auth, StandardCharsets.UTF_8));
    }

    // try to use keepalive backwards.
    // This is not needed since it is a HTTP/1.1 request.
    // header.setHeader ("Connection", "Keep-Alive");

    return null;
  }

  private boolean checkCacheControl (@Nonnull final String cachecontrol)
  {
    for (String sCached : StringHelper.getExploded (',', cachecontrol))
    {
      sCached = sCached.trim ();
      if (sCached.equals ("no-store"))
        return false;
      if (sCached.equals ("private"))
        return false;
    }
    return true;
  }

  public HttpHeader doHttpOutFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    boolean useCache = true;
    // String cached = header.getHeader ("Pragma");
    final List <String> ccs = header.getHeaders ("Cache-Control");
    for (final String cached : ccs)
    {
      if (cached != null)
        useCache &= checkCacheControl (cached);
    }

    final String status = header.getStatusCode ().trim ();
    if (!(status.equals ("200") || status.equals ("206") || status.equals ("304")))
    {
      con.setKeepalive (false);
      useCache = false;
    }

    String age = header.getHeader ("Age");
    long secs = 0;
    if (age == null)
      age = "0";
    try
    {
      secs = Long.parseLong (age);
    }
    catch (final NumberFormatException e)
    {
      // ignore, we already have a warning for this..
    }
    if (secs > 60 * 60 * 24)
      header.setHeader ("Warning", "113 RabbIT \"Heuristic expiration\"");

    header.setResponseHTTPVersion ("HTTP/1.1");
    con.setMayCache (useCache);

    /** Try to make sure that IE can handle NTLM authentication. */
    /** This does not work. */
    /*
     * List ls = header.getHeaders ("WWW-Authenticate"); for (Iterator i =
     * ls.iterator (); i.hasNext (); ) { String s = (String)i.next (); if
     * (s.indexOf ("Negotiate") != -1 || s.indexOf ("NTLM") != -1) {
     * con.setFilteringNotAllowed (false); con.setChunking (false); } }
     */

    removeConnectionTokens (header);
    for (final String r : removes)
      header.removeHeader (r);

    final String d = header.getHeader ("Date");
    if (d == null)
    {
      // ok, maybe we should check if there is an Age set
      // otherwise we can do like this.
      header.setHeader ("Date", HttpDateParser.getDateString (new Date ()));
    }

    final String cl = header.getHeader ("Content-Length");
    if (cl == null && !con.getChunking ())
      con.setKeepalive (false);

    return null;
  }

  public HttpHeader doConnectFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    final String uap = header.getHeader ("Proxy-Authorization");
    if (uap != null)
      handleProxyAuthentication (uap, con);
    return null;
  }

  public void setup (final StringMap properties, final HttpProxy proxy)
  {
    removes.clear ();
    final String rs = properties.getOrDefault ("remove", "");
    final String [] sts = rs.split (",");
    for (final String r : sts)
      removes.add (r.trim ());
    final String userFile = properties.getOrDefault ("userfile", "conf/users");
    userHandler.setFile (userFile);
    final String cid = properties.getOrDefault ("cookieid", "false");
    cookieId = cid.equals ("true");
  }

  /**
   * Check if a given url is a public URL of the Proxy.
   *
   * @param url
   *        the URL to check.
   * @return true if this url has public access, false otherwise.
   */
  public boolean isPublic (final URL url)
  {
    final String file = url.getFile ();
    return file.startsWith ("/FileSender/public/");
  }
}
