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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.messaging.Connection;
import org.apache.qpid.messaging.ConnectionFactory;
import org.apache.qpid.messaging.ListMessage;
import org.apache.qpid.messaging.MapMessage;
import org.apache.qpid.messaging.Message;
import org.apache.qpid.messaging.Receiver;
import org.apache.qpid.messaging.Session;
import org.apache.qpid.messaging.Sender;
import org.apache.qpid.messaging.StringMessage;

public class CppTest
{
    public static void main(String[] args) throws Exception
    {
        Connection con = ConnectionFactory.get().createConnection("localhost:5672");
        con.open();
        Session ssn = con.createSession(null);
        Sender sender = ssn.createSender("amq.topic/test");
        Receiver receiver = ssn.createReceiver("amq.topic/test");

        System.out.println("======= Text Message with Message Properties ========");

        Message msg = con.getMessageFactory().createMessage("Hello World");
        msg.setProperty("color", "blue");
        msg.setProperty("price", 5);
        msg.setProperty("boolean", true);
        sender.send(msg, false);

        StringMessage stringMsg = (StringMessage) receiver.fetch(0);
        System.out.println("Received message "  + stringMsg + " with content type : " + stringMsg.getContentType() + " and content : " + stringMsg.getString());

        Map<String,Object> props = stringMsg.getProperties();
        System.out.println("Props size : " + props.size());
        System.out.println("Props empty : " + props.isEmpty());
        System.out.println("Contains key 'color' : " + props.containsKey("color"));
        for (String key : props.keySet())
        {
            System.out.println("Key=" + key + ", value=" + props.get(key));
        }
        System.out.println("Unspecified property : " + props.get("Unspecified-Prop"));

        System.out.println("================= Map Message =================");
        Map<String,Object> myMap = new HashMap<String,Object>();
        myMap.put("k1", 1);
        myMap.put("k2", 2);

        msg = con.getMessageFactory().createMessage(myMap);
        sender.send(msg, false);
        MapMessage mapMsg = (MapMessage) receiver.fetch(0);
        System.out.println("Received message "  + mapMsg + " with content type : " + mapMsg.getContentType() + " and content : " + mapMsg.getMap());

        System.out.println("================= List Message =================");
        List<Object> myList = new ArrayList<Object>();
        myList.add("Red");
        myList.add("Green");
        myList.add("Blue");

        msg = con.getMessageFactory().createMessage(myList);
        sender.send(msg, false);
        ListMessage listMsg = (ListMessage) receiver.fetch(0);
        System.out.println("Received message "  + listMsg + " with content type : " + listMsg.getContentType() + " and content : " + listMsg.getList());

        sender.close();
        receiver.close();
        ssn.close();
        con.close();
    }

}
