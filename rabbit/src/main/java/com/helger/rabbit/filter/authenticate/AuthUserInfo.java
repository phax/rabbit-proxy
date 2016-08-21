package com.helger.rabbit.filter.authenticate;

import java.net.InetAddress;

/**
 * Information about an authenticated user.
 */
public class AuthUserInfo
{
  private final String token;
  private final long timeout;
  private final InetAddress sa;

  /**
   * A user has successfully managed to authenticat
   * 
   * @param token
   *        the token used
   * @param timeout
   *        the timeout in millis
   * @param sa
   *        the InetAddress the user came from
   */
  public AuthUserInfo (final String token, final long timeout, final InetAddress sa)
  {
    this.token = token;
    this.timeout = timeout;
    this.sa = sa;
  }

  @Override
  public String toString ()
  {
    return getClass ().getSimpleName () + "{token: " + token + ", timeout: " + timeout + ", socket: " + sa + "}";
  }

  /**
   * Check if this authentication is still valid.
   * 
   * @return true if authentication token is still valid, false otherwise
   */
  public boolean stillValid ()
  {
    final long now = System.currentTimeMillis ();
    return timeout > now;
  }

  /**
   * Check if the given token matches the token for this user
   * 
   * @param token
   *        the new token to validate
   * @return true if the tokens match, false otherwise
   */
  public boolean correctToken (final String token)
  {
    return token.equals (this.token);
  }

  /**
   * Check if the user is still using the same InetAddress
   * 
   * @param sa
   *        the new InetAddress of the user
   * @return true if the user is still on the same InetAddress
   */
  public boolean correctSocketAddress (final InetAddress sa)
  {
    return this.sa.equals (sa);
  }
}
