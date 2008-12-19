/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */


package org.apache.qpid.commands;


import org.apache.qpid.commands.objects.QueueObject;
import org.apache.qpid.commands.objects.ObjectNames;
import org.apache.qpid.utils.JMXinfo;

import javax.management.ObjectName;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.MBeanAttributeInfo;
import java.util.StringTokenizer;
import java.util.Set;
import java.util.Iterator;
import java.util.List;
import java.io.InputStreamReader;
import java.io.BufferedReader;

/**
 * Created by IntelliJ IDEA.
 * User: lahiru
 * Date: Aug 6, 2008
 * Time: 5:35:05 PM
 * To change this template use File | Settings | File Templates.
 */
public class Commandmove extends Command {
    private String object;
    private String name1 = null, name2 = null, vhost1 = null, vhost2 = null, method1 = null, method2 = null;    //target and starting queue specifications happen with these options
    private int number = 0;
    private QueueObject queue1, queue2;
    private MBeanServerConnection mbsc;
    private ObjectName queue;
    private int fmid = 0, tmid = 0;

    public Commandmove(JMXinfo info, String name) {
        super(info, name);
        this.mbsc = info.getmbserverconnector();
        this.queue1 = new QueueObject(mbsc);
//        this.queue2 = new QueueObject(mbsc);
        this.method1 = "moveMessages";
        this.method2 = "getMessagesOnTheQueue";

    }

    public void movemessages() {
        Set set = null;
        queue1.setQueryString(this.object, this.name1, this.vhost1);
//        queue2.setQueryString(this.object, this.name2, this.vhost2);
        set = queue1.returnObjects();
        List messageidlist = null;
        Long frommessageid = null, tomessageid, middle;
        int temp = 0;
        if (queue1.getSet().size() != 0) {                   // find the queue 
            Iterator it = set.iterator();
            this.queue = (ObjectName) it.next();
        } else {
            if (isname1() || isname2()) {                   // if the specified queue is not there in the broker

                echo("The Queue you have specified is not in the current broker");
                echo("");
            } else {
                printusage();
            }
        }
//        if(this.tmid == 0 || this.fmid == 0)
//        {
//            this.number = queue1.getmessagecount(this.queue);
//            echo("");
//            System.out.print("Do you want to delete all the messages from the Queue[Y/N] :");
//            InputStreamReader isr = new InputStreamReader(System.in);
//            BufferedReader br = new BufferedReader(isr);
//            try{
//                String s = br.readLine();
//                echo(s);
//                if(s.compareToIgnoreCase("y") != 0)
//                    return;
//            }catch(Exception ex)
//            {
//                ex.printStackTrace();
//            }
//
//        }
//        if(this.number > queue1.getmessagecount(this.queue))
//        {
//            System.out.println("Given number is Greater than the Queue Depth");
//            return;
//        }//if user doesn't specify -t option all the messages will be moved
//        Object[] params = {new Integer(this.number)};
//        String[] signature = {new String("java.lang.Integer")};
//        try{
//            messageidlist = (List)this.mbsc.invoke(queue,this.method2,params,signature);
//            Iterator it1 = messageidlist.iterator();
//            temp++;
//            do
//            {
//                middle = (Long)it1.next();
//                if(temp == 1)
//                    frommessageid = middle; // get the messageid of first message
//
//            }while(it1.hasNext());
//            tomessageid = middle;   // get the messageid of the last message
        try {
            Object[] params1 = {getfmid(), gettmid(), this.name2};
            String[] signature1 = {new String("long"), new String("long"), new String("java.lang.String")};
            this.mbsc.invoke(this.queue, this.method1, params1, signature1);

        } catch (Exception ex) {
            ex.printStackTrace();
            echo("Given messageId's might be wrong please run the view command and check messageId's you have given\n");
            echo("From MessageId should be greater than 0 and should less than To messageId");
        }


    }

    public void execute() {
        /* In here you it's easy to handle any number of otpions which are going to add with the list command which works
        with main option object or o
         */

        if (checkoptionsetting("object") || checkoptionsetting("o")) {
            String object = optionchecker("object");
            if (object == null) {
                object = optionchecker("o");
            }
            if (object.compareToIgnoreCase("queue") == 0)
                setobject(object);
            else {
                unrecognizeoption();
                echo("This command is only applicable for queue command so please start with queue");
            }
            if (checkoptionsetting("n2") && checkoptionsetting("n1")) {
                setname1(optionchecker("n1"));
                setname2(optionchecker("n2"));
            } else {
                echo("You have to specify both n1 and n2 option value to move a message");   /* when user forget to specify target or starting queue name */
                return;
            }

            if (checkoptionsetting("v1")) {

                setvhost1(optionchecker("v1"));
            }
            if (checkoptionsetting("tmid") && checkoptionsetting("fmid")) {
                String tmid = optionchecker("tmid");
                String fmid = optionchecker("fmid");


                settomessageIdandfrommessageId(removeSpaces(tmid), removeSpaces(fmid));
            } else {
                echo("You have to set from MessageId and to MessageId in order to move messages between queues");
                echo("To view MessageId's use <view> command with -n and -v options");
                return;
            }
            this.movemessages();

        } else if (checkoptionsetting("h") || checkoptionsetting("help"))
            printusage();
        else
            unrecognizeoption();
    }

    public void printusage() {
        echo("");
        echo("Usage:move [OPTION] ... [OBJECT TYPE]...\n");
        echo("Move the top most messages from the given queue object to the given destination object\n");
        echo("To specify the desired queues you have to give the virtualhost name and the queue name with following commands\n");
        echo("Where possible options include:\n");
        echo("        -v1                   Give the virtuallhost name from which queue you want to move messages");
        echo("        -n1                   Give the queue name which you want to move messages from");
        echo("        -n2                   Give the queue name of the destination queue");
        echo("        -tmid                 Give From MessageId you want to move from the Queue");
        echo("        -fmid                 Give To MessageId you want to move from the Queue");
        echo("        -h      --help        Display the help and back to the qpid-cli prompt\n");

    }

    private void setobject(String object) {
        this.object = object;
    }

    private void setname1(String name) {
        this.name1 = name;
    }

    private void setname2(String name) {
        this.name2 = name;
    }

    private boolean isname1() {
        if (this.name1 == null)
            return false;

        else
            return true;
    }

    private boolean isname2() {
        if (this.name2 == null)
            return false;

        else
            return true;
    }

    private void setvhost1(String vhost) {
        this.vhost1 = vhost;
    }
//    private void setvhost2(String vhost) {
//        this.vhost2 = vhost;
//    }

    private void setnumber(String number) {
        Integer i = new Integer(number);
        this.number = i.intValue();
    }

    private static String removeSpaces(String s) {
        StringTokenizer st = new StringTokenizer(s, " ", false);
        String t = "";
        while (st.hasMoreElements()) t += st.nextElement();
        return t;
    }

    private void settomessageIdandfrommessageId(String tmid, String fmid) {
        Integer i = new Integer(tmid);
        Integer j = new Integer(fmid);
        this.tmid = i.intValue();
        this.fmid = j.intValue();
    }

    public int gettmid() {
        return this.tmid;
    }

    public int getfmid() {
        return this.fmid;
    }

    public String getname1() {
        return this.name1;
    }

    public String getname2() {
        return this.name2;
    }

    public String getvhost() {
        return this.vhost1;
    }

    public String getobject() {
        return this.object;
    }
}
