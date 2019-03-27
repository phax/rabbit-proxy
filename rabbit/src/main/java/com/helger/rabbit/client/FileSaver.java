package com.helger.rabbit.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import com.helger.commons.io.stream.StreamHelper;
import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.BlockListener;
import com.helger.rabbit.httpio.IResourceSource;
import com.helger.rabbit.io.BufferHandle;
import com.helger.rnio.ITaskIdentifier;
import com.helger.rnio.impl.DefaultTaskIdentifier;

/**
 * A class to save a ResourceSource into a file. This is mostly an example of
 * how to use the rabbit client classes.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class FileSaver implements BlockListener
{
  private final HttpHeader request;
  private final ClientBase clientBase;
  private final ClientListener listener;
  private final IResourceSource rs;
  private final FileChannel fc;

  /**
   * Create a new FileSaver that will write a resource to the given file.
   *
   * @param request
   *        the actual request
   * @param clientBase
   *        the client
   * @param listener
   *        the ClientListener to tell when the resource has been fully handled
   * @param rs
   *        the resource to save
   * @param f
   *        where to store the resource
   * @throws IOException
   *         if the file can not be written
   */
  public FileSaver (final HttpHeader request,
                    final ClientBase clientBase,
                    final ClientListener listener,
                    final IResourceSource rs,
                    final File f) throws IOException
  {
    this.request = request;
    this.clientBase = clientBase;
    this.listener = listener;
    @SuppressWarnings ("resource")
    final FileOutputStream fos = new FileOutputStream (f);
    fc = fos.getChannel ();
    this.rs = rs;
  }

  public void bufferRead (final BufferHandle bufHandle)
  {
    final ITaskIdentifier ti = new DefaultTaskIdentifier (getClass ().getSimpleName (), request.getRequestURI ());
    clientBase.getNioHandler ().runThreadTask ( () -> {
      try
      {
        final ByteBuffer buf = bufHandle.getBuffer ();
        fc.write (buf);
        bufHandle.possiblyFlush ();
        _readMore ();
      }
      catch (final IOException e)
      {
        failed (e);
      }
    }, ti);
  }

  private void _readMore ()
  {
    rs.addBlockListener (FileSaver.this);
  }

  public void finishedRead ()
  {
    rs.release ();
    try
    {
      fc.close ();
      listener.requestDone (request);
    }
    catch (final IOException e)
    {
      listener.handleFailure (request, e);
    }
  }

  public void failed (final Exception cause)
  {
    _downloadFailed ();
    listener.handleFailure (request, cause);
  }

  public void timeout ()
  {
    _downloadFailed ();
    listener.handleTimeout (request);
  }

  private void _downloadFailed ()
  {
    rs.release ();
    StreamHelper.close (fc);
  }
}
