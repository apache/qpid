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

import org.apache.log4j.Logger;
import org.apache.qpid.requestreply.PingPongProducer;
import org.apache.qpid.topic.Config;

import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.ObjectMessage;

/**
 * This class is used to test sending and receiving messages to (pingQueue) and from a queue (replyQueue).
 * The producer and consumer created by this test send and receive messages to and from the same Queue. ie.
 * pingQueue and replyQueue are same.
 * This class extends @see org.apache.qpid.requestreply.PingPongProducer which different ping and reply Queues
 */
public class TestPingItself extends PingPongProducer
{
    private static final Logger _logger = Logger.getLogger(TestPingItself.class);

    /**
     * This creates a client for pinging to a Queue. There will be one producer and one consumer instance. Consumer
     * listening to the same Queue, producer is sending to
     *
     * @param brokerDetails
     * @param username
     * @param password
     * @param virtualpath
     * @param queueName
     * @param selector
     * @param transacted
     * @param persistent
     * @param messageSize
     * @param verbose
     * @param afterCommit
     * @param beforeCommit
     * @param afterSend
     * @param beforeSend
     * @param batchSize
     * @throws Exception
     */
    public TestPingItself(String brokerDetails, String username, String password, String virtualpath, String queueName,
                          String selector, boolean transacted, boolean persistent, int messageSize, boolean verbose,
                          boolean afterCommit, boolean beforeCommit, boolean afterSend, boolean beforeSend, boolean failOnce,
                          int batchSize)
            throws Exception
    {
        super(brokerDetails, username, password, virtualpath, queueName, selector, transacted, persistent, messageSize,
              verbose, afterCommit, beforeCommit, afterSend, beforeSend, failOnce, batchSize, 0);
    }

    /**
     * This creats a client for tests with multiple queues. Creates as many consumer instances as there are queues,
     * each listening to a Queue. A producer is created which picks up a queue from the list of queues to send message.
     *
     * @param brokerDetails
     * @param username
     * @param password
     * @param virtualpath
     * @param selector
     * @param transacted
     * @param persistent
     * @param messageSize
     * @param verbose
     * @param afterCommit
     * @param beforeCommit
     * @param afterSend
     * @param beforeSend
     * @param batchSize
     * @param queueCount
     * @throws Exception
     */
    public TestPingItself(String brokerDetails, String username, String password, String virtualpath,
                          String selector, boolean transacted, boolean persistent, int messageSize, boolean verbose,
                          boolean afterCommit, boolean beforeCommit, boolean afterSend, boolean beforeSend, boolean failOnce,
                          int batchSize, int queueCount)
            throws Exception
    {
        super(brokerDetails, username, password, virtualpath, null, null, transacted, persistent, messageSize,
              verbose, afterCommit, beforeCommit, afterSend, beforeSend, failOnce, batchSize, queueCount);

        createQueues(queueCount);

        _persistent = persistent;
        _messageSize = messageSize;
        _verbose = verbose;

        createConsumers(selector);
        createProducer();
    }

    /**
     * Sets the replyQueue to be the same as ping queue.
     */
    @Override
    public void createConsumer(String selector) throws JMSException
    {
        // Create a message consumer to get the replies with and register this to be called back by it.
        setReplyQueue(getPingQueue());
        MessageConsumer consumer = getConsumerSession().createConsumer(getReplyQueue(), PREFETCH, false, EXCLUSIVE, selector);
        consumer.setMessageListener(this);
    }

    /**
     * Starts a ping-pong loop running from the command line.
     *
     * @param args The command line arguments as defined above.
     */
    public static void main(String[] args) throws Exception
    {
        // Extract the command line.
        Config config = new Config();
        config.setOptions(args);

        String brokerDetails = config.getHost() + ":" + config.getPort();
        String virtualpath = "/test";
        boolean verbose = false;
        boolean transacted = config.isTransacted();
        boolean persistent = config.usePersistentMessages();
        int messageSize = config.getPayload() != 0 ? config.getPayload() : DEFAULT_MESSAGE_SIZE;
        int messageCount = config.getMessages();
        int queueCount = config.getQueueCount();
        int batchSize = config.getBatchSize() != 0 ? config.getBatchSize() : BATCH_SIZE;

        String queue = "ping_" + System.currentTimeMillis();
        _logger.info("Queue:" + queue + ", Transacted:" + transacted + ", persistent:" + persistent +
                     ",MessageSize:" + messageSize + " bytes");


        boolean afterCommit = false;
        boolean beforeCommit = false;
        boolean afterSend = false;
        boolean beforeSend = false;
        boolean failOnce = false;

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
                    if (parts[1].equals("once"))
                    {
                        failOnce = true;
                    }

                }
                else
                {
                    System.out.println("Unrecognized failover request:" + arg);
                }
            }
        }

        TestPingItself pingItself = null;
        // Create a ping producer to handle the request/wait/reply cycle.
        if (queueCount > 1)
        {
            pingItself = new TestPingItself(brokerDetails, "guest", "guest", virtualpath, null,
                                            transacted, persistent, messageSize, verbose,
                                            afterCommit, beforeCommit, afterSend, beforeSend, failOnce,
                                            batchSize, queueCount);
        }
        else
        {
            pingItself = new TestPingItself(brokerDetails, "guest", "guest", virtualpath, queue, null,
                                            transacted, persistent, messageSize, verbose,
                                            afterCommit, beforeCommit, afterSend, beforeSend, failOnce,
                                            batchSize);
        }

        pingItself.getConnection().start();

        // Create a shutdown hook to terminate the ping-pong producer.
        Runtime.getRuntime().addShutdownHook(pingItself.getShutdownHook());

        // Ensure that the ping pong producer is registered to listen for exceptions on the connection too.
        pingItself.getConnection().setExceptionListener(pingItself);

        if ((queueCount > 1) || (messageCount > 0))
        {
            ObjectMessage msg = pingItself.getTestMessage(null, messageSize, persistent);

            // Send the message and wait for a reply.
            int numReplies = pingItself.pingAndWaitForReply(msg, messageCount, TIMEOUT);

            _logger.info(("Messages Sent = " + messageCount + ", MessagesReceived = " + numReplies));
        }
        else
        {
            // set the message count to 0 to run this loop
            // Run a few priming pings to remove warm up time from test results.
            pingItself.prime(PRIMING_LOOPS);

            _logger.info("Running the infinite loop and pinging the broker...");
            // Create the ping loop thread and run it until it is terminated by the shutdown hook or exception.
            Thread pingThread = new Thread(pingItself);
            pingThread.run();
            pingThread.join();
        }
        pingItself.getConnection().close();
    }

    private static void usage()
    {
        System.err.println("Usage: TestPingPublisher \n" +
                           "-host : broker host" +
                           "-port : broker port" +
                           "-transacted : (true/false). Default is false" +
                           "-persistent : (true/false). Default is false" +
                           "-payload    : paylaod size. Default is 0" +
                           "-queues     : no of queues" +
                           "-messages   : no of messages to be sent (if 0, the ping loop will run indefinitely)");
        System.exit(0);
    }
}
