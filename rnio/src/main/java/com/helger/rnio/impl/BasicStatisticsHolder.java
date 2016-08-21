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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.helger.rnio.StatisticsHolder;
import com.helger.rnio.TaskIdentifier;
import com.helger.rnio.statistics.CompletionEntry;
import com.helger.rnio.statistics.TotalTimeSpent;

/**
 * A holder of statistics for tasks.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class BasicStatisticsHolder implements StatisticsHolder
{
  // Map is group id to TaskIdentifier
  private final Map <String, List <TaskIdentifier>> pendingTasks = new HashMap<> ();

  // Map is group id to TaskIdentifier
  private final Map <String, List <TaskIdentifier>> runningTasks = new HashMap<> ();

  private final int maxLatest = 10;
  // Map is group id to CompletionEntry
  private final Map <String, List <CompletionEntry>> latest = new HashMap<> ();

  private final int maxLongest = 10;
  // Map is group id to CompletionEntry
  private final Map <String, List <CompletionEntry>> longest = new HashMap<> ();

  private final Map <String, TotalTimeSpent> total = new HashMap<> ();

  private <T> List <T> getList (final String id, final Map <String, List <T>> tasks)
  {
    List <T> ls = tasks.get (id);
    if (ls == null)
    {
      ls = new ArrayList<> ();
      tasks.put (id, ls);
    }
    return ls;
  }

  private void addTask (final TaskIdentifier ti, final Map <String, List <TaskIdentifier>> tasks)
  {
    getList (ti.getGroupId (), tasks).add (ti);
  }

  private void removeTask (final TaskIdentifier ti, final Map <String, List <TaskIdentifier>> tasks)
  {
    final List <TaskIdentifier> ls = tasks.get (ti.getGroupId ());
    if (ls == null)
      throw new NullPointerException ("No pending taks for group: " + ti.getGroupId ());
    if (!ls.remove (ti))
      throw new IllegalArgumentException ("Given task was not pending: " + ti);
  }

  public synchronized void addPendingTask (final TaskIdentifier ti)
  {
    addTask (ti, pendingTasks);
  }

  public synchronized void changeTaskStatusToRunning (final TaskIdentifier ti)
  {
    removeTask (ti, pendingTasks);
    addTask (ti, runningTasks);
  }

  public synchronized void changeTaskStatusToFinished (final TaskIdentifier ti,
                                                       final boolean wasOk,
                                                       final long timeSpent)
  {
    removeTask (ti, runningTasks);
    final CompletionEntry ce = new CompletionEntry (ti, wasOk, timeSpent);
    addToLatest (ce);
    addToLongest (ce);
    addToTotal (ce);
  }

  private void addToLatest (final CompletionEntry ce)
  {
    final List <CompletionEntry> ls = getList (ce.ti.getGroupId (), latest);
    ls.add (ce);
    if (ls.size () > maxLatest)
      ls.remove (0);
  }

  private void addToLongest (final CompletionEntry ce)
  {
    final List <CompletionEntry> ls = getList (ce.ti.getGroupId (), longest);
    if (ls.isEmpty ())
    {
      ls.add (ce);
    }
    else
      if (addSorted (ce, ls))
      {
        if (ls.size () > maxLongest)
          ls.remove (ls.size () - 1);
      }
  }

  private boolean addSorted (final CompletionEntry ce, final List <CompletionEntry> ls)
  {
    final int s = ls.size ();
    for (int i = 0; i < s; i++)
    {
      if (ce.timeSpent > ls.get (i).timeSpent)
      {
        ls.add (i, ce);
        return true;
      }
    }
    if (s < maxLongest)
    {
      ls.add (ce);
      return true;
    }
    return false;
  }

  private void addToTotal (final CompletionEntry ce)
  {
    TotalTimeSpent tts = total.get (ce.ti.getGroupId ());
    if (tts == null)
    {
      tts = new TotalTimeSpent ();
      total.put (ce.ti.getGroupId (), tts);
    }
    tts.update (ce);
  }

  private <K, V> Map <K, List <V>> copy (final Map <K, List <V>> m)
  {
    final Map <K, List <V>> ret = new HashMap<> ();
    for (final Map.Entry <K, List <V>> me : m.entrySet ())
      ret.put (me.getKey (), new ArrayList<> (me.getValue ()));
    return ret;
  }

  public synchronized Map <String, List <TaskIdentifier>> getPendingTasks ()
  {
    return copy (pendingTasks);
  }

  public synchronized Map <String, List <TaskIdentifier>> getRunningTasks ()
  {
    return copy (runningTasks);
  }

  public synchronized Map <String, List <CompletionEntry>> getLatest ()
  {
    return copy (latest);
  }

  public synchronized Map <String, List <CompletionEntry>> getLongest ()
  {
    return copy (longest);
  }

  public synchronized Map <String, TotalTimeSpent> getTotalTimeSpent ()
  {
    return Collections.unmodifiableMap (total);
  }
}
