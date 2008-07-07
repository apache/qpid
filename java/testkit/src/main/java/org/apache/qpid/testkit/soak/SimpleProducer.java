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

import javax.jms.Destination;
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
 * This test will send n-1 messages, followed by the n-th
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
 * It creates a producer per session.
 * If session_count is > 1 it will round robin the messages
 * btw the producers.
 *
 * All producers send to a single destination
 *
 */

public class SimpleProducer extends BaseTest
{
    protected Destination feedbackQueue;
    Random gen = new Random();

    public SimpleProducer()
    {
        super();
    }

    protected Message getNextMessage()
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

    public void test()
    {
        try
        {
            Session[] sessions = new Session[session_count];
            MessageProducer[] prods = new MessageProducer[session_count];

            for (int i = 0; i < session_count; i++)
            {
                sessions[i] = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
                prods[i] = sessions[i].createProducer(dest);
            }

            // this will ensure that the producer will not overun the consumer.
            feedbackQueue = new AMQQueue(new AMQShortString("amq.direct"),
                                         new AMQShortString(UUID.randomUUID().toString()),
                                         new AMQShortString("control"));

            MessageConsumer feedbackConsumer = sessions[0].createConsumer(feedbackQueue);

            int prod_pointer = 0;
            boolean multi_session = session_count > 1 ? true : false;

            while (true)
            {
                for (int i = 0; i < msg_count - 1; i++)
                {
                    Message msg = getNextMessage();
                    msg.setJMSTimestamp(System.currentTimeMillis());
                    prods[prod_pointer].send(msg);
                    if (multi_session)
                    {
                        prod_pointer++;
                        if (prod_pointer == session_count)
                        {
                            prod_pointer = 0;
                        }
                    }
                }

                TextMessage m = sessions[0].createTextMessage("End");
                m.setJMSReplyTo(feedbackQueue);
                prods[prod_pointer].send(m);
                System.out.println(df.format(System.currentTimeMillis()));
                feedbackConsumer.receive();
                Thread.sleep(1000);
            }
        }
        catch (Exception e)
        {
            handleError(e,"Exception while setting up the producer");
        }

    }

    public static void main(String[] args)
    {
        SimpleProducer test = new SimpleProducer();
        test.setUp();
        test.test();
    }

}
