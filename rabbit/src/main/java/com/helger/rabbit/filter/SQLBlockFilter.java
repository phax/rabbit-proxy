package com.helger.rabbit.filter;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.SocketChannel;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.collection.attr.StringMap;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.proxy.HttpProxy;

/**
 * A blocker that checks hosts against a sql database
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class SQLBlockFilter implements IHttpFilter
{
  private static final Logger LOGGER = LoggerFactory.getLogger (SQLBlockFilter.class);
  
  private DataSourceHelper dsh;
  private final String DEFAULT_SQL = "select 1 from bad_hosts where hostname = ?";

  public HttpHeader doHttpInFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    try (final java.sql.Connection db = dsh.getConnection ();
        final PreparedStatement ps = db.prepareStatement (dsh.getSelect ()))
    {
      final URL u = new URL (header.getRequestURI ());
      ps.setString (1, u.getHost ());
      try (final ResultSet rs = ps.executeQuery ())
      {
        if (rs.next ())
          return con.getHttpGenerator ().get403 ();
      }
    }
    catch (final MalformedURLException e)
    {
      LOGGER.warn ("Failed to create URL", e);
    }
    catch (final SQLException e)
    {
      LOGGER.warn ("Failed to get database connection", e);
    }
    return null;
  }

  public HttpHeader doHttpOutFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    return null;
  }

  public HttpHeader doConnectFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    // TODO: possibly block connect requests?
    return null;
  }

  /**
   * Setup this class with the given properties.
   *
   * @param props
   *        the new configuration of this class.
   */
  public void setup (final StringMap props, final HttpProxy proxy)
  {
    try
    {
      dsh = new DataSourceHelper (props, DEFAULT_SQL);
    }
    catch (final NamingException e)
    {
      throw new RuntimeException (e);
    }
  }
}
