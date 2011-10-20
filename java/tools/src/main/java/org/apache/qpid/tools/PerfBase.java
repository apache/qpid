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
package org.apache.qpid.tools;

import java.net.InetAddress;
import java.text.DecimalFormat;
import java.util.UUID;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MapMessage;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.client.AMQAnyDestination;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQSession_0_10;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.messaging.Address;

public class PerfBase
{
    public final static String CODE = "CODE";
    public final static String ID = "ID";
    public final static String REPLY_ADDR = "REPLY_ADDR";
    public final static String MAX_LATENCY = "MAX_LATENCY";
    public final static String MIN_LATENCY = "MIN_LATENCY";
    public final static String AVG_LATENCY = "AVG_LATENCY";
    public final static String STD_DEV = "STD_DEV";
    public final static String CONS_RATE = "CONS_RATE";
    public final static String PROD_RATE = "PROD_RATE";
    public final static String MSG_COUNT = "MSG_COUNT";
    public final static String TIMESTAMP = "Timestamp";

    String CONTROLLER_ADDR = System.getProperty("CONT_ADDR","CONTROLLER;{create: always, node:{x-declare:{auto-delete:true}}}");

    TestParams params;
    Connection con;
    Session session;
    Session controllerSession;
    Destination dest;
    Destination myControlQueue;
    Destination controllerQueue;
    DecimalFormat df = new DecimalFormat("###.##");
    String id;
    String myControlQueueAddr;

    MessageProducer sendToController;
    MessageConsumer receiveFromController;
    String prefix = "";

    enum OPCode {
        REGISTER_CONSUMER, REGISTER_PRODUCER,
        PRODUCER_STARTWARMUP, CONSUMER_STARTWARMUP,
        CONSUMER_READY, PRODUCER_READY,
        PRODUCER_START,
        RECEIVED_END_MSG, CONSUMER_STOP,
        RECEIVED_PRODUCER_STATS, RECEIVED_CONSUMER_STATS,
        CONTINUE_TEST, STOP_TEST
    };

    enum MessageType {
        BYTES, TEXT, MAP, OBJECT;

        public static MessageType getType(String s) throws Exception
        {
            if ("text".equalsIgnoreCase(s))
            {
                return TEXT;
            }
            else if ("bytes".equalsIgnoreCase(s))
            {
                return BYTES;
            }
            /*else if ("map".equalsIgnoreCase(s))
            {
                return MAP;
            }
            else if ("object".equalsIgnoreCase(s))
            {
                return OBJECT;
            }*/
            else
            {
                throw new Exception("Unsupported message type");
            }
        }
    };

    MessageType msgType = MessageType.BYTES;

    public PerfBase(String prefix)
    {
        params = new TestParams();
        String host = "";
        try
        {
            host = InetAddress.getLocalHost().getHostName();
        }
        catch (Exception e)
        {
        }
        id = host + "-" + UUID.randomUUID().toString();
        this.prefix = prefix;
        this.myControlQueueAddr = id + ";{create: always}";
    }

    public void setUp() throws Exception
    {
        if (params.getHost().equals("") || params.getPort() == -1)
        {
            con = new AMQConnection(params.getUrl());
        }
        else
        {
            con = new AMQConnection(params.getHost(),params.getPort(),"guest","guest","test","test");
        }
        con.start();
        session = con.createSession(params.isTransacted(),
                                    params.isTransacted()? Session.SESSION_TRANSACTED:params.getAckMode());

        controllerSession = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

        dest = createDestination();
        controllerQueue = new AMQAnyDestination(CONTROLLER_ADDR);
        myControlQueue = session.createQueue(myControlQueueAddr);
        msgType = MessageType.getType(params.getMessageType());
        System.out.println("Using " + msgType + " messages");

        sendToController = controllerSession.createProducer(controllerQueue);
        receiveFromController = controllerSession.createConsumer(myControlQueue);
    }

    private Destination createDestination() throws Exception
    {
        if (params.isUseUniqueDests())
        {
            System.out.println("Prefix : " + prefix);
            Address addr = Address.parse(params.getAddress());
            AMQAnyDestination temp = new AMQAnyDestination(params.getAddress());
            int type = ((AMQSession_0_10)session).resolveAddressType(temp);

            if ( type == AMQDestination.TOPIC_TYPE)
            {
                addr = new Address(addr.getName(),addr.getSubject() + "." + prefix,addr.getOptions());
                System.out.println("Setting subject : " + addr);
            }
            else
            {
                addr = new Address(addr.getName() + "_" + prefix,addr.getSubject(),addr.getOptions());
                System.out.println("Setting name : " + addr);
            }

            return new AMQAnyDestination(addr);
        }
        else
        {
            return new AMQAnyDestination(params.getAddress());
        }
    }

    public synchronized void sendMessageToController(MapMessage m) throws Exception
    {
        m.setString(ID, id);
        m.setString(REPLY_ADDR,myControlQueueAddr);
        sendToController.send(m);
    }

    public void receiveFromController(OPCode expected) throws Exception
    {
        MapMessage m = (MapMessage)receiveFromController.receive();
        OPCode code = OPCode.values()[m.getInt(CODE)];
        System.out.println("Received Code : " + code);
        if (expected != code)
        {
            throw new Exception("Expected OPCode : " + expected + " but received : " + code);
        }

    }

    public boolean continueTest() throws Exception
    {
        MapMessage m = (MapMessage)receiveFromController.receive();
        OPCode code = OPCode.values()[m.getInt(CODE)];
        System.out.println("Received Code : " + code);
        return (code == OPCode.CONTINUE_TEST);
    }

    public void tearDown() throws Exception
    {
        session.close();
        controllerSession.close();
        con.close();
    }

    public void handleError(Exception e,String msg)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(msg);
        sb.append(" ");
        sb.append(e.getMessage());
        System.err.println(sb.toString());
        e.printStackTrace();
    }
}

