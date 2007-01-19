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
 *
 * @todo Add a better command line interpreter to the main method. The command line is not very nice...
 */
class TestPingClient extends AbstractPingClient implements MessageListener
{
    private static final Logger _logger = Logger.getLogger(TestPingClient.class);

    /** Used to indicate that the reply generator should log timing info to the console (logger info level). */
    private boolean _verbose = false;

    /** The producer session. */
    private Session _consumerSession;

    /**
     * Creates a TestPingClient on the specified session.
     *
     * @param brokerDetails
     * @param username
     * @param password
     * @param queueName
     * @param virtualpath
     * @param transacted
     * @param selector
     * @param verbose
     *
     * @throws Exception All underlying exceptions allowed to fall through. This is only test code...
     */
    public TestPingClient(String brokerDetails, String username, String password, String queueName, String virtualpath,
                          boolean transacted, String selector, boolean verbose) throws Exception
    {
        // Create a connection to the broker.
        InetAddress address = InetAddress.getLocalHost();
        String clientID = address.getHostName() + System.currentTimeMillis();

        setConnection(new AMQConnection(brokerDetails, username, password, clientID, virtualpath));

        // Create a transactional or non-transactional session depending on the command line parameter.
        _consumerSession = (Session) getConnection().createSession(transacted, Session.AUTO_ACKNOWLEDGE);

        // Connect a consumer to the ping queue and register this to be called back by it.
        Queue q = new AMQQueue(queueName);
        MessageConsumer consumer = _consumerSession.createConsumer(q, 1, false, false, selector);
        consumer.setMessageListener(this);

        // Hang on to the verbose flag setting.
        _verbose = verbose;
    }

    /**
     * Starts a stand alone ping-pong client running in verbose mode.
     *
     * @param args
     */
    public static void main(String[] args) throws Exception
    {
        _logger.info("Starting...");

        // Display help on the command line.
        if (args.length < 4)
        {
            System.out.println(
                "Usage: brokerdetails username password virtual-path [queueName] [verbose] [transacted] [selector]");
            System.exit(1);
        }

        // Extract all command line parameters.
        String brokerDetails = args[0];
        String username = args[1];
        String password = args[2];
        String virtualpath = args[3];
        String queueName = (args.length >= 5) ? args[4] : "ping";
        boolean verbose = (args.length >= 6) ? Boolean.parseBoolean(args[5]) : true;
        boolean transacted = (args.length >= 7) ? Boolean.parseBoolean(args[6]) : false;
        String selector = (args.length == 8) ? args[7] : null;

        // Create the test ping client and set it running.
        TestPingClient pingClient =
            new TestPingClient(brokerDetails, username, password, queueName, virtualpath, transacted, selector, verbose);
        pingClient.getConnection().start();

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
                    System.out.println("Ping time: " + diff);
                }
            }

            // Commit the transaction if running in transactional mode.
            commitTx(_consumerSession);
        }
        catch (JMSException e)
        {
            _logger.error("There was a JMSException: " + e.getMessage(), e);
        }
    }
}
