/*
 * Software to build and maintain SCOPe, https://scop.berkeley.edu/
 *
 * Copyright (C) 2007-2018 The Regents of the University of California
 *
 * For feedback, mailto:scope@compbio.berkeley.edu
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * Version 2.1 of the License, or (at your option) any later version.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */
package gov.lbl.scop.app;

import java.sql.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import org.strbio.io.*;
import org.strbio.local.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.local.*;

/**
   Notify users with any messages in the SCOP queue

   <pre>
   Version 1.1, 11/17/10 - ported Notify from PCAP to SCOP
   Version 1.0, 10/1/07 - original version
   </pre>
   
   @version 1.1
   @author JMC
*/
public class Notify {
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();
            ResultSet rs, rs2;

            // regexps to recognize a legal parameter and a message subject
            Pattern reParam = Pattern.compile("%-?\\d*\\.?\\d*[dfs]");
            Pattern reSubject = Pattern.compile("^Subject:\\s*(.*?)\n");

            // hack to reduce chance of race condition
            Vector<Integer> todo = new Vector<Integer>();
            stmt.executeUpdate("update notify_message_queue set sent_time=\"0000-00-00\" where sent_time is null");
            rs = stmt.executeQuery("select id from notify_message_queue where sent_time=\"0000-00-00\"");
            while (rs.next()) {
                int id = rs.getInt(1);
                todo.add(new Integer(id));
                stmt2.executeUpdate("update notify_message_queue set sent_time=now() where id="+id);
            }

            // process each element in todo list
            for (Integer idInt : todo) {
                int id = idInt.intValue();

                rs = stmt.executeQuery("select q.user_id, t.template from notify_message_queue q, notify_template t where q.template_id=t.id and q.id="+id);
                if (rs.next()) {
                    int userID = rs.getInt(1);
                    String template = rs.getString(2);

                    // do parameter substitution
                    rs = stmt.executeQuery("select parameter from notify_parameter where message_id="+id+" order by id");
                    while (rs.next()) {
                        String param = rs.getString(1);

                        Matcher m = reParam.matcher(template);
                        if (m.find()) {
                            String begin = "";
                            if (m.start() > 0)
                                begin = template.substring(0,m.start());
                            String end = "";
                            if (m.end() < template.length())
                                end = template.substring(m.end());
                            char paramType = template.charAt(m.end()-1);
                            StringWriter sw = new StringWriter();
                            PrintfWriter pw = new PrintfWriter(sw);
                            if (paramType=='s') {
                                pw.printf(m.group(),param);
                            }
                            else if (paramType=='d') {
                                pw.printf(m.group(),StringUtil.atol(param));
                            }
                            else if (paramType=='f') {
                                pw.printf(m.group(),StringUtil.atod(param));
                            }
                            else {
                                throw new IllegalArgumentException("pattern format error");
                            }

                            pw.flush();
                            template = begin+sw.toString()+end;
                        }
                    }

                    // parse out subject, if present
                    String msgBody, subject;

                    Matcher m = reSubject.matcher(template);
                    if (m.find()) {
                        subject = m.group(1);
                        msgBody = template.substring(m.end());
                    }
                    else {
                        subject = null;
                        msgBody = template;
                    }

                    SCOP.mailUser(userID,subject,msgBody);

                    // copy any users listed
                    rs = stmt.executeQuery("select user_id from notify_cc where message_id="+id);
                    while (rs.next()) {
                        userID = rs.getInt(1);
                        SCOP.mailUser(userID,subject,msgBody);
                    }
                }
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
