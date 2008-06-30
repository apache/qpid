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


import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

/**
 * Test Description
 * ================
 * The difference between this test and the
 * LongDurationConsumer is that each Session runs
 * in it's own Thread and the ability to receive
 * messages transactionally.
 *
 * All consumers will still share the same destination.
 *
 */
public class MultiThreadedConsumer extends BaseTest
{
    protected final boolean transacted;

    public MultiThreadedConsumer()
    {
        super();
        transacted = Boolean.getBoolean("transacted");
    }

    /**
     * Creates a Session and a consumer that runs in its
     * own thread.
     * It can also consume transactionally.
     *
     */
    public void test()
    {
        try
        {
            for (int i = 0; i < session_count; i++)
            {

                final Session session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Thread t = new Thread(new Runnable()
                {
                    public void run()
                    {
                        try
                        {
                            MessageConsumer consumer = session.createConsumer(dest);

                            consumer.setMessageListener(new MessageListener()
                            {

                                public void onMessage(Message m)
                                {
                                    try
                                    {
                                        String payload = ((TextMessage) m).getText();
                                        if (payload.equals("End"))
                                        {
                                            System.out.println(m.getJMSMessageID() + "," + System.currentTimeMillis());
                                            MessageProducer temp = session.createProducer(m.getJMSReplyTo());
                                            Message controlMsg = session.createTextMessage();
                                            temp.send(controlMsg);
                                            if (transacted)
                                            {
                                                session.commit();
                                            }
                                            temp.close();
                                        }
                                    }
                                    catch (JMSException e)
                                    {
                                        handleError(e,"Exception receiving messages");
                                    }
                                }
                            });
                        }
                        catch (Exception e)
                        {
                            handleError(e,"Exception creating a consumer");
                        }

                    }

                });
                t.setName("session-" + i);
                t.start();
            } // for loop
        }
        catch (Exception e)
        {
            handleError(e,"Exception while setting up the test");
        }

    }

    public static void main(String[] args)
    {
        MultiThreadedConsumer test = new MultiThreadedConsumer();
        test.setUp();
        test.test();
    }

}
