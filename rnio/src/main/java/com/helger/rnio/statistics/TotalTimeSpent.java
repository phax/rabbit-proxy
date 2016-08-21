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
package com.helger.rnio.statistics;

/**
 * Information about total time spent on a group of tasks.
 * <p>
 * This class is not thread safe.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class TotalTimeSpent
{

  private long successful = 0;
  private long failures = 0;
  private long totalMillis = 0;

  /**
   * Update this information with data from the newly completed task.
   *
   * @param ce
   *        the CompletionEntry that we want to update our information with
   */
  public void update (final CompletionEntry ce)
  {
    if (ce.wasOk)
      successful++;
    else
      failures++;
    totalMillis += ce.timeSpent;
  }

  /**
   * Get the number of successfully completed jobs.
   *
   * @return the number of successful jobs
   */
  public long getSuccessful ()
  {
    return successful;
  }

  /**
   * Get the number of failed jobs.
   *
   * @return the number of unsuccessful jobs
   */
  public long getFailures ()
  {
    return failures;
  }

  /**
   * Get the total time spent doing this kind of task.
   *
   * @return the total time take for all jobs
   */
  public long getTotalMillis ()
  {
    return totalMillis;
  }
}
