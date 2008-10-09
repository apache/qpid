package org.apache.qpid.nclient.interop;
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


import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.ErrorCode;
import org.apache.qpid.QpidException;
import org.apache.qpid.api.Message;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageAcceptMode;
import org.apache.qpid.transport.MessageAcquireMode;
import org.apache.qpid.transport.MessageCreditUnit;
import org.apache.qpid.transport.MessageFlowMode;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.MessageTransfer;
import org.apache.qpid.transport.Connection;
import org.apache.qpid.transport.Session;
import org.apache.qpid.transport.SessionException;
import org.apache.qpid.transport.SessionListener;

public class BasicInteropTest implements SessionListener
{

    private Session session;
    private Connection conn;
    private String host;

    public BasicInteropTest(String host)
    {
        this.host = host;
    }

    public void close() throws QpidException
    {
        conn.close();
    }

    public void testCreateConnection(){
        System.out.println("------- Creating connection--------");
        conn = new Connection();
        try{
            conn.connect(host, 5672, "test", "guest", "guest");
        }catch(Exception e){
            System.out.println("------- Error Creating connection--------");
            e.printStackTrace();
            System.exit(1);
        }
        System.out.println("------- Connection created Suscessfully --------");
    }

    public void testCreateSession(){
        System.out.println("------- Creating session --------");
        session = conn.createSession(0);
        System.out.println("------- Session created sucessfully --------");
    }

    public void testExchange(){
        System.out.println("------- Creating an exchange --------");
        session.exchangeDeclare("test", "direct", "", null);
        session.sync();
        System.out.println("------- Exchange created --------");
    }

    public void testQueue(){
        System.out.println("------- Creating a queue --------");
        session.queueDeclare("testQueue", "", null);
        session.sync();
        System.out.println("------- Queue created --------");

        System.out.println("------- Binding a queue --------");
        session.exchangeBind("testQueue", "test", "testKey", null);
        session.sync();
        System.out.println("------- Queue bound --------");
    }

    public void testSendMessage(){
        System.out.println("------- Sending a message --------");
        Map<String,Object> props = new HashMap<String,Object>();
        props.put("name", "rajith");
        props.put("age", 10);
        props.put("spf", 8.5);
        session.messageTransfer("test", MessageAcceptMode.NONE, MessageAcquireMode.PRE_ACQUIRED,
                                new Header(new DeliveryProperties().setRoutingKey("testKey"),
                                           new MessageProperties().setApplicationHeaders(props)),
                                ByteBuffer.wrap("TestMessage".getBytes()));

        session.sync();
        System.out.println("------- Message sent --------");
    }

    public void testSubscribe()
    {
        System.out.println("------- Sending a subscribe --------");
        session.setSessionListener(this);
        session.messageSubscribe("testQueue", "myDest",
                                 MessageAcceptMode.EXPLICIT,
                                 MessageAcquireMode.PRE_ACQUIRED,
                                 null, 0, null);

        System.out.println("------- Setting Credit mode --------");
        session.messageSetFlowMode("myDest", MessageFlowMode.WINDOW);
        System.out.println("------- Setting Credit --------");
        session.messageFlow("myDest", MessageCreditUnit.MESSAGE, 1);
        session.messageFlow("myDest", MessageCreditUnit.BYTE, -1);
    }

    public void opened(Session ssn) {}

    public void message(Session ssn, MessageTransfer xfr)
    {
        System.out.println("--------Message Received--------");
        System.out.println(xfr.toString());
        System.out.println("--------/Message Received--------");
        ssn.processed(xfr);
        ssn.flushProcessed();
    }

    public void testMessageFlush()
    {
        session.messageFlush("myDest");
        session.sync();
    }

    public void exception(Session ssn, SessionException exc)
    {
        System.out.println("------- Broker Notified an error --------");
        System.out.println("------- " + exc + " --------");
        System.out.println("------- /Broker Notified an error --------");
    }

    public void closed(Session ssn) {}

    public static void main(String[] args) throws QpidException
    {
        String host = "0.0.0.0";
        if (args.length>0)
        {
            host = args[0];
        }

        BasicInteropTest t = new BasicInteropTest(host);
        t.testCreateConnection();
        t.testCreateSession();
        t.testExchange();
        t.testQueue();
        t.testSubscribe();
        t.testSendMessage();
        t.testMessageFlush();
        t.close();
    }
}
