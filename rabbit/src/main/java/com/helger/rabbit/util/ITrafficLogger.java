package com.helger.rabbit.util;

/**
 * A traffic LOGGER interface.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public interface ITrafficLogger
{
  /**
   * Log a read
   * 
   * @param read
   *        the number of bytes read.
   */
  void read (long read);

  /**
   * Get the number of read bytes.
   * 
   * @return the number of bytes that have been read
   */
  long read ();

  /**
   * Log a write
   * 
   * @param written
   *        the number of bytes written.
   */
  void write (long written);

  /**
   * Get the number of written bytes.
   * 
   * @return the number of bytes that have been written
   */
  long write ();

  /**
   * Log a file transfer.
   * 
   * @param transferred
   *        the number of bytes transferred.
   */
  void transferFrom (long transferred);

  /**
   * Get the number of bytes transferred from this resource.
   * 
   * @return the number of bytes that have been transferred
   */
  long transferFrom ();

  /**
   * Lot a file transfer.
   * 
   * @param transferred
   *        the number of bytes transferred.
   */
  void transferTo (long transferred);

  /**
   * Get the number of bytes transferred to this resourse.
   * 
   * @return the number of bytes that have been transferred
   */
  long transferTo ();

  /**
   * Clear the current log.
   */
  void clear ();

  /**
   * Add the current log into the other TrafficLogger.
   * 
   * @param other
   *        the traffic LOGGER to add this statistics
   */
  void addTo (ITrafficLogger other);
}
