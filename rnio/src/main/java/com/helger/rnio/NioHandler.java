/**
 * Copyright (c) 2010 Robert Olofsson.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the authors nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHORS AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHORS OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */
package com.helger.rnio;

import java.nio.channels.SelectableChannel;
import java.util.concurrent.ThreadFactory;

/**
 * A handler of nio based events.
 */
public interface NioHandler
{

  /**
   * Start handling operations.
   *
   * @param tf
   *        the thread factory to use when creating the selctor threads
   */
  void start (ThreadFactory tf);

  /**
   * Shutdown this task runner. This will make the NioHandler stop accepting new
   * thread tasks and it will close all channels that are still registerd (and
   * call closed () for the channel handlers).
   */
  void shutdown ();

  /**
   * Get the default timeout time for an operations started at this point in
   * time.
   *
   * @return the default timeout in milliseconds
   */
  Long getDefaultTimeout ();

  /**
   * Check if the current thread is one of the selector threads.
   *
   * @return true if the the current thread is a thread used for the selctor.
   *         false if the current thread is any other thread
   */
  boolean isSelectorThread ();

  /**
   * Run a task in a background thread. The task will be run sometime in the
   * future.
   *
   * @param r
   *        the task to run.
   * @param ti
   *        an identifier for the statistics
   */
  void runThreadTask (Runnable r, TaskIdentifier ti);

  /**
   * Install an event listener for read events. When the channels is ready the
   * ReadHandler.read () method will be called and read selection will be turned
   * off for the channel.
   *
   * @param channel
   *        the channel to wait for data on
   * @param handler
   *        the listener that will be notified when the chanel has data
   */
  void waitForRead (SelectableChannel channel, ReadHandler handler);

  /**
   * Install an event listener for write events. When the channel is ready the
   * WriteHandler.write () method will be called and write selection will be
   * turned off for the channel.
   *
   * @param channel
   *        the channel to write to
   * @param handler
   *        the listener that will be notified when the chanel is ready to be
   *        written
   */
  void waitForWrite (SelectableChannel channel, WriteHandler handler);

  /**
   * Install an event listener for accent events. When the channel is ready the
   * Accepthandler.accept () method will be called and accept selection will be
   * turned off for the channel.
   *
   * @param channel
   *        the channel to wait for data on
   * @param handler
   *        the listener that will be notified when the chanel has is ready for
   *        accepting new channels
   */
  void waitForAccept (SelectableChannel channel, AcceptHandler handler);

  /**
   * Install an event listener for connect events. When the channel is ready the
   * Connecthandler.connect () method will be called and connect selection will
   * be turned off for the channel.
   *
   * @param channel
   *        the channel that is trying to connect
   * @param handler
   *        the listener that will be notified when the chanel is ready to
   *        complete the connection
   */
  void waitForConnect (SelectableChannel channel, ConnectHandler handler);

  /**
   * Remove an event listener.
   *
   * @param channel
   *        the channel to remove the listener from
   * @param handler
   *        the listener to remove
   */
  void cancel (SelectableChannel channel, SocketChannelHandler handler);

  /**
   * Close the given channel. Closing a channel will cause
   * SocketChannelHandler.close () to be raised on any listeners for this
   * channel and will then cancel all selector interaction.
   *
   * @param channel
   *        the Channel to close
   */
  void close (SelectableChannel channel);

  /**
   * Visit all the selectors. This should really only be used for status
   * handling and/or debugging.
   *
   * @param visitor
   *        the listener that will be notified of the selector and channels
   */
  void visitSelectors (SelectorVisitor visitor);

  /**
   * Get the timing information for the thread tasks.
   *
   * @return the StatisticsHolder for this NioHandler
   */
  StatisticsHolder getTimingStatistics ();
}
