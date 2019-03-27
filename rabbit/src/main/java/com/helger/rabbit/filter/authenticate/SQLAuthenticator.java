package com.helger.rabbit.filter.authenticate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.collection.attr.StringMap;
import com.helger.rabbit.filter.DataSourceHelper;
import com.helger.rabbit.http.HttpHeader;

/**
 * An authenticator that checks the username/password against an sql database.
 * Will read the following parameters from the config file:
 * <ul>
 * <li>resource
 * <li>user
 * <li>password
 * <li>select - the sql query to run
 * </ul>
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class SQLAuthenticator implements IAuthenticator
{
  private static final Logger LOGGER = LoggerFactory.getLogger (SQLAuthenticator.class);
  private static final String DEFAULT_SELECT = "select password from users where username = ?";

  private final DataSourceHelper dsh;

  /**
   * Create a new SQLAuthenticator that will be configured using the given
   * properties.
   *
   * @param props
   *        the configuration for this authenticator
   */
  public SQLAuthenticator (final StringMap props)
  {
    try
    {
      dsh = new DataSourceHelper (props, DEFAULT_SELECT);
    }
    catch (final NamingException e)
    {
      throw new RuntimeException (e);
    }
  }

  public String getToken (final HttpHeader header, final com.helger.rabbit.proxy.Connection con)
  {
    return con.getPassword ();
  }

  public boolean authenticate (final String user, final String token)
  {
    try
    {
      try (final Connection db = dsh.getConnection ())
      {
        final String pwd = getDbPassword (db, user);
        if (pwd == null)
          return false;
        return pwd.equals (token);
      }
    }
    catch (final SQLException e)
    {
      LOGGER.warn ("Exception when trying to authenticate", e);
    }
    return false;
  }

  private String getDbPassword (final Connection db, final String username) throws SQLException
  {
    try (final PreparedStatement ps = db.prepareStatement (dsh.getSelect ()))
    {
      ps.setString (1, username);
      try (final ResultSet rs = ps.executeQuery ())
      {
        if (rs.next ())
          return rs.getString (1);
      }
    }
    return null;
  }
}
