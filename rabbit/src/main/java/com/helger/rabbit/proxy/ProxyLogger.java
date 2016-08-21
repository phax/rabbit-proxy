package com.helger.rabbit.proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import com.helger.commons.url.SMap;

/**
 * A class to handle proxy logging.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ProxyLogger implements ConnectionLogger
{

  /** Output for accesses */
  private Logger accessLog;

  /** The format we write dates on. */
  private final SimpleDateFormat sdf = new SimpleDateFormat ("dd/MMM/yyyy:HH:mm:ss 'GMT'");

  /** The monitor for sdf. */
  private final Object sdfMonitor = new Object ();

  /** The distance to GMT in milis. */
  private final long offset;

  /** Create a new ProxyLogger. */
  public ProxyLogger ()
  {
    final TimeZone tz = sdf.getTimeZone ();
    final GregorianCalendar gc = new GregorianCalendar ();
    gc.setTime (new Date ());
    offset = tz.getOffset (gc.get (Calendar.ERA),
                           gc.get (Calendar.YEAR),
                           gc.get (Calendar.MONTH),
                           gc.get (Calendar.DAY_OF_MONTH),
                           gc.get (Calendar.DAY_OF_WEEK),
                           gc.get (Calendar.MILLISECOND));
  }

  /**
   * Get the distance to GMT in millis.
   *
   * @return the time offset
   */
  public long getOffset ()
  {
    return offset;
  }

  private Logger getLogger (final SMap config,
                            final String prefix,
                            final Formatter format,
                            final String... logDomains) throws IOException
  {
    final String log = config.get (prefix + "_log");
    String sl = config.get (prefix + "_size_limit");
    sl = sl == null ? "1" : sl.trim ();
    final int limit = Integer.parseInt (sl) * 1024 * 1024;
    final int numFiles = Integer.parseInt (config.get (prefix + "_num_files"), 10);
    sl = config.get (prefix + "_log_level");
    sl = sl != null ? sl : "INFO";
    final Level level = Level.parse (sl);

    final FileHandler fh = new FileHandler (log, limit, numFiles, true);
    fh.setFormatter (new SimpleFormatter ());
    Logger ret = null;
    for (final String logDomain : logDomains)
    {
      final Logger logger = Logger.getLogger (logDomain);
      logger.setLevel (level);
      logger.addHandler (fh);
      logger.setUseParentHandlers (false);
      if (ret == null)
        ret = logger;
    }

    if (format != null)
      fh.setFormatter (format);

    return ret;
  }

  /**
   * Configure this logger from the given properties
   *
   * @param config
   *        the properties to use for configuration.
   * @throws IOException
   *         if logging setup fails
   */
  public void setup (final SMap config) throws IOException
  {
    final String sysLogging = System.getProperty ("java.util.logging.config.file");
    if (sysLogging != null)
    {
      System.out.println ("Logging configure by system property");
    }
    else
    {
      final Logger eh = getLogger (config, "error", null, "rabbit", "org.khelekore.rnio");
      eh.info ("Log level set to: " + eh.getLevel ());
    }
    accessLog = getLogger (config, "access", new AccessFormatter (), "rabbit_access");
  }

  private static class AccessFormatter extends Formatter
  {
    @Override
    public String format (final LogRecord record)
    {
      return record.getMessage () + "\n";
    }
  }

  public void logConnection (final Connection con)
  {
    if (accessLog == null)
      return;

    final StringBuilder sb = new StringBuilder ();
    final Socket s = con.getChannel ().socket ();
    if (s != null)
    {
      final InetAddress ia = s.getInetAddress ();
      if (ia != null)
        sb.append (ia.getHostAddress ());
      else
        sb.append ("????");
    }
    sb.append (" - ");
    sb.append ((con.getUserName () != null ? con.getUserName () : "-"));
    sb.append (" ");
    final long now = System.currentTimeMillis ();
    final Date d = new Date (now - offset);
    synchronized (sdfMonitor)
    {
      sb.append (sdf.format (d));
    }
    sb.append (" \"");
    sb.append (con.getRequestLine ());
    sb.append ("\" ");
    sb.append (con.getStatusCode ());
    sb.append (" ");
    sb.append (con.getContentLength ());
    sb.append (" ");
    sb.append (con.getId ().toString ());
    sb.append (" ");
    sb.append ((con.getExtraInfo () != null ? con.getExtraInfo () : ""));

    accessLog.log (Level.INFO, sb.toString ());
  }
}
