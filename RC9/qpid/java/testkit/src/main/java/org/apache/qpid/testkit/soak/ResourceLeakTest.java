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


import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.framing.AMQShortString;

/**
 * Test Description
 * ================
 * This test will open x number of connections where each
 * connection will create a session and a producer/consumer pair,
 * and then send configurable no of messages.
 * It will them sleep for configurable time interval and
 * tear down the connections/sessions/consumers.
 * It will then repeat the process again until the test is stopped.
 *
 * Purpose of the test
 * ===================
 * To find if the broker has leaks when cleaning resources.
 * To find if the client has leaks with resources.
 */
public class ResourceLeakTest extends BaseTest
{
    protected int connection_count = 10;
    protected long connection_idle_time = 5000;

    public ResourceLeakTest()
    {
        super();
        connection_count = Integer.getInteger("con_count",10);
        connection_idle_time = Long.getLong("con_idle_time", 5000);
    }

    public void test()
    {
        try
        {

            AMQConnection[] cons = new AMQConnection[connection_count];
            Session[] sessions = new Session[connection_count];
            MessageConsumer[] msgCons = new MessageConsumer[connection_count];
            MessageProducer [] msgProds = new MessageProducer[connection_count];
            Destination dest = new AMQQueue(new AMQShortString(exchange_name),
                                            new AMQShortString(routing_key),
                                            new AMQShortString(queue_name),
                                            true, //exclusive
                                            true  // auto delete
                                            );

            while (true)
            {
                for (int i = 0; i < connection_count; i++)
                {
                    AMQConnection con = new AMQConnection(url);
                    con.start();
                    cons[i] = con;
                    Session ssn = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
                    sessions[i] = ssn;
                    MessageConsumer msgCon = ssn.createConsumer(dest);
                    msgCons[i] = msgCon;
                    MessageProducer msgProd = ssn.createProducer(dest);
                    msgProds[i] = msgProd;

                    BytesMessage msg = ssn.createBytesMessage();
                    msg.writeBytes("Test Msg".getBytes());

                    for (int j = 0; j < msg_count;j++)
                    {
                        msgProd.send(msg);
                    }

                    int j = 0;
                    while (j < msg_count)
                    {
                      msgCon.receive();
                      j++;
                    }
                }
                System.out.println(df.format(System.currentTimeMillis()));
                Thread.sleep(connection_idle_time);

                try
                {
                    for (int i = 0; i < connection_count; i++)
                    {
                        msgCons[i].close();
                        msgProds[i].close();
                        sessions[i].close();
                        cons[i].close();
                    }
                }
                catch (Exception e)
                {
                    handleError(e,"Exception closing resources");
                }
            }
        }
        catch (Exception e)
        {
            handleError(e,"Exception in setting up the test");
        }

    }

    public static void main(String[] args)
    {
        ResourceLeakTest test = new ResourceLeakTest();
        test.test();
    }

}
