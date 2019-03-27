package com.helger.rabbit.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import com.helger.rabbit.http.HttpHeader;
import com.helger.rabbit.httpio.HttpHeaderListener;
import com.helger.rabbit.httpio.HttpHeaderReader;
import com.helger.rabbit.io.BufferHandle;
import com.helger.rabbit.io.CacheBufferHandle;
import com.helger.rabbit.io.SimpleBufferHandle;
import com.helger.rabbit.util.ITrafficLogger;
import com.helger.rabbit.util.SimpleTrafficLogger;
import com.helger.rnio.IBufferHandler;
import com.helger.rnio.INioHandler;
import com.helger.rnio.IStatisticsHolder;
import com.helger.rnio.impl.BasicStatisticsHolder;
import com.helger.rnio.impl.CachingBufferHandler;
import com.helger.rnio.impl.MultiSelectorNioHandler;
import com.helger.rnio.impl.SimpleThreadFactory;

/**
 * A class to help test the HttpHeaderReader.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class MainTestHttpHeaderReader
{
  private final INioHandler nioHandler;
  private final ITrafficLogger trafficLogger = new SimpleTrafficLogger ();
  private final IBufferHandler bufferHandler = new CachingBufferHandler ();
  private final TestListener listener = new TestListener ();
  private SocketChannel readFrom, writeTo;
  private final int PORT = 9966;

  /**
   * Start the tests
   * 
   * @param args
   *        the command line arguments
   */
  public static void main (final String [] args)
  {
    try
    {
      final MainTestHttpHeaderReader tester = new MainTestHttpHeaderReader ();
      tester.start ();
      tester.runTests ();
      tester.waitForFinish ();
      tester.shutdown ();
    }
    catch (final IOException e)
    {
      e.printStackTrace ();
    }
  }

  private MainTestHttpHeaderReader () throws IOException
  {
    final ExecutorService es = Executors.newCachedThreadPool ();
    final IStatisticsHolder sh = new BasicStatisticsHolder ();
    nioHandler = new MultiSelectorNioHandler (es, sh, 1, Long.valueOf (15000L));
  }

  private void start () throws IOException
  {
    nioHandler.start (new SimpleThreadFactory ());
    final ServerSocketChannel ssc = ServerSocketChannel.open ();
    ssc.socket ().bind (new InetSocketAddress (PORT));
    readFrom = SocketChannel.open ();
    readFrom.connect (new InetSocketAddress (PORT));
    readFrom.configureBlocking (false);
    writeTo = ssc.accept ();
  }

  private void runTests () throws IOException
  {
    testSimpleFullHeader ();
    testTwoFullHeaders ();
    testEmpty ();
    testPartialHeader ();
    testNonZeroStart ();
    testNonZeroStartPartial ();
    testLargeHeader ();
  }

  private void waitForFinish ()
  {
    listener.waitForReady ();
  }

  private void shutdown ()
  {
    nioHandler.shutdown ();
  }

  private void testSimpleFullHeader () throws IOException
  {
    final BufferHandle clientHandle = getSimpleHeaderBuffer ();
    final HttpHeaderReader reader = getReader (clientHandle);
    reader.readHeader ();
    if (clientHandle.getBuffer () != null)
      throw new RuntimeException ("Failed to use full buffer");
  }

  private void testTwoFullHeaders () throws IOException
  {
    final HttpHeader header = getSimpleHttpHeader ();
    final byte [] data = header.getBytes ();
    final ByteBuffer buf = ByteBuffer.allocate (data.length * 2);
    buf.put (data);
    buf.put (data);
    buf.flip ();
    final BufferHandle clientHandle = new SimpleBufferHandle (buf);
    final HttpHeaderReader reader = getReader (clientHandle);
    reader.readHeader ();
    if (clientHandle.getBuffer () == null)
      throw new RuntimeException ("Should Still have a buffer");
    listener.waitForReady ();
    reader.readHeader ();
    if (clientHandle.getBuffer () != null)
      throw new RuntimeException ("Failed to use full buffer: " + clientHandle.getBuffer ());
  }

  private void testEmpty () throws IOException
  {
    final BufferHandle clientHandle = new CacheBufferHandle (bufferHandler);
    final HttpHeaderReader reader = getReader (clientHandle);
    reader.readHeader ();
    final BufferHandle header = getSimpleHeaderBuffer ();
    writeTo.write (header.getBuffer ());
  }

  private void testPartialHeader () throws IOException
  {
    final BufferHandle clientHandle = getSimpleHeaderBuffer ();
    final ByteBuffer buf = clientHandle.getBuffer ();
    final ByteBuffer bc = buf.duplicate ();
    buf.limit (10);
    final HttpHeaderReader reader = getReader (clientHandle);
    reader.readHeader ();
    bc.position (10);
    writeTo.write (bc);
  }

  private void testNonZeroStart () throws IOException
  {
    final HttpHeader header = getSimpleHttpHeader ();
    final byte [] buf = header.getBytes ();
    final ByteBuffer bc = ByteBuffer.allocate (buf.length * 2 + 100);
    bc.position (100);
    bc.put (buf);
    bc.put (buf);
    bc.position (100);
    final BufferHandle clientHandle = new SimpleBufferHandle (bc);
    final HttpHeaderReader reader = getReader (clientHandle);
    reader.readHeader ();
    listener.waitForReady ();
    reader.readHeader ();
    if (clientHandle.getBuffer () != null)
      throw new RuntimeException ("Failed to use full buffer: " + clientHandle.getBuffer ());
  }

  private void testNonZeroStartPartial () throws IOException
  {
    final HttpHeader header = getSimpleHttpHeader ();
    final byte [] buf = header.getBytes ();
    final ByteBuffer bc = ByteBuffer.allocate (buf.length + 100);
    bc.position (100);
    bc.put (buf, 0, 10);
    bc.flip ();
    bc.position (100);
    final BufferHandle clientHandle = new SimpleBufferHandle (bc);
    final ByteBuffer rest = ByteBuffer.wrap (buf);
    rest.position (10);
    final HttpHeaderReader reader = getReader (clientHandle);
    reader.readHeader ();
    writeTo.write (rest);
  }

  private void testLargeHeader () throws IOException
  {
    final HttpHeader header = getLargeHttpHeader ();
    final BufferHandle clientHandle = new CacheBufferHandle (bufferHandler);
    final HttpHeaderReader reader = getReader (clientHandle);
    reader.readHeader ();
    final ByteBuffer buf = ByteBuffer.wrap (header.getBytes ());
    writeTo.write (buf);
  }

  private HttpHeaderReader getReader (final BufferHandle clientHandle)
  {
    listener.waitForReady ();
    return new HttpHeaderReader (readFrom, clientHandle, nioHandler, trafficLogger, true, false, listener);
  }

  private BufferHandle getSimpleHeaderBuffer ()
  {
    final HttpHeader header = getSimpleHttpHeader ();
    final ByteBuffer buf = ByteBuffer.wrap (header.getBytes ());
    return new SimpleBufferHandle (buf);
  }

  private HttpHeader getLargeHttpHeader ()
  {
    final HttpHeader header = getSimpleHttpHeader ();
    final char [] chars = new char [5000];
    Arrays.fill (chars, 'A');
    final String val = new String (chars);
    header.addHeader ("Large", val);
    header.addHeader ("Last", "Last");
    return header;
  }

  private HttpHeader getSimpleHttpHeader ()
  {
    final HttpHeader header = new HttpHeader ();
    header.setRequestLine ("GET http://localhost:" + PORT + "/ HTTP/1.1");
    header.addHeader ("Host", "localhost");
    return header;
  }

  private class TestListener implements HttpHeaderListener
  {
    private final Semaphore latch = new Semaphore (1);

    private void waitForReady ()
    {
      latch.acquireUninterruptibly ();
    }

    public void httpHeaderRead (final HttpHeader header,
                                final BufferHandle bh,
                                final boolean keepalive,
                                final boolean isChunked,
                                final long dataSize)
    {
      System.out.print ("read a header\n" + header);
      latch.release ();
    }

    public void timeout ()
    {
      latch.release ();
      throw new RuntimeException ("Connection timed out...");
    }

    public void failed (final Exception e)
    {
      latch.release ();
      throw new RuntimeException ("Connection failed...", e);
    }

    public void closed ()
    {
      latch.release ();
      throw new RuntimeException ("Connection closed...");
    }
  }
}
