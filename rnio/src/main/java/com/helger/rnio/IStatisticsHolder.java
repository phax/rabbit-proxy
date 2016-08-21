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

import java.util.List;
import java.util.Map;

import com.helger.rnio.statistics.CompletionEntry;
import com.helger.rnio.statistics.TotalTimeSpent;

/**
 * A holder of statistics for tasks.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public interface IStatisticsHolder
{

  /**
   * A new task is put in the queue, waiting to be handled.
   *
   * @param ti
   *        the identifier of the new task.
   */
  void addPendingTask (ITaskIdentifier ti);

  /**
   * A pending task is about to be run.
   *
   * @param ti
   *        the identifier of the task that will start to run.
   */
  void changeTaskStatusToRunning (ITaskIdentifier ti);

  /**
   * A task has been completed.
   *
   * @param ti
   *        the identifier of the task that has completed.
   * @param wasOk
   *        true if the task completed without errors, false otherwise.
   * @param timeSpent
   *        wall clock time spent on the task.
   */
  void changeTaskStatusToFinished (ITaskIdentifier ti, boolean wasOk, long timeSpent);

  /**
   * Get information about the currently pending tasks.
   *
   * @return a mapping from group ids to the task identifiers
   */
  Map <String, List <ITaskIdentifier>> getPendingTasks ();

  /**
   * Get information about the currently running tasks.
   *
   * @return a mapping from group ids to the task identifiers
   */
  Map <String, List <ITaskIdentifier>> getRunningTasks ();

  /**
   * Get information about the most recent completed tasks
   *
   * @return a mapping from group ids to the task identifiers
   */
  Map <String, List <CompletionEntry>> getLatest ();

  /**
   * Get information about the longest running task.
   *
   * @return a mapping from group ids to the task identifiers
   */
  Map <String, List <CompletionEntry>> getLongest ();

  /**
   * Get the total time spent for each task.
   *
   * @return a mapping from group ids to the task identifiers
   */
  Map <String, TotalTimeSpent> getTotalTimeSpent ();
}
