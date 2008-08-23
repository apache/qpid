package org.apache.qpid.nclient.impl;
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


import org.apache.qpid.ErrorCode;
import org.apache.qpid.api.Message;
import org.apache.qpid.nclient.Client;
import org.apache.qpid.nclient.Connection;
import org.apache.qpid.nclient.ClosedListener;
import org.apache.qpid.nclient.Session;
import org.apache.qpid.nclient.util.MessageListener;
import org.apache.qpid.nclient.util.MessagePartListenerAdapter;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageAcceptMode;
import org.apache.qpid.transport.MessageAcquireMode;
import org.apache.qpid.transport.MessageProperties;

import java.nio.ByteBuffer;
import java.util.UUID;

public class DemoClient
{
    public static MessagePartListenerAdapter createAdapter()
    {
        return new MessagePartListenerAdapter(new MessageListener()
        {
            public void onMessage(Message m)
            {
                System.out.println("\n================== Received Msg ==================");
                System.out.println("Message Id : " + m.getMessageProperties().getMessageId());
                System.out.println(m.toString());
                System.out.println("================== End Msg ==================\n");
            }

        });
    }

    public static final void main(String[] args)
    {
        Connection conn = Client.createConnection();
        try{
            conn.connect("0.0.0.0", 5672, "test", "guest", "guest");
        }catch(Exception e){
            e.printStackTrace();
        }

        Session ssn = conn.createSession(50000);
        ssn.setClosedListener(new ClosedListener()
                {
                     public void onClosed(ErrorCode errorCode, String reason, Throwable t)
                     {
                         System.out.println("ErrorCode : " + errorCode + " reason : " + reason);
                     }
                });
        ssn.queueDeclare("queue1", null, null);
        ssn.exchangeBind("queue1", "amq.direct", "queue1",null);
        ssn.sync();

        ssn.messageSubscribe("queue1", "myDest", (short)0, (short)0,createAdapter(), null);

        // queue
        ssn.messageTransfer("amq.direct", MessageAcceptMode.NONE, MessageAcquireMode.PRE_ACQUIRED,
                            new Header(new DeliveryProperties().setRoutingKey("queue1"),
                                       new MessageProperties().setMessageId(UUID.randomUUID())),
                            ByteBuffer.wrap("this is the data".getBytes()));

        //reject
        ssn.messageTransfer("amq.direct", MessageAcceptMode.NONE, MessageAcquireMode.PRE_ACQUIRED,
                            new Header(new DeliveryProperties().setRoutingKey("stocks")),
                            ByteBuffer.wrap("this should be rejected".getBytes()));
        ssn.sync();

        // topic subs
        ssn.messageSubscribe("topic1", "myDest2", (short)0, (short)0,createAdapter(), null);
        ssn.messageSubscribe("topic2", "myDest3", (short)0, (short)0,createAdapter(), null);
        ssn.messageSubscribe("topic3", "myDest4", (short)0, (short)0,createAdapter(), null);
        ssn.sync();

        ssn.queueDeclare("topic1", null, null);
        ssn.exchangeBind("topic1", "amq.topic", "stock.*",null);
        ssn.queueDeclare("topic2", null, null);
        ssn.exchangeBind("topic2", "amq.topic", "stock.us.*",null);
        ssn.queueDeclare("topic3", null, null);
        ssn.exchangeBind("topic3", "amq.topic", "stock.us.rh",null);
        ssn.sync();

        // topic
        ssn.messageTransfer("amq.topic", MessageAcceptMode.NONE, MessageAcquireMode.PRE_ACQUIRED,
                            new Header(new DeliveryProperties().setRoutingKey("stock.us.ibm"),
                                       new MessageProperties().setMessageId(UUID.randomUUID())),
                            ByteBuffer.wrap("Topic message".getBytes()));
    }

}
