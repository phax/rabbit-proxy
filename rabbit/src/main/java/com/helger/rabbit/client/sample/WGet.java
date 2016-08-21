package com.helger.rabbit.client.sample;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import com.helger.rabbit.client.ClientBase;
import com.helger.rabbit.client.ClientListenerAdapter;
import com.helger.rabbit.client.CountingClientBaseStopper;
import com.helger.rabbit.client.FileSaver;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.WebConnectionResourceSource;

/**
 * A class to download a set of resources. Given a set of urls this class will
 * download all of them concurrently using a standard ClientBase. This is mostly
 * an example of how to use the rabbit client classes.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class WGet
{
  private final ClientBase clientBase;
  private final CountingClientBaseStopper ccbs;

  /**
   * Download all urls given in the args arrays.
   * 
   * @param args
   *        the command line arguments
   */
  public static void main (final String [] args)
  {
    try
    {
      final WGet wget = new WGet ();
      if (args.length > 0)
        wget.get (args);
      else
        wget.clientBase.shutdown ();
    }
    catch (final IOException e)
    {
      e.printStackTrace ();
    }
  }

  /**
   * Create a new WGet that can be used to download resources.
   * 
   * @throws IOException
   *         if starting the NioHandler fails
   */
  public WGet () throws IOException
  {
    clientBase = new ClientBase ();
    ccbs = new CountingClientBaseStopper (clientBase);
  }

  /**
   * Add a set of urls to download.
   * 
   * @param urls
   *        the URL:s to download
   * @throws IOException
   *         if the get fails at startup
   */
  public void get (final String [] urls) throws IOException
  {
    for (final String url : urls)
      get (url);
  }

  /**
   * Add an url to the set of urls to be downloaded
   * 
   * @param url
   *        the URL to download
   * @throws IOException
   *         if the get fail at startup
   */
  public void get (final String url) throws IOException
  {
    ccbs.sendRequest (clientBase.getRequest ("GET", url), new WGetListener ());
  }

  private class WGetListener extends ClientListenerAdapter
  {
    @Override
    public void redirectedTo (final String url) throws IOException
    {
      get (url);
    }

    @Override
    public void handleResponse (final HttpHeader request,
                                final HttpHeader response,
                                final WebConnectionResourceSource wrs)
    {
      try
      {
        final File f = new File (getFileName (request));
        if (f.exists ())
          throw new IOException ("File already exists: " + f.getName ());
        final FileSaver blockHandler = new FileSaver (request, clientBase, this, wrs, f);
        wrs.addBlockListener (blockHandler);
      }
      catch (final IOException e)
      {
        wrs.release ();
        handleFailure (request, e);
      }
    }

    @Override
    public void requestDone (final HttpHeader request)
    {
      ccbs.requestDone ();
    }
  }

  private String getFileName (final HttpHeader request) throws IOException
  {
    final URL u = new URL (request.getRequestURI ());
    String s = u.getFile ();
    final int i = s.lastIndexOf ('/');
    if (i > -1)
      s = s.substring (i + 1);
    if (s.equals (""))
      return "index.html";
    return s;
  }
}
