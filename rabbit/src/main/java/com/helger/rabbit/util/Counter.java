package com.helger.rabbit.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class counts different messages
 */
public class Counter
{
  // All the messages we count.
  private final ConcurrentMap <String, AtomicInteger> counters = new ConcurrentHashMap <> ();

  /**
   * Increase a logentry.
   * 
   * @param log
   *        the event to increase
   */
  public void inc (final String log)
  {
    final AtomicInteger l = counters.putIfAbsent (log, new AtomicInteger ());
    if (l != null)
      l.incrementAndGet ();
  }

  /**
   * Get all events
   * 
   * @return an Set of all events
   */
  public Set <String> keys ()
  {
    return counters.keySet ();
  }

  /**
   * Get the current count for an event.
   * 
   * @param key
   *        the event were intrested in
   * @return the current count of event.
   */
  public int get (final String key)
  {
    final AtomicInteger l = counters.get (key);
    if (l == null)
      return 0;
    return l.get ();
  }
}
