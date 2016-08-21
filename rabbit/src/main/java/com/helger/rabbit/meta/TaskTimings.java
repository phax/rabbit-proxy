package com.helger.rabbit.meta;

import java.util.List;
import java.util.Map;

import com.helger.rabbit.proxy.HtmlPage;
import com.helger.rnio.NioHandler;
import com.helger.rnio.StatisticsHolder;
import com.helger.rnio.TaskIdentifier;
import com.helger.rnio.statistics.CompletionEntry;
import com.helger.rnio.statistics.TotalTimeSpent;

/**
 * A page that shows the currently open web connections.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class TaskTimings extends BaseMetaHandler
{
  @Override
  protected String getPageHeader ()
  {
    return "Task Timing Information";
  }

  /** Add the page information */
  @Override
  protected PageCompletion addPageInformation (final StringBuilder sb)
  {
    addStatus (sb);
    return PageCompletion.PAGE_DONE;
  }

  private void addStatus (final StringBuilder sb)
  {
    final NioHandler nio = con.getNioHandler ();
    final StatisticsHolder stats = nio.getTimingStatistics ();

    appendTable (sb, "Pending tasks", stats.getPendingTasks ());
    appendTable (sb, "Runing tasks", stats.getRunningTasks ());

    appendTotalTimes (sb, "Total time spent", stats.getTotalTimeSpent ());

    appendCompletion (sb, "Latest completed tasks", stats.getLatest ());
    appendCompletion (sb, "Longest completed tasks", stats.getLongest ());
  }

  private String getRowColor (final int row)
  {
    final boolean odd = (row & 1) == 1;
    return odd ? "#EEFFFF" : "#DDDDFF";
  }

  private void appenTableHeader (final StringBuilder sb,
                                 final String title,
                                 final int [] widths,
                                 final String [] titles)
  {
    sb.append (title);
    sb.append (HtmlPage.getTableHeader (100, 1));
    sb.append (HtmlPage.getTableTopicRow ());
    for (int i = 0; i < widths.length; i++)
    {
      sb.append ("<th width=\"");
      sb.append (widths[i]);
      sb.append ("%\">");
      sb.append (titles[i]);
      sb.append ("</th>");
    }
  }

  private void appendTable (final StringBuilder sb, final String title, final Map <String, List <TaskIdentifier>> m)
  {
    appenTableHeader (sb, title, new int [] { 30, 70 }, new String [] { "Group", "Information" });
    int row = 0;
    for (final Map.Entry <String, List <TaskIdentifier>> me : m.entrySet ())
    {
      for (final TaskIdentifier ti : me.getValue ())
      {
        sb.append ("<tr bgcolor=\"" +
                   getRowColor (row) +
                   "\"><td>" +
                   ti.getGroupId () +
                   "</td><td>" +
                   ti.getDescription () +
                   "</td></tr>\n");
      }
      row++;
    }
    sb.append ("</table><br>\n");
  }

  private void appendCompletion (final StringBuilder sb,
                                 final String title,
                                 final Map <String, List <CompletionEntry>> m)
  {
    appenTableHeader (sb, title, new int [] { 30, 50, 10, 10 }, new String [] { "Group", "Information", "Ok", "Time" });
    int row = 0;
    for (final Map.Entry <String, List <CompletionEntry>> me : m.entrySet ())
    {
      for (final CompletionEntry ce : me.getValue ())
      {
        sb.append ("<tr bgcolor=\"" +
                   getRowColor (row) +
                   "\"><td>" +
                   ce.ti.getGroupId () +
                   "</td><td>" +
                   ce.ti.getDescription () +
                   "</td><td>" +
                   ce.wasOk +
                   "</td><td>" +
                   ce.timeSpent +
                   "</td></tr>\n");
      }
      row++;
    }
    sb.append ("</table><br>\n");
  }

  private void appendTotalTimes (final StringBuilder sb, final String title, final Map <String, TotalTimeSpent> m)
  {
    appenTableHeader (sb,
                      title,
                      new int [] { 70, 10, 10, 10 },
                      new String [] { "Group", "Success", "Failures", "Total Time" });
    final int row = 0;
    for (final Map.Entry <String, TotalTimeSpent> me : m.entrySet ())
    {
      final TotalTimeSpent tts = me.getValue ();
      sb.append ("<tr bgcolor=\"" +
                 getRowColor (row) +
                 "\"><td>" +
                 me.getKey () +
                 "</td><td>" +
                 tts.getSuccessful () +
                 "</td><td>" +
                 tts.getFailures () +
                 "</td><td>" +
                 tts.getTotalMillis () +
                 "</td></tr>\n");

    }
    sb.append ("</table><br>\n");
  }
}
