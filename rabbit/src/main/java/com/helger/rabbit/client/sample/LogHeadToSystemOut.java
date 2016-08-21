package com.helger.rabbit.client.sample;

import com.helger.rabbit.http.HttpHeader;

/**
 * A http HEAD response handler that just logs the response to System.out.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class LogHeadToSystemOut implements HeadResponseListener
{
  public void response (final HttpHeader request, final HttpHeader response)
  {
    System.out.print (request.getRequestURI () + "\n" + response);
  }
}
