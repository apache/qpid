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
package org.apache.qpid.requestreply;

import java.net.InetAddress;

import javax.jms.*;

import org.apache.log4j.Logger;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.jms.Session;
import org.apache.qpid.ping.AbstractPingClient;

/**
 * PingPongClient is a message listener the bounces back messages to their reply to destination. This is used to return
 * ping messages generated by {@link org.apache.qpid.requestreply.PingPongProducer} but could be used for other purposes too.
 *
 * <p/>The message id from the received message is extracted, and placed into the reply as the correlation id. Messages
 * are bounced back to the reply-to destination. The original sender of the message has the option to use either a unique
 * temporary queue or the correlation id to correlate the original message to the reply.
 *
 * <p/>There is a verbose mode flag which causes information about each ping to be output to the console
 * (info level logging, so usually console). This can be helpfull to check the bounce backs are happening but should
 * be disabled for real timing tests as writing to the console will slow things down.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Bounce back messages to their reply to destination.
 * <tr><td> Provide command line invocation to start the bounce back on a configurable broker url.
 * </table>
 *
 * @todo Rename this to BounceBackClient or something similar.
 */
public class PingPongClient extends AbstractPingClient implements MessageListener
{
    private static final Logger _logger = Logger.getLogger(PingPongClient.class);

    /** Used to indicate that the reply generator should log timing info to the console (logger info level). */
    private boolean _verbose = false;

    /**
     * Creates a PingPongClient on the specified session.
     *
     * @param session       The JMS Session for the ping pon client to run on.
     * @param consumer      The message consumer to receive the messages with.
     * @param verbose       If set to <tt>true</tt> will output timing information on every message.
     */
    public PingPongClient(Session session, MessageConsumer consumer, boolean verbose) throws JMSException
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
            new PingPongClient(session, consumer, true);

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
     * listener on a message consumer. It sends a reply (pong) to all messages it receieves on the reply to
     * destination of the message.
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

            // Correlate the reply to the original.
            message.setJMSCorrelationID(message.getJMSMessageID());

            // Send the receieved message as the pong reply.
            MessageProducer producer = _session.createProducer(message.getJMSReplyTo());
            producer.send(message);

            // Commit the transaction if running in transactional mode.
            commitTx();
        }
        catch (JMSException e)
        {
            _logger.debug("There was a JMSException: " + e.getMessage(), e);
        }
    }
}
