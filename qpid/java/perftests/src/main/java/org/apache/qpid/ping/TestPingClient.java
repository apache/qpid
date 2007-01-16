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

import java.net.InetAddress;

import javax.jms.*;

import org.apache.log4j.Logger;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.jms.Session;

/**
 * PingClient is a message listener that received time stamped ping messages. It can work out how long a ping took,
 * provided that its clokc is synchronized to that of the ping producer, or by running it on the same machine (or jvm)
 * as the ping producer.
 *
 * <p/>There is a verbose mode flag which causes information about each ping to be output to the console
 * (info level logging, so usually console). This can be helpfull to check the bounce backs are happening but should
 * be disabled for real timing tests as writing to the console will slow things down.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Provide command line invocation to start the ping consumer on a configurable broker url.
 * </table>
 */
class TestPingClient extends AbstractPingClient implements MessageListener
{
    private static final Logger _logger = Logger.getLogger(TestPingClient.class);

    /** Used to indicate that the reply generator should log timing info to the console (logger info level). */
    private boolean _verbose = false;

    /**
     * Creates a PingPongClient on the specified session.
     *
     * @param session       The JMS Session for the ping pon client to run on.
     * @param consumer      The message consumer to receive the messages with.
     * @param verbose       If set to <tt>true</tt> will output timing information on every message.
     */
    public TestPingClient(Session session, MessageConsumer consumer, boolean verbose) throws JMSException
    {
        // Hang on to the session for the replies.
        super(session);

        // Set this up to listen for messages on the queue.
        consumer.setMessageListener(this);
    }

    /**
     * Starts a stand alone ping-pong client running in verbose mode.
     *
     * @param args
     */
    public static void main(String[] args)
    {
        _logger.info("Starting...");

        // Display help on the command line.
        if (args.length < 4)
        {
            System.out.println("Usage: brokerdetails username password virtual-path [transacted] [selector]");
            System.exit(1);
        }

        // Extract all comman line parameters.
        String brokerDetails = args[0];
        String username = args[1];
        String password = args[2];
        String virtualpath = args[3];
        boolean transacted = (args.length >= 5) ? Boolean.parseBoolean(args[4]) : false;
        String selector = (args.length == 6) ? args[5] : null;

        try
        {
            InetAddress address = InetAddress.getLocalHost();

            AMQConnection con1 = new AMQConnection(brokerDetails, username, password, address.getHostName(), virtualpath);

            _logger.info("Connected with URL:" + con1.toURL());

            // Create a transactional or non-transactional session depending on the command line parameter.
            Session session = null;

            if (transacted)
            {
                session = (org.apache.qpid.jms.Session) con1.createSession(false, Session.SESSION_TRANSACTED);
            }
            else if (!transacted)
            {
                session = (org.apache.qpid.jms.Session) con1.createSession(false, Session.AUTO_ACKNOWLEDGE);
            }

            Queue q = new AMQQueue("ping");

            MessageConsumer consumer = session.createConsumer(q, 1, false, false, selector);
            new TestPingClient(session, consumer, true);

            con1.start();
        }
        catch (Throwable t)
        {
            System.err.println("Fatal error: " + t);
            t.printStackTrace();
        }

        System.out.println("Waiting...");
    }

    /**
     * This is a callback method that is notified of all messages for which this has been registered as a message
     * listener on a message consumer.
     *
     * @param message The message that triggered this callback.
     */
    public void onMessage(javax.jms.Message message)
    {
        try
        {
            // Spew out some timing information if verbose mode is on.
            if (_verbose)
            {
                Long timestamp = message.getLongProperty("timestamp");

                if (timestamp != null)
                {
                    long diff = System.currentTimeMillis() - timestamp;
                    _logger.info("Ping time: " + diff);
                }
            }

            // Commit the transaction if running in transactional mode.
            commitTx();
        }
        catch (JMSException e)
        {
            _logger.debug("There was a JMSException: " + e.getMessage(), e);
        }
    }
}
