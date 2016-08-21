package com.helger.rabbit.proxy;

import com.helger.rabbit.cache.ICacheEntry;
import com.helger.rabbit.handler.IHandlerFactory;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.IResourceSource;
import com.helger.rabbit.io.BufferHandle;
import com.helger.rabbit.io.CacheBufferHandle;
import com.helger.rabbit.io.WebConnection;

/**
 * A container to send around less parameters.
 * 
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class RequestHandler
{
  private BufferHandle webHandle;
  private final ConditionalChecker cond;

  private IResourceSource content = null;
  private HttpHeader webHeader = null;
  private ICacheEntry <HttpHeader, HttpHeader> entry = null;
  private HttpHeader dataHook = null; // the entrys datahook if any.
  private IHandlerFactory handlerFactory = null;
  private long size = -1;
  private WebConnection wc = null;
  private boolean conditional;

  public RequestHandler (final Connection con)
  {
    webHandle = new CacheBufferHandle (con.getProxy ().getBufferHandler ());
    cond = new ConditionalChecker ();
  }

  public synchronized BufferHandle getWebHandle ()
  {
    return webHandle;
  }

  public synchronized void setWebHandle (final BufferHandle webHandle)
  {
    this.webHandle = webHandle;
  }

  public synchronized HttpHeader getWebHeader ()
  {
    return webHeader;
  }

  public synchronized void setWebHeader (final HttpHeader webHeader)
  {
    this.webHeader = webHeader;
  }

  public synchronized HttpHeader getDataHook ()
  {
    return dataHook;
  }

  public synchronized void setDataHook (final HttpHeader dataHook)
  {
    this.dataHook = dataHook;
  }

  public ConditionalChecker getCond ()
  {
    return cond;
  }

  public synchronized ICacheEntry <HttpHeader, HttpHeader> getEntry ()
  {
    return entry;
  }

  public synchronized void setEntry (final ICacheEntry <HttpHeader, HttpHeader> entry)
  {
    this.entry = entry;
  }

  public synchronized WebConnection getWebConnection ()
  {
    return wc;
  }

  public synchronized void setWebConnection (final WebConnection wc)
  {
    this.wc = wc;
  }

  public synchronized boolean isConditional ()
  {
    return conditional;
  }

  public synchronized void setConditional (final boolean conditional)
  {
    this.conditional = conditional;
  }

  public synchronized IResourceSource getContent ()
  {
    return content;
  }

  public synchronized void setContent (final IResourceSource content)
  {
    this.content = content;
  }

  public synchronized IHandlerFactory getHandlerFactory ()
  {
    return handlerFactory;
  }

  public synchronized void setHandlerFactory (final IHandlerFactory handlerFactory)
  {
    this.handlerFactory = handlerFactory;
  }

  public synchronized long getSize ()
  {
    return size;
  }

  public synchronized void setSize (final long size)
  {
    this.size = size;
  }
}
