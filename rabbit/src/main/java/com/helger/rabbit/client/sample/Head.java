package com.helger.rabbit.client.sample;

import java.io.IOException;

import com.helger.rabbit.client.ClientBase;
import com.helger.rabbit.client.ClientListenerAdapter;
import com.helger.rabbit.client.CountingClientBaseStopper;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.WebConnectionResourceSource;

/**
 * A class that performs a set HEAD request to the given urls. This is mostly an
 * example of how to use the rabbit client classes.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class Head
{
  private final HeadResponseListener listener;
  private final ClientBase clientBase;
  private final CountingClientBaseStopper ccbs;

  /**
   * Run a HEAD request for any url passed in args and then prints the results
   * on System.out.
   * 
   * @param args
   *        the command line arguments
   */
  public static void main (final String [] args)
  {
    try
    {
      final LogHeadToSystemOut ltso = new LogHeadToSystemOut ();
      final Head h = new Head (ltso);
      h.head (args);
    }
    catch (final IOException e)
    {
      e.printStackTrace ();
    }
  }

  /**
   * Create a new HEAD requestor.
   * 
   * @param listener
   *        the HeadResponseListener to notify when a request has completed
   * @throws IOException
   *         if the client can not be created
   */
  public Head (final HeadResponseListener listener) throws IOException
  {
    this.listener = listener;
    clientBase = new ClientBase ();
    ccbs = new CountingClientBaseStopper (clientBase);
  }

  /**
   * Run HEAD requests to all the urls given.
   * 
   * @param urls
   *        a number of urls.
   * @throws IOException
   *         if sending the requests fails
   */
  public void head (final String [] urls) throws IOException
  {
    for (final String url : urls)
      head (url);
  }

  /**
   * Run HEAD requests to the given url
   * 
   * @param url
   *        the url to run a HEAD reqeusts against.
   * @throws IOException
   *         if sending the request fails
   */
  public void head (final String url) throws IOException
  {
    ccbs.sendRequest (clientBase.getRequest ("HEAD", url), new HeadListener ());
  }

  private class HeadListener extends ClientListenerAdapter
  {
    @Override
    public void redirectedTo (final String url) throws IOException
    {
      head (url);
    }

    @Override
    public void handleResponse (final HttpHeader request,
                                final HttpHeader response,
                                final WebConnectionResourceSource wc)
    {
      listener.response (request, response);
      wc.release ();
      requestDone (request);
    }

    @Override
    public void requestDone (final HttpHeader request)
    {
      ccbs.requestDone ();
    }
  }
}
