package com.helger.rabbit.filter.authenticate;

import com.helger.commons.url.SMap;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.util.SimpleUserHandler;

/**
 * An authenticator that reads username and passwords from a plain text file.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class PlainFileAuthenticator implements IAuthenticator
{
  private final SimpleUserHandler userHandler;

  /**
   * Create a new PlainFileAuthenticator that will be configured using the given
   * properties.
   *
   * @param props
   *        the configuration for this authenticator
   */
  public PlainFileAuthenticator (final SMap props)
  {
    final String userFile = props.getOrDefault ("userfile", "conf/allowed");
    userHandler = new SimpleUserHandler ();
    userHandler.setFile (userFile);
  }

  public String getToken (final HttpHeader header, final Connection con)
  {
    return con.getPassword ();
  }

  public boolean authenticate (final String user, final String pwd)
  {
    return userHandler.isValidUser (user, pwd);
  }
}
