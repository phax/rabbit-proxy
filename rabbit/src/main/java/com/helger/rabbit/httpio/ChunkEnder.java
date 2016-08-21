package com.helger.rabbit.httpio;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import com.helger.rabbit.io.BufferHandle;
import com.helger.rabbit.io.SimpleBufferHandle;
import com.helger.rabbit.util.TrafficLogger;
import com.helger.rnio.INioHandler;

/**
 * A class that sends the chunk ending (with an empty footer).
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ChunkEnder
{
  private static final byte [] CHUNK_ENDING = new byte [] { '0', '\r', '\n', '\r', '\n' };

  /**
   * Send the chunk ending block.
   * 
   * @param channel
   *        the Channel to send the chunk ender to
   * @param nioHandler
   *        the NioHandler to use for network operations
   * @param tl
   *        the TrafficLogger to update with network statistics
   * @param bsl
   *        the listener that will be notified when the sending is complete
   */
  public void sendChunkEnding (final SocketChannel channel,
                               final INioHandler nioHandler,
                               final TrafficLogger tl,
                               final BlockSentListener bsl)
  {
    final ByteBuffer bb = ByteBuffer.wrap (CHUNK_ENDING);
    final BufferHandle bh = new SimpleBufferHandle (bb);
    final BlockSender bs = new BlockSender (channel, nioHandler, tl, bh, false, bsl);
    bs.write ();
  }
}
