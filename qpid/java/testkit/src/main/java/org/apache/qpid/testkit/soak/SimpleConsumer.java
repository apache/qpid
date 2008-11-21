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

import org.apache.qpid.thread.Threading;

/**
 * Test Description
 * ================
 * This test will create x number of sessions.
 * Each session will have it's own consumer.
 * Once a consumer receives the "End" message it
 * will send a message to the destination indicated
 * by the replyTo field in the End message.
 * This will signal the producer that all the previous
 * messages have been consumed. The producer will
 * then start sending messages again.
 *
 * This prevents the producer from overruning the
 * consumer.
 *  *
 * All consumers share a single destination
 *
 */

public class SimpleConsumer extends BaseTest
{
    public SimpleConsumer()
    {
        super();
        //needed only to calculate throughput.
        // If msg_count is different set it via -Dmsg_count
        msg_count = 10;
    }

    public void test()
    {
        try
        {
            final Session[] sessions = new Session[session_count];
            MessageConsumer[] cons = new MessageConsumer[session_count];

            for (int i = 0; i < session_count; i++)
            {
                sessions[i] = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
                cons[i] = sessions[i].createConsumer(dest);
                cons[i].setMessageListener(new MessageListener()
                {

                    private boolean startIteration = true;
                    private long startTime = 0;
                    private long iterations = 0;

                    public void onMessage(Message m)
                    {
                        try
                        {
                            long now = System.currentTimeMillis();
                            if (startIteration)
                            {
                                startTime = m.getJMSTimestamp();
                                startIteration = false;
                            }

                            if (m instanceof TextMessage && ((TextMessage) m).getText().equals("End"))
                            {

                                long totalIterationTime = now - startTime;
                                startIteration = true;
                                double throughput = ((double)msg_count/(double)totalIterationTime) * 1000;
                                long latencySample = now - m.getJMSTimestamp();
                                iterations++;

                                StringBuilder sb = new StringBuilder();
                                sb.append(iterations).append(",").
                                append(nf.format(throughput)).append(",").append(latencySample);

                                System.out.println(sb.toString());

                                MessageProducer temp = sessions[0].createProducer(m.getJMSReplyTo());
                                Message controlMsg = sessions[0].createTextMessage();
                                temp.send(controlMsg);
                                temp.close();
                            }
                        }
                        catch (JMSException e)
                        {
                            handleError(e,"Exception when receiving the message");
                        }
                    }
                });
            }

        }
        catch (Exception e)
        {
            handleError(e,"Exception when setting up the consumers");
        }

    }

    public static void main(String[] args)
    {
        final SimpleConsumer test = new SimpleConsumer();
        Runnable r = new Runnable(){    
            public void run()
            {
                test.setUp();
                test.test();
            }
        };    
        
        Thread t;
        try
        {
            t = Threading.getThreadFactory().createThread(r);                      
        }
        catch(Exception e)
        {
            throw new Error("Error creating consumer thread",e);
        }
    }

}
