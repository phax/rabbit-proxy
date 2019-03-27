package com.helger.rabbit.handler;

import com.helger.commons.collection.attr.StringMap;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.IResourceSource;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.proxy.HttpProxy;
import com.helger.rabbit.proxy.TrafficLoggerHandler;

/**
 * The methods needed to create a new Handler.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public interface IHandlerFactory
{

  /**
   * Get a new Handler for the given request made.
   *
   * @param connection
   *        the Connection handling the request.
   * @param tlh
   *        the Traffic LOGGER handler.
   * @param header
   *        the request.
   * @param webheader
   *        the response.
   * @param content
   *        the resource.
   * @param mayCache
   *        if the handler may cache the response.
   * @param mayFilter
   *        if the handler may filter the response.
   * @param size
   *        the Size of the data beeing handled (-1 = unknown length).
   * @return the new Handler
   */
  IHandler getNewInstance (Connection connection,
                          TrafficLoggerHandler tlh,
                          HttpHeader header,
                          HttpHeader webheader,
                          IResourceSource content,
                          boolean mayCache,
                          boolean mayFilter,
                          long size);

  /**
   * setup the handler factory.
   *
   * @param properties
   *        the properties for this factory
   * @param proxy
   *        the HttpProxy using this HandlerFactory
   */
  void setup (StringMap properties, HttpProxy proxy);
}
