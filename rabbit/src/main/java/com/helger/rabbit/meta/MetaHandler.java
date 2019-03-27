package com.helger.rabbit.meta;

import java.io.IOException;

import com.helger.commons.collection.attr.StringMap;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.util.ITrafficLogger;

/**
 * The specification for dynamic status information handlers in the proxy.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public interface MetaHandler
{

  /**
   * Handle a client request.
   *
   * @param request
   *        the http request header.
   * @param htab
   *        the supplied argument to the page (CGI-parameters).
   * @param con
   *        the Connection that is serving the request.
   * @param tlProxy
   *        the TrafficLogger to log proxy traffic on.
   * @param tlClient
   *        the TrafficLogger to log client traffic on.
   * @throws IOException
   *         if writing the resource fails
   */
  void handle (HttpHeader request,
               StringMap htab,
               Connection con,
               ITrafficLogger tlProxy,
               ITrafficLogger tlClient) throws IOException;
}
