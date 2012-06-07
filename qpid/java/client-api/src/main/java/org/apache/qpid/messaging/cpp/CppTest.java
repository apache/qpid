/* Licensed to the Apache Software Foundation (ASF) under one
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
 */
package org.apache.qpid.messaging.cpp;

import java.util.Map;

import org.apache.qpid.messaging.Connection;
import org.apache.qpid.messaging.ConnectionFactory;
import org.apache.qpid.messaging.Message;
import org.apache.qpid.messaging.Receiver;
import org.apache.qpid.messaging.Session;
import org.apache.qpid.messaging.Sender;

public class CppTest
{
    public static void main(String[] args)
    {
        Connection con = ConnectionFactory.get().createConnection("localhost:5672");
        con.open();
        Session ssn = con.createSession("hello");
        System.out.println("Got a session object "  + ssn);

        Sender sender = ssn.createSender("amq.topic/test");
        System.out.println("Got a Sender object "  + sender);

        Receiver receiver = ssn.createReceiver("amq.topic/test");
        System.out.println("Got a Receiver object "  + receiver);

        Message msg = new TextMessage("Hello World");
        msg.setProperty("color", "blue");
        msg.setProperty("price", 5);
        msg.setProperty("boolean", true);
        sender.send(msg, false);
        TextMessage m = (TextMessage) receiver.fetch(0);

        System.out.println("Received message "  + m + " with content type : " + m.getContentType() + " and content : " + m.getContent());

        Map<String,Object> props = m.getProperties();
        if (props != null)
        {
            //System.out.println("Color : " + m.getProperties().get("color"));
            System.out.println("Price : " + props.get("price"));
        }

        ssn.close();
        con.close();
    }

}
