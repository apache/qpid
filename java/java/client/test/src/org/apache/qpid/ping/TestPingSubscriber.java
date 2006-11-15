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
package org.apache.qpid.ping;

import org.apache.log4j.Logger;
import org.apache.log4j.Level;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.jms.Session;

import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Topic;
import javax.jms.JMSException;
import java.net.InetAddress;

public class TestPingSubscriber
{
    private static final Logger _logger = Logger.getLogger(TestPingClient.class);

    private static class TestPingMessageListener implements MessageListener
    {
        public TestPingMessageListener()
        {
        }

        long _lastTimestamp = 0L;
        long _lastTimestampString = 0L;

        public void onMessage(javax.jms.Message message)
        {
            Long time = System.nanoTime();

            if (_logger.isInfoEnabled())
            {
                long timestamp = 0L;
                long timestampString = 0L;

                try
                {
                    timestamp = message.getLongProperty("timestamp");
                    timestampString = Long.parseLong(message.getStringProperty("timestampString"));

                    if (timestampString != timestamp)
                    {
                        _logger.info("Timetamps differ!:\n" +
                                "timestamp:" + timestamp + "\n" +
                                "timestampString:" + timestampString);
                    }

                }
                catch (JMSException jmse)
                {
                }

                long diff = time - timestamp;

                long stringDiff = time - timestampString;

                _logger.info("Ping: TS:" + stringDiff/1000+"us");

                // _logger.info(_name + " got message '" + message + "\n");
            }
        }
    }

    public static void main(String[] args)
    {
        _logger.setLevel(Level.INFO);

        _logger.info("Starting...");

        if (args.length < 4)
        {
            System.out.println("Usage: brokerdetails username password virtual-path [selector] ");
            System.exit(1);
        }
        try
        {
            InetAddress address = InetAddress.getLocalHost();
            AMQConnection con1 = new AMQConnection(args[0], args[1], args[2],
                    address.getHostName(), args[3]);

            final org.apache.qpid.jms.Session session1 = (org.apache.qpid.jms.Session)
                    con1.createSession(false, Session.AUTO_ACKNOWLEDGE);


            String selector = null;

            if (args.length == 5)
            {
                selector = args[4];
            }

            _logger.info("Message selector is <" + selector + ">...");

            Topic t = new AMQTopic("ping");

            MessageConsumer consumer1 = session1.createConsumer(t,
                    1, false, false, selector);

            consumer1.setMessageListener(new TestPingMessageListener());
            con1.start();
        }
        catch (Throwable t)
        {
            System.err.println("Fatal error: " + t);
            t.printStackTrace();
        }

        System.out.println("Waiting...");
    }
}

