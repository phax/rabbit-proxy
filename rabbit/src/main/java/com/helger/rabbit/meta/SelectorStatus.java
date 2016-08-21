package com.helger.rabbit.meta;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Date;
import java.util.Set;

import com.helger.rabbit.proxy.HtmlPage;
import com.helger.rnio.ISelectorVisitor;

/**
 * A status page for the proxy.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class SelectorStatus extends BaseMetaHandler
{

  @Override
  protected String getPageHeader ()
  {
    return "Selector status";
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
    sb.append ("Status of selector at: ");
    sb.append (new Date ());
    sb.append ("<p>\n");

    con.getNioHandler ().visitSelectors (new ISelectorVisitor ()
    {
      int count = 0;

      public void selector (final Selector selector)
      {
        final boolean odd = (count & 1) == 1;
        final String trColor = odd ? "#EE8888" : "#DD6666";
        final String tdColor = odd ? "#EEFFFF" : "#DDDDFF";
        appendKeys (sb, selector.selectedKeys (), "Selected key", trColor, tdColor);
        appendKeys (sb, selector.keys (), "Registered key", trColor, tdColor);
        count++;
      }

      public void end ()
      {}
    });
  }

  private void appendKeys (final StringBuilder sb,
                           final Set <SelectionKey> sks,
                           final String header,
                           final String thColor,
                           final String trColor)
  {
    sb.append (HtmlPage.getTableHeader (100, 1));
    sb.append ("<tr bgcolor=\"").append (thColor).append ("\">");
    sb.append ("<th width=\"20%\">").append (header).append ("</th>");
    sb.append ("<th>channel</th>" +
               "<th width=\"50%\">Attachment</th>" +
               "<th>Interest</th>" +
               "<th>Ready</th>" +
               "</tr>\n");
    for (final SelectionKey sk : sks)
    {
      sb.append ("<tr bgcolor=\"").append (trColor).append ("\"><td>");
      sb.append (sk.toString ());
      sb.append ("</td><td>");
      sb.append (sk.channel ());
      sb.append ("</td><td>");
      sb.append (sk.attachment ());
      sb.append ("</td><td>");
      final boolean valid = sk.isValid ();
      appendOpString (sb, valid ? sk.interestOps () : 0);
      sb.append ("</td><td>");
      appendOpString (sb, valid ? sk.readyOps () : 0);
      sb.append ("</td></tr>\n");
    }
    sb.append ("</table>\n<br>\n");
  }

  private void appendOpString (final StringBuilder sb, final int op)
  {
    sb.append ((op & SelectionKey.OP_READ) != 0 ? "R" : "_");
    sb.append ((op & SelectionKey.OP_WRITE) != 0 ? "W" : "_");
    sb.append ((op & SelectionKey.OP_CONNECT) != 0 ? "C" : "_");
    sb.append ((op & SelectionKey.OP_ACCEPT) != 0 ? "A" : "_");
  }
}
