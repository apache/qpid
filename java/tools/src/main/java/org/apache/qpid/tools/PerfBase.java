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
    String id = UUID.randomUUID().toString();
    String myControlQueueAddr = id + ";{create: always}";

    MessageProducer sendToController;
    MessageConsumer receiveFromController;

    enum OPCode {
        REGISTER_CONSUMER, REGISTER_PRODUCER,
        PRODUCER_STARTWARMUP, CONSUMER_STARTWARMUP,
        CONSUMER_READY, PRODUCER_READY,
        PRODUCER_START,
        RECEIVED_END_MSG, CONSUMER_STOP,
        RECEIVED_PRODUCER_STATS, RECEIVED_CONSUMER_STATS
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

    public PerfBase()
    {
        params = new TestParams();
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

        dest = new AMQAnyDestination(params.getAddress());
        controllerQueue = new AMQAnyDestination(CONTROLLER_ADDR);
        myControlQueue = session.createQueue(myControlQueueAddr);
        msgType = MessageType.getType(params.getMessageType());
        System.out.println("Using " + msgType + " messages");

        sendToController = controllerSession.createProducer(controllerQueue);
        receiveFromController = controllerSession.createConsumer(myControlQueue);
    }

    public synchronized void sendMessageToController(MapMessage m) throws Exception
    {
        m.setString(ID, id);
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

