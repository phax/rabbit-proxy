package com.helger.rabbit.filter;

import java.nio.channels.SocketChannel;

import com.helger.commons.collection.attr.StringMap;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.proxy.Connection;
import com.helger.rabbit.proxy.HttpProxy;

/**
 * A filter for http headers.
 */
public interface IHttpFilter
{

  /**
   * Test if a socket/header combination is valid or return a new HttpHeader.
   *
   * @param socket
   *        the Socket that made the request.
   * @param header
   *        the actual request made.
   * @param con
   *        the Connection handling the request.
   * @return null if everything is fine or a HTTPHeader describing the error
   *         (like a 403).
   */
  HttpHeader doHttpInFiltering (SocketChannel socket, HttpHeader header, Connection con);

  /**
   * Test if a socket/header combination is valid or return a new HttpHeader.
   *
   * @param socket
   *        the Socket that made the request.
   * @param header
   *        the actual request made.
   * @param con
   *        the Connection handling the request.
   * @return null if everything is fine or a HTTPHeader describing the error
   *         (like a 403).
   */
  HttpHeader doHttpOutFiltering (SocketChannel socket, HttpHeader header, Connection con);

  /**
   * Test if a socket/header combination is valid or return a new HttpHeader.
   *
   * @param socket
   *        the Socket that made the request.
   * @param header
   *        the actual request made.
   * @param con
   *        the Connection handling the request.
   * @return null if everything is fine or a HTTPHeader describing the error
   *         (like a 403).
   */
  HttpHeader doConnectFiltering (SocketChannel socket, HttpHeader header, Connection con);

  /**
   * Setup this filter.
   *
   * @param properties
   *        the StringMap to get the settings from.
   * @param proxy
   *        the HttpProxy that is using this filter
   */
  void setup (StringMap properties, HttpProxy proxy);
}
