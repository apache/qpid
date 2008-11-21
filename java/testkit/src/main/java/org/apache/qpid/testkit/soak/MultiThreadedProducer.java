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


import java.util.Random;
import java.util.UUID;

import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.framing.AMQShortString;

/**
 * Test Description
 * ================
 *
 * This test creats x number of sessions, where each session
 * runs in it's own thread. Each session creates a producer
 * and it's own feedback queue.
 *
 * A producer will send n-1 messages, followed by the n-th
 * message which contains "End" in it's payload to signal
 * that this is the last message message in the sequence.
 * The end message has the feedback queue as it's replyTo.
 * It will then listen on the feedback queue waiting for the
 * confirmation and then sleeps for 1000 ms before proceeding
 * with the next n messages.
 *
 * This hand shaking mechanism ensures that all of the
 * messages sent are consumed by some consumer. This prevents
 * the producers from saturating the broker especially when
 * the consumers are slow.
 *
 * All producers send to a single destination
 * If using transactions it's best to use smaller message count
 * as the test only commits after sending all messages in a batch.
 *
 */

public class MultiThreadedProducer extends SimpleProducer
{
    protected final boolean transacted;

    public MultiThreadedProducer()
    {
        super();
        transacted = Boolean.getBoolean("transacted");
    }

    public void test()
    {
        try
        {
            final int msg_count_per_session =  msg_count/session_count;

            for (int i = 0; i < session_count; i++)
            {
                final Session session = con.createSession(transacted, Session.AUTO_ACKNOWLEDGE);
                Thread t = new Thread(new Runnable()
                {
                    private Random gen = new Random();

                    private Message getNextMessage()
                    {
                        if (msg_size == -1)
                        {
                            int index = gen.nextInt(1000);
                            return msgArray[index];
                        }
                        else
                        {
                            return msgArray[0];
                        }
                    }

                    public void run()
                    {
                        try
                        {
                            MessageProducer prod = session.createProducer(dest);
                            // this will ensure that the producer will not overun the consumer.
                            feedbackQueue = new AMQQueue(new AMQShortString("amq.direct"), new AMQShortString(UUID
                                    .randomUUID().toString()), new AMQShortString("control"));

                            MessageConsumer feedbackConsumer = session.createConsumer(feedbackQueue);

                            while (true)
                            {
                                for (int i = 0; i < msg_count_per_session; i++)
                                {
                                    Message msg = getNextMessage();
                                    msg.setJMSMessageID("ID:" + UUID.randomUUID());
                                    prod.send(msg);
                                }

                                TextMessage m = session.createTextMessage("End");
                                m.setJMSReplyTo(feedbackQueue);
                                prod.send(m);

                                if (transacted)
                                {
                                    session.commit();
                                }

                                System.out.println(df.format(System.currentTimeMillis()));
                                feedbackConsumer.receive();
                                if (transacted)
                                {
                                    session.commit();
                                }
                                Thread.sleep(1000);
                            }

                        }
                        catch (Exception e)
                        {
                            handleError(e,"Exception in producing message");
                        }

                    }

                });
                t.setName("session-" + i);
                t.start();

            }

        }
        catch (Exception e)
        {
            handleError(e,"Exception while setting up the test");
        }

    }

    public static void main(String[] args)
    {
        MultiThreadedProducer test = new MultiThreadedProducer();
        test.setUp();
        test.test();
    }

}
