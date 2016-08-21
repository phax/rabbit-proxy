package com.helger.rabbit.proxy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.helger.rabbit.io.BufferHandle;
import com.helger.rabbit.io.WebConnection;
import com.helger.rnio.IReadHandler;

/**
 * A base for client resource transfer classes.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
abstract class ResourceHandlerBase implements ClientResourceHandler
{
  protected final Connection con;
  protected final BufferHandle bufHandle;
  protected final TrafficLoggerHandler tlh;
  protected WebConnection wc;
  protected ClientResourceTransferredListener listener;
  private List <ClientResourceListener> resourceListeners;

  public ResourceHandlerBase (final Connection con, final BufferHandle bufHandle, final TrafficLoggerHandler tlh)
  {
    this.con = con;
    this.bufHandle = bufHandle;
    this.tlh = tlh;
  }

  /**
   * Will store the variables and call doTransfer ()
   */
  public void transfer (final WebConnection wc, final ClientResourceTransferredListener crtl)
  {
    this.wc = wc;
    this.listener = crtl;
    doTransfer ();
  }

  protected void doTransfer ()
  {
    if (!bufHandle.isEmpty ())
      sendBuffer ();
    else
      waitForRead ();
  }

  public void addContentListener (final ClientResourceListener crl)
  {
    if (resourceListeners == null)
      resourceListeners = new ArrayList <> ();
    resourceListeners.add (crl);
  }

  public void fireResourceDataRead (final BufferHandle bufHandle)
  {
    if (resourceListeners == null)
      return;
    for (final ClientResourceListener crl : resourceListeners)
    {
      crl.resourceDataRead (bufHandle);
    }
  }

  abstract void sendBuffer ();

  protected void waitForRead ()
  {
    bufHandle.possiblyFlush ();
    final IReadHandler sh = new Reader ();
    con.getNioHandler ().waitForRead (con.getChannel (), sh);
  }

  private class Reader implements IReadHandler
  {
    private final Long timeout = con.getNioHandler ().getDefaultTimeout ();

    public void read ()
    {
      try
      {
        final ByteBuffer buffer = bufHandle.getBuffer ();
        final int read = con.getChannel ().read (buffer);
        if (read == 0)
        {
          waitForRead ();
        }
        else
          if (read == -1)
          {
            failed (new IOException ("Failed to read request"));
          }
          else
          {
            tlh.getClient ().read (read);
            buffer.flip ();
            sendBuffer ();
          }
      }
      catch (final IOException e)
      {
        listener.failed (e);
      }
    }

    public void closed ()
    {
      bufHandle.possiblyFlush ();
      listener.failed (new IOException ("Connection closed"));
    }

    public void timeout ()
    {
      bufHandle.possiblyFlush ();
      listener.timeout ();
    }

    public boolean useSeparateThread ()
    {
      return false;
    }

    public String getDescription ()
    {
      return toString ();
    }

    public Long getTimeout ()
    {
      return timeout;
    }
  }

  public void timeout ()
  {
    bufHandle.possiblyFlush ();
    listener.timeout ();
  }

  public void failed (final Exception e)
  {
    bufHandle.possiblyFlush ();
    listener.failed (e);
  }
}
