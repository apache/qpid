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
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.client.AMQAnyDestination;

public class Spout extends OptionParser
{
        
    static final Option COUNT = new Option("c",
            "count",
            "stop after count messages have been sent, zero disables",
            "COUNT",
            "1",
            Integer.class);
        
    static final Option ID = new Option("i",
            "id",
            "use the supplied id instead of generating one",
            null,
            null,
            Boolean.class);
    
    static final Option CONTENT = new Option(null,
            "content",
            "specify textual content",
            "TEXT",
            null,
            Boolean.class);
    
    static final Option MSG_PROPERTY = new Option("P",
            "property",
            "specify message property",
            "NAME=VALUE",
            null,
            Boolean.class);    
    
    static final Option MAP_ENTRY = new Option("M",
            "map",
            "specify entry for map content",
            "KEY=VALUE",
            null,
            Boolean.class); 

    static 
    {        
        optDefs.add(BROKER);
        optDefs.add(HELP);
        optDefs.add(TIMEOUT);
        optDefs.add(COUNT);
        optDefs.add(MSG_PROPERTY);
        optDefs.add(MAP_ENTRY);
        optDefs.add(CONTENT);
        optDefs.add(CON_OPTIONS);
        optDefs.add(BROKER_OPTIONS);
    }
    
    public Spout(String[] args, String usage, String desc) throws Exception
    {   
        super(args, usage, desc);        
        
        Connection con = createConnection();
        con.start();
        Session ssn = con.createSession(false,Session.AUTO_ACKNOWLEDGE);     
        Destination dest = new AMQAnyDestination(address);
        MessageProducer producer = ssn.createProducer(dest);
        
        int count = Integer.parseInt(getOp(COUNT));
        
        for (int i=0; i < count; i++)
        {
            Message msg = createMessage(ssn);
            producer.send(msg);
            System.out.println("\n------------- Msg -------------");
            System.out.println(msg);
            System.out.println("-------------------------------\n");
        }
        ssn.close();
        con.close();
    }
   
    private Message createMessage(Session ssn) throws Exception
    {
        if (containsOp(MAP_ENTRY))
        {
            MapMessage msg = ssn.createMapMessage();
            for (String pair: getOp(MAP_ENTRY).split(","))
            {
                msg.setString(pair.substring(0, pair.indexOf('=')),
                              pair.substring(pair.indexOf('=') + 1));
            }
            setProperties(msg);
            return msg;
        }
        else
        {
            Message msg = 
                ssn.createTextMessage(containsOp(CONTENT) ? getOp(CONTENT) : "");
            setProperties(msg);
            return msg;
        }
    }

    private void setProperties(Message m) throws Exception
    {
        if(containsOp(MSG_PROPERTY))
        {
            for (String pair: getOp(MSG_PROPERTY).split(","))
            {
                m.setStringProperty(pair.substring(0, pair.indexOf('=')),
                              pair.substring(pair.indexOf('=') + 1));
            }
        }
    }
    
    public static void main(String[] args) throws Exception
    {
        String u = "Usage: spout [OPTIONS] 'ADDRESS'";
        String d = "Send messages to the specified address."; 
            
        new Spout(args,u,d);        
    }
}
