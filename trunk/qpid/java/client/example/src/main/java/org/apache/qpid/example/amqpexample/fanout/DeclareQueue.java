package org.apache.qpid.example.amqpexample.fanout;
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


import org.apache.qpid.nclient.Client;
import org.apache.qpid.nclient.Connection;
import org.apache.qpid.nclient.Session;

/**
 *  This creates a queue a queue and binds it to the
 *  amq.direct exchange
 *
 */
public class DeclareQueue
{

    public static void main(String[] args)
    {
        // Create connection
        Connection con = Client.createConnection();
        try
        {
            con.connect("localhost", 5672, "test", "guest", "guest");
        }
        catch(Exception e)
        {
            System.out.print("Error connecting to broker");
            e.printStackTrace();
        }

        // Create session
        Session session = con.createSession(0);

        // declare and bind queue
        session.queueDeclare("message_queue", null, null);
        session.exchangeBind("message_queue", "amq.fanout",null, null);

        // confirm completion
        session.sync();

        //cleanup
        session.sessionDetach(session.getName());
        try
        {
            con.close();
        }
        catch(Exception e)
        {
            System.out.print("Error closing broker connection");
            e.printStackTrace();
        }
    }
}
