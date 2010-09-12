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
package org.apache.qpid.example;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.apache.qpid.client.AMQAnyDestination;
import org.apache.qpid.client.AMQConnection;
import org.slf4j.Logger;

public class Drain extends OptionParser
{
       
    static final Option FOREVER = new Option("f",
            "forever",
            "ignore timeout and wait forever",
            null,
            null,
            Boolean.class);

    static final Option COUNT = new Option ("c",
            "count",
            "read c messages, then exit",
            "COUNT",
            "0",
            Integer.class);
                                                

    static 
    {        
        optDefs.add(BROKER);
        optDefs.add(HELP);
        optDefs.add(TIMEOUT);
        optDefs.add(FOREVER);
        optDefs.add(COUNT);
        optDefs.add(CON_OPTIONS);
        optDefs.add(BROKER_OPTIONS);
    }
    
    public Drain(String[] args, String usage, String desc) throws Exception
    {   
        super(args, usage, desc);        
        
        Connection con = createConnection();
        con.start();
        Session ssn = con.createSession(false,Session.AUTO_ACKNOWLEDGE);     
        Destination dest = new AMQAnyDestination(address);
        MessageConsumer consumer = ssn.createConsumer(dest);
        Message msg;
        
        long timeout = -1;        
        int count = 0;
        int i = 0;
        
        if (containsOp(TIMEOUT)) { timeout = Integer.parseInt(getOp(TIMEOUT))*1000; }
        if (containsOp(FOREVER)) { timeout = 0; }
        if (containsOp(COUNT)) { count = Integer.parseInt(getOp(COUNT)); }
        
        while ((msg = consumer.receive(timeout)) != null)
        {
            System.out.println("\n------------- Msg -------------");
            System.out.println(msg);
            System.out.println("-------------------------------\n");

            if (count > 0) {
                if (++i == count) {
                    break;                    
                }               
            }            
        }
        
        ssn.close();
        con.close();
    }
   
    public static void main(String[] args) throws Exception
    {
        String u = "Usage: drain [OPTIONS] 'ADDRESS'";
        String d = "Drains messages from the specified address."; 
            
        new Drain(args,u,d);        
    }
}
