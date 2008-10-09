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
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageAcceptMode;
import org.apache.qpid.transport.MessageAcquireMode;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.MessageTransfer;
import org.apache.qpid.transport.Connection;
import org.apache.qpid.transport.Session;
import org.apache.qpid.transport.SessionException;
import org.apache.qpid.transport.SessionListener;

import java.nio.ByteBuffer;
import java.util.UUID;

public class DemoClient
{
    public static class DemoListener implements SessionListener
    {
        public void opened(Session ssn) {}

        public void exception(Session ssn, SessionException exc)
        {
            System.out.println(exc);
        }

        public void message(Session ssn, MessageTransfer m)
        {
            System.out.println("\n================== Received Msg ==================");
            System.out.println("Message Id : " + m.getHeader().get(MessageProperties.class).getMessageId());
            System.out.println(m.toString());
            System.out.println("================== End Msg ==================\n");
        }

        public void closed(Session ssn) {}
    }

    public static final void main(String[] args)
    {
        Connection conn = new Connection();
        conn.connect("0.0.0.0", 5672, "test", "guest", "guest");

        Session ssn = conn.createSession(50000);
        ssn.setSessionListener(new DemoListener());
        ssn.queueDeclare("queue1", null, null);
        ssn.exchangeBind("queue1", "amq.direct", "queue1",null);
        ssn.sync();

        ssn.messageSubscribe("queue1", "myDest", MessageAcceptMode.NONE, MessageAcquireMode.PRE_ACQUIRED,
                             null, 0, null);

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
        ssn.messageSubscribe("topic1", "myDest2", MessageAcceptMode.NONE, MessageAcquireMode.PRE_ACQUIRED,
                             null, 0, null);
        ssn.messageSubscribe("topic2", "myDest3", MessageAcceptMode.NONE, MessageAcquireMode.PRE_ACQUIRED,
                             null, 0, null);
        ssn.messageSubscribe("topic3", "myDest4", MessageAcceptMode.NONE, MessageAcquireMode.PRE_ACQUIRED,
                             null, 0, null);
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
