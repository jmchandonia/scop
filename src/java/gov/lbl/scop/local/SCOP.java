/*
 * Software to build and maintain SCOPe, https://scop.berkeley.edu/
 *
 * Copyright (C) 2012-2018 The Regents of the University of California
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
package gov.lbl.scop.local;

import java.sql.*;
import java.io.*;
import java.util.*;

import javax.mail.*;
import javax.mail.internet.*;

/**
   Class to access features of local SCOP environment.
  
   <pre>
   Version 1.1, 11/17/10 - added mail capability based on PCAP
   Version 1.0, 8/7/8
   </pre>

   @version 1.1
   @author JMC
*/
public class SCOP {
    /**
       get local property, or null if not defined.
       These are stored in scop.properties
    */
    public static String getProperty(String key) {
        Properties prop = new Properties();
        try {
            SCOP x = new SCOP();
            Class myClass = x.getClass();
            prop.load(myClass.getResourceAsStream("scop.properties"));
        }
        catch (IOException e) {
        }
        catch (SecurityException e) {
        }
        String value = prop.getProperty(key, null);
        return value;
    }

    /**
       Send user email, if we have their address.
       Subject will be prepended with [lims] for easier sorting.
       Returns 0 if success, 1 if unknown email, 2 if other error
    */
    public static int mailUser(int userID, String subject, String body) {
        String email = LocalSQL.getEmail(userID);
        if (email==null)
            return 1;

        Properties props = System.getProperties();
        String host = getProperty("mail.smtp.host");
        if (host==null)
            host = "localhost";
        props.put("mail.smtp.host", host);
        try {
            Session session = Session.getInstance(props, null);
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress("SCOP daemon <JMChandonia@lbl.gov>"));
            msg.setRecipients(Message.RecipientType.TO,
                              InternetAddress.parse(email, false));
            if (subject==null)
                subject = "SCOP daemon notification";
            msg.setSubject("[SCOPD] "+subject);
            msg.setText(body);
            Transport.send(msg);
            return 0;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return 2;
    }
}
