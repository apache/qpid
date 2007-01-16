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
package org.apache.qpid.pingpong;

import org.apache.log4j.Logger;
import org.apache.log4j.Level;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.jms.Session;

import javax.jms.*;
import java.net.InetAddress;

public class TestPingClient
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
                    //ignore
                }

                long diff = timestamp - _lastTimestamp;
                _lastTimestamp = timestamp;

                long stringDiff = timestampString - _lastTimestampString;

                _lastTimestampString = timestampString;

                _logger.info("Ping: T:" + diff + "ms, TS:" + stringDiff);

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


            _logger.info("Connected with URL:" + con1.toURL());
            
            final org.apache.qpid.jms.Session session1 = (org.apache.qpid.jms.Session)
                    con1.createSession(false, Session.AUTO_ACKNOWLEDGE);


            String selector = null;

            if (args.length == 5)
            {
                selector = args[4];
                _logger.info("Message selector is <" + selector + ">...");
            }
            else
            {
                _logger.info("Not using message selector");
            }


            Queue q = new AMQQueue("ping");

            MessageConsumer consumer1 = session1.createConsumer(q,
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

