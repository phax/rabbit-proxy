package com.helger.rabbit.client;

import java.io.IOException;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.WebConnectionResourceSource;

/**
 * A basic ClientListener.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ClientListenerAdapter implements ClientListener
{
  private static final Logger LOGGER = LoggerFactory.getLogger (ClientListenerAdapter.class);

  /**
   * Create the redirected url and calls redirectedTo() and requestDone().
   */
  @Override
  public void redirected (final HttpHeader request, final String location, final ClientBase base)
  {
    try
    {
      final URL u = base.getRedirectedURL (request, location);
      redirectedTo (u.toString ());
      requestDone (request);
    }
    catch (final IOException e)
    {
      handleFailure (request, e);
    }
  }

  /**
   * This method does nothing, override to perform actual request.
   *
   * @param url
   *        the new URL that the redirect header contained
   * @throws IOException
   *         if redirecting fails
   */
  public void redirectedTo (final String url) throws IOException
  {
    // nothing
  }

  /**
   * This method does nothing.
   */
  @Override
  public void handleResponse (final HttpHeader request, final HttpHeader response, final WebConnectionResourceSource wc)
  {
    // empty
  }

  /**
   * This method returns true, override if you want different behaviour.
   */
  @Override
  public boolean followRedirects ()
  {
    return true;
  }

  /**
   * Logs an error to the LOGGER and calls requestDone().
   */
  @Override
  public void handleTimeout (final HttpHeader request)
  {
    LOGGER.warn ("Request to " + request.getRequestURI () + " timed out");
    requestDone (request);
  }

  /**
   * Logs an error to the LOGGER and calls requestDone().
   */
  @Override
  public void handleFailure (final HttpHeader request, final Exception e)
  {
    LOGGER.warn ("Request to " + request.getRequestURI () + " failed: ", e);
    requestDone (request);
  }

  /** Handle any cleanup in this method. */
  @Override
  public void requestDone (final HttpHeader request)
  {
    // nothing.
  }
}
