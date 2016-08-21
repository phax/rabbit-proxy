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
package com.helger.rnio.impl;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.helger.rnio.AcceptHandler;
import com.helger.rnio.ConnectHandler;
import com.helger.rnio.NioHandler;
import com.helger.rnio.ReadHandler;
import com.helger.rnio.SelectorVisitor;
import com.helger.rnio.SocketChannelHandler;
import com.helger.rnio.StatisticsHolder;
import com.helger.rnio.TaskIdentifier;
import com.helger.rnio.WriteHandler;

/**
 * An implementation of NioHandler that runs several selector threads.
 * <p>
 * Any tasks that should run on a background thread are passed to the
 * {@link ExecutorService} that was given in the constructor.
 * <p>
 * This class will log using the "org.khelekore.rnio" {@link Logger}.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class MultiSelectorNioHandler implements NioHandler
{
  /** The executor service. */
  private final ExecutorService executorService;
  private final List <SingleSelectorRunner> selectorRunners;
  private final Logger logger = Logger.getLogger ("org.khelekore.rnio");
  private final StatisticsHolder stats;
  private final Long defaultTimeout;
  private int nextIndex = 0;

  /**
   * Create a new MultiSelectorNioHandler that runs background tasks on the
   * given executor and has a specified number of selectors.
   *
   * @param executorService
   *        the ExecutorService to use for this NioHandler
   * @param stats
   *        the StatisticsHolder to use for this NioHandler
   * @param numSelectors
   *        the number of threads that this NioHandler will use
   * @param defaultTimeout
   *        the default timeout value for this NioHandler
   * @throws IOException
   *         if the selectors can not be started
   */
  public MultiSelectorNioHandler (final ExecutorService executorService,
                                  final StatisticsHolder stats,
                                  final int numSelectors,
                                  final Long defaultTimeout) throws IOException
  {
    this.executorService = executorService;
    this.stats = stats;

    if (numSelectors < 1)
    {
      final String err = "Must have at least one selector: " + numSelectors;
      throw new IllegalArgumentException (err);
    }
    selectorRunners = new ArrayList<> (numSelectors);
    for (int i = 0; i < numSelectors; i++)
      selectorRunners.add (new SingleSelectorRunner (executorService));
    if (defaultTimeout != null && defaultTimeout.longValue () <= 0)
    {
      final String err = "Default timeout may not be zero or negative";
      throw new IllegalArgumentException (err);
    }
    this.defaultTimeout = defaultTimeout;
  }

  public void start (final ThreadFactory tf)
  {
    for (final SingleSelectorRunner ssr : selectorRunners)
      ssr.start (tf);
  }

  public void shutdown ()
  {
    final Thread t = new Thread ( () -> {
      executorService.shutdown ();
      for (final SingleSelectorRunner ssr : selectorRunners)
        ssr.shutdown ();
    });
    t.start ();
  }

  public Long getDefaultTimeout ()
  {
    if (defaultTimeout == null)
      return null;
    return Long.valueOf (System.currentTimeMillis () + defaultTimeout.longValue ());
  }

  public boolean isSelectorThread ()
  {
    for (final SingleSelectorRunner ssr : selectorRunners)
      if (ssr.isSelectorThread ())
        return true;
    return false;
  }

  public void runThreadTask (final Runnable r, final TaskIdentifier ti)
  {
    stats.addPendingTask (ti);
    executorService.execute (new StatisticsCollector (stats, r, ti));
  }

  private SingleSelectorRunner getSelectorRunner ()
  {
    int index;
    synchronized (this)
    {
      index = nextIndex++;
      nextIndex %= selectorRunners.size ();
    }
    return selectorRunners.get (index);
  }

  /**
   * Run a task on one of the selector threads. The task will be run sometime in
   * the future.
   *
   * @param channel
   *        the channel to run the task on
   * @param sr
   *        the task to run on the main thread.
   */
  private void runSelectorTask (final SelectableChannel channel, final SelectorRunnable sr)
  {
    // If the channel is already being served by someone, favor that one.
    for (final SingleSelectorRunner ssr : selectorRunners)
    {
      if (ssr.handlesChannel (channel))
      {
        ssr.runSelectorTask (sr);
        return;
      }
    }
    // Put it on any selector
    final SingleSelectorRunner ssr = getSelectorRunner ();
    ssr.runSelectorTask (sr);
  }

  public void waitForRead (final SelectableChannel channel, final ReadHandler handler)
  {
    if (logger.isLoggable (Level.FINEST))
      logger.fine ("Waiting for read for: channel: " + channel + ", handler: " + handler);
    runSelectorTask (channel, ssr -> ssr.waitForRead (channel, handler));
  }

  public void waitForWrite (final SelectableChannel channel, final WriteHandler handler)
  {
    if (logger.isLoggable (Level.FINEST))
      logger.fine ("Waiting for write for: channel: " + channel + ", handler: " + handler);
    runSelectorTask (channel, ssr -> ssr.waitForWrite (channel, handler));
  }

  public void waitForAccept (final SelectableChannel channel, final AcceptHandler handler)
  {
    if (logger.isLoggable (Level.FINEST))
      logger.fine ("Waiting for accept for: channel: " + channel + ", handler: " + handler);
    runSelectorTask (channel, ssr -> ssr.waitForAccept (channel, handler));
  }

  public void waitForConnect (final SelectableChannel channel, final ConnectHandler handler)
  {
    runSelectorTask (channel, ssr -> ssr.waitForConnect (channel, handler));
  }

  public void cancel (final SelectableChannel channel, final SocketChannelHandler handler)
  {
    for (final SingleSelectorRunner sr : selectorRunners)
    {
      sr.runSelectorTask (ssr -> ssr.cancel (channel, handler));
    }
  }

  public void close (final SelectableChannel channel)
  {
    for (final SingleSelectorRunner sr : selectorRunners)
    {
      sr.runSelectorTask (ssr -> ssr.close (channel));
    }
  }

  public void visitSelectors (final SelectorVisitor visitor)
  {
    // TODO: do we need to run on the respective threads?
    for (final SingleSelectorRunner sr : selectorRunners)
      sr.visit (visitor);
    visitor.end ();
  }

  // TODO: where does this belong?
  public StatisticsHolder getTimingStatistics ()
  {
    return stats;
  }
}
