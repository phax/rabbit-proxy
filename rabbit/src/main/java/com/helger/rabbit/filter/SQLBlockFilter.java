package com.helger.rabbit.filter;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.SocketChannel;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingException;

import com.helger.commons.url.SMap;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.proxy.HttpProxy;

/**
 * A blocker that checks hosts against a sql database
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class SQLBlockFilter implements HttpFilter
{
  private DataSourceHelper dsh;
  private final Logger logger = Logger.getLogger (getClass ().getName ());
  private final String DEFAULT_SQL = "select 1 from bad_hosts where hostname = ?";

  public HttpHeader doHttpInFiltering (final SocketChannel socket, final HttpHeader header, final Connection con)
  {
    try
    {
      final java.sql.Connection db = dsh.getConnection ();
      try
      {
        final PreparedStatement ps = db.prepareStatement (dsh.getSelect ());
        try
        {
          final URL u = new URL (header.getRequestURI ());
          ps.setString (1, u.getHost ());
          final ResultSet rs = ps.executeQuery ();
          try
          {
            if (rs.next ())
            {
              return con.getHttpGenerator ().get403 ();
            }
          }
          finally
          {
            rs.close ();
          }
        }
        finally
        {
          ps.close ();
        }
      }
      finally
      {
        db.close ();
      }
    }
    catch (final MalformedURLException e)
    {
      logger.log (Level.WARNING, "Failed to create URL", e);
    }
    catch (final SQLException e)
    {
      logger.log (Level.WARNING, "Failed to get database connection", e);
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
  public void setup (final SMap props, final HttpProxy proxy)
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
