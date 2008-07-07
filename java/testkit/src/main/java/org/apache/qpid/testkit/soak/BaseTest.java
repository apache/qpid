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
package org.apache.qpid.testkit.soak;


import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.testkit.MessageFactory;

public class BaseTest
{
    protected String host = "127.0.0.1";
    protected int msg_size = 100;
    protected int msg_count = 10;
    protected int session_count = 1;
    protected boolean durable = false;
    protected String queue_name =  "message_queue";
    protected String exchange_name =  "amq.direct";
    protected String routing_key =  "routing_key";
    protected String contentType = "application/octet-stream";
    protected int port = 5672;
    protected String url;
    protected Message[] msgArray;

    protected AMQConnection con;
    protected Destination dest = null;
    protected DateFormat df = new SimpleDateFormat("yyyy.MM.dd 'at' HH:mm:ss");
    protected NumberFormat nf = new DecimalFormat("##.00");

    public BaseTest()
    {
       host = System.getProperty("host", "127.0.0.1");
       port = Integer.getInteger("port", 5672);
       msg_size = Integer.getInteger("msg_size", 100);
       msg_count = Integer.getInteger("msg_count", 10);
       session_count = Integer.getInteger("session_count", 1);
       durable = Boolean.getBoolean("durable");
       queue_name = System.getProperty("queue_name", "message_queue");
       exchange_name = System.getProperty("exchange_name", "amq.direct");
       routing_key = System.getProperty("routing_key", "routing_key");
       contentType = System.getProperty("content_type","application/octet-stream");



       url = "amqp://username:password@topicClientid/test?brokerlist='tcp://" + host + ":" + port + "'";
    }

    public void setUp()
    {
        try
        {
            con = new AMQConnection(url);
            con.start();


            if (exchange_name.equals("amq.topic"))
            {
                dest = new AMQTopic(new AMQShortString(exchange_name),
                                    new AMQShortString(routing_key),
                                    false,   //auto-delete
                                    null,   //queue name
                                    durable);
            }
            else
            {
                dest = new AMQQueue(new AMQShortString(exchange_name),
                                    new AMQShortString(routing_key),
                                    new AMQShortString(queue_name),
                                    false, //exclusive
                                    false, //auto-delete
                                    durable);
            }

            // Create the session to setup the messages
            Session session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

            if (msg_size == -1)
            {
                // This creates an array of 1000 messages from 500-1500 bytes
                // During the tests a message will be picked randomly
                msgArray = new Message[1000];
                for (int i = 0; i < 1000; i++)
                {
                    Message msg = (contentType.equals("text/plain")) ?
                                  MessageFactory.createTextMessage(session,500 + i) :
                                  MessageFactory.createBytesMessage(session, 500 + i);
                    msg.setJMSDeliveryMode((durable) ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
                    msgArray[i] = msg;
                }
            }
            else
            {
                Message msg = (contentType.equals("text/plain")) ?
                              MessageFactory.createTextMessage(session, msg_size):
                              MessageFactory.createBytesMessage(session, msg_size);
                msg.setJMSDeliveryMode((durable) ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
                msgArray = new Message[]
                { msg };
            }

            session.close();

        }
        catch (Exception e)
        {
            handleError(e,"Error while setting up the test");
        }
    }

    public void handleError(Exception e,String msg)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(msg);
        sb.append(" @ ");
        sb.append(df.format(new Date(System.currentTimeMillis())));
        sb.append(" ");
        sb.append(e.getMessage());
        System.err.println(sb.toString());
        e.printStackTrace();
    }
}
