package com.helger.rabbit.httpio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.logging.Logger;

import com.helger.rabbit.io.BufferHandle;
import com.helger.rabbit.io.CacheBufferHandle;
import com.helger.rnio.BufferHandler;
import com.helger.rnio.NioHandler;
import com.helger.rnio.TaskIdentifier;
import com.helger.rnio.impl.Closer;
import com.helger.rnio.impl.DefaultTaskIdentifier;

/**
 * A resource that comes from a file.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class FileResourceSource implements ResourceSource
{
  protected FileChannel fc;

  // used for block handling.
  private BlockListener listener;
  private NioHandler nioHandler;
  protected BufferHandle bufHandle;

  private final Logger logger = Logger.getLogger (getClass ().getName ());

  /**
   * Create a new FileResourceSource using the given filename
   * 
   * @param filename
   *        the file for this resource
   * @param nioHandler
   *        the NioHandler to use for background tasks
   * @param bufHandler
   *        the BufferHandler to use when reading and writing
   * @throws IOException
   *         if the file is a valid file
   */
  public FileResourceSource (final String filename,
                             final NioHandler nioHandler,
                             final BufferHandler bufHandler) throws IOException
  {
    this (new File (filename), nioHandler, bufHandler);
  }

  /**
   * Create a new FileResourceSource using the given filename
   * 
   * @param f
   *        the resource
   * @param nioHandler
   *        the NioHandler to use for background tasks
   * @param bufHandler
   *        the BufferHandler to use when reading and writing
   * @throws IOException
   *         if the file is a valid file
   */
  public FileResourceSource (final File f,
                             final NioHandler nioHandler,
                             final BufferHandler bufHandler) throws IOException
  {
    if (!f.exists ())
      throw new FileNotFoundException ("File: " + f.getName () + " not found");
    if (!f.isFile ())
      throw new FileNotFoundException ("File: " + f.getName () + " is not a regular file");
    final FileInputStream fis = new FileInputStream (f);
    fc = fis.getChannel ();
    this.nioHandler = nioHandler;
    this.bufHandle = new CacheBufferHandle (bufHandler);
  }

  /**
   * FileChannels can be used, will always return true.
   * 
   * @return true
   */
  public boolean supportsTransfer ()
  {
    return true;
  }

  public long length ()
  {
    try
    {
      return fc.size ();
    }
    catch (final IOException e)
    {
      e.printStackTrace ();
      return -1;
    }
  }

  public long transferTo (final long position, final long count, final WritableByteChannel target) throws IOException
  {
    try
    {
      return fc.transferTo (position, count, target);
    }
    catch (final IOException e)
    {
      if ("Resource temporarily unavailable".equals (e.getMessage ()))
      {
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5103988
        // transferTo on linux throws IOException on full buffer.
        return 0;
      }
      throw e;
    }
  }

  /**
   * Generally we do not come into this method, but it can happen..
   */
  public void addBlockListener (final BlockListener listener)
  {
    this.listener = listener;
    // Get buffer on selector thread.
    bufHandle.getBuffer ();
    final TaskIdentifier ti = new DefaultTaskIdentifier (getClass ().getSimpleName (),
                                                         "addBlockListener: channel: " + fc);
    nioHandler.runThreadTask (new ReadBlock (), ti);
  }

  private class ReadBlock implements Runnable
  {
    public void run ()
    {
      try
      {
        final ByteBuffer buffer = bufHandle.getBuffer ();
        final int read = fc.read (buffer);
        if (read == -1)
        {
          returnFinished ();
        }
        else
        {
          buffer.flip ();
          returnBlockRead ();
        }
      }
      catch (final IOException e)
      {
        returnWithFailure (e);
      }
    }
  }

  private void returnWithFailure (final Exception e)
  {
    bufHandle.possiblyFlush ();
    listener.failed (e);
  }

  private void returnFinished ()
  {
    bufHandle.possiblyFlush ();
    listener.finishedRead ();
  }

  private void returnBlockRead ()
  {
    listener.bufferRead (bufHandle);
  }

  public void release ()
  {
    Closer.close (fc, logger);
    listener = null;
    nioHandler = null;
    bufHandle.possiblyFlush ();
    bufHandle = null;
  }
}
