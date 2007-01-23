/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.ping;

import org.apache.qpid.requestreply.PingPongProducer;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.jms.Session;
import org.apache.qpid.jms.MessageProducer;
import org.apache.log4j.Logger;

import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.ObjectMessage;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Queue;
import java.net.InetAddress;
import java.util.List;
import java.util.ArrayList;

/**
 * This class is used to test sending and receiving messages to (pingQueue) and from a queue (replyQueue).
 * The producer and consumer created by this test send and receive messages to and from the same Queue. ie.
 * pingQueue and replyQueue are same.
 * This class extends @see org.apache.qpid.requestreply.PingPongProducer which different ping and reply Queues
 */
public class TestPingItself extends PingPongProducer
{
    private static final Logger _logger = Logger.getLogger(TestPingItself.class);

    public TestPingItself(String brokerDetails, String username, String password, String virtualpath, String queueName,
                          String selector, boolean transacted, boolean persistent, int messageSize, boolean verbose,
                          boolean afterCommit, boolean beforeCommit, boolean afterSend, boolean beforeSend,
                          int batchSize)
            throws Exception
    {
        super(brokerDetails, username, password, virtualpath, queueName, selector, transacted, persistent, messageSize,
              verbose, afterCommit, beforeCommit, afterSend, beforeSend, batchSize, 0);
    }

    public TestPingItself(String brokerDetails, String username, String password, String virtualpath,
                          String selector, boolean transacted, boolean persistent, int messageSize, boolean verbose,
                          boolean afterCommit, boolean beforeCommit, boolean afterSend, boolean beforeSend,
                          int batchSize, int queueCount)
            throws Exception
    {
        super(brokerDetails, username, password, virtualpath, null, null, transacted, persistent, messageSize,
              verbose, afterCommit, beforeCommit, afterSend, beforeSend, batchSize, queueCount);

        createQueues(queueCount);

        _persistent = persistent;
        _messageSize = messageSize;
        _verbose = verbose;

        createConsumers(selector);
        createProducer();
    }

    @Override
    /**
     * Sets the replyQueue to be the same as ping queue. 
     */
    public void createConsumer(String selector) throws JMSException
    {
        // Create a message consumer to get the replies with and register this to be called back by it.
        setReplyQueue(getPingQueue());
        MessageConsumer consumer = getConsumerSession().createConsumer(getReplyQueue(), PREFETCH, false, EXCLUSIVE, selector);
        consumer.setMessageListener(this);
    }

    /**
     * Starts a ping-pong loop running from the command line. The bounce back client {@link org.apache.qpid.requestreply.PingPongBouncer} also needs
     * to be started to bounce the pings back again.
     * <p/>
     * <p/>The command line takes from 2 to 4 arguments:
     * <p/><table>
     * <tr><td>brokerDetails <td> The broker connection string.
     * <tr><td>virtualPath   <td> The virtual path.
     * <tr><td>transacted    <td> A boolean flag, telling this client whether or not to use transactions.
     * <tr><td>size          <td> The size of ping messages to use, in bytes.
     * </table>
     *
     * @param args The command line arguments as defined above.
     */
    public static void main(String[] args) throws Exception
    {
        // Extract the command line.
        if (args.length < 2)
        {
            System.err.println("Usage: TestPingPublisher <brokerDetails> <virtual path> [verbose (true/false)] " +
                               "[transacted (true/false)] [persistent (true/false)] [message size in bytes]");
            System.exit(0);
        }

        String brokerDetails = args[0];
        String virtualpath = args[1];
        boolean verbose = (args.length >= 3) ? Boolean.parseBoolean(args[2]) : true;
        boolean transacted = (args.length >= 4) ? Boolean.parseBoolean(args[3]) : false;
        boolean persistent = (args.length >= 5) ? Boolean.parseBoolean(args[4]) : false;
        int messageSize = (args.length >= 6) ? Integer.parseInt(args[5]) : DEFAULT_MESSAGE_SIZE;
        int batchSize = (args.length >= 7) ? Integer.parseInt(args[6]) : 1;

        String queue = "ping_" + System.currentTimeMillis();
        _logger.info("Queue:" + queue + ", Transacted:" + transacted + ", persistent:" + persistent +
                     ",MessageSize:" + messageSize + " bytes");


        boolean afterCommit = false;
        boolean beforeCommit = false;
        boolean afterSend = false;
        boolean beforeSend = false;

        for (String arg : args)
        {
            if (arg.startsWith("failover:"))
            {
                //failover:<before|after>:<send:commit>
                String[] parts = arg.split(":");
                if (parts.length == 3)
                {
                    if (parts[2].equals("commit"))
                    {
                        afterCommit = parts[1].equals("after");
                        beforeCommit = parts[1].equals("before");
                    }

                    if (parts[2].equals("send"))
                    {
                        afterSend = parts[1].equals("after");
                        beforeSend = parts[1].equals("before");
                    }
                }
                else
                {
                    System.out.println("Unrecognized failover request:" + arg);
                }
            }
        }

        // Create a ping producer to handle the request/wait/reply cycle.
        TestPingItself pingItself = new TestPingItself(brokerDetails, "guest", "guest", virtualpath, queue, null,
                                                       transacted, persistent, messageSize, verbose,
                                                       afterCommit, beforeCommit, afterSend, beforeSend,
                                                       batchSize);
        pingItself.getConnection().start();

        // Run a few priming pings to remove warm up time from test results.
        pingItself.prime(PRIMING_LOOPS);
        // Create a shutdown hook to terminate the ping-pong producer.
        Runtime.getRuntime().addShutdownHook(pingItself.getShutdownHook());

        // Ensure that the ping pong producer is registered to listen for exceptions on the connection too.
        pingItself.getConnection().setExceptionListener(pingItself);

        // Create the ping loop thread and run it until it is terminated by the shutdown hook or exception.
        Thread pingThread = new Thread(pingItself);
        pingThread.run();
        pingThread.join();
    }
}
