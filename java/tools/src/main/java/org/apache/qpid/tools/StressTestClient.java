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
package org.apache.qpid.tools;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;

public class StressTestClient
{
    private static final String QUEUE_NAME_PREFIX = "BURL:direct://amq.direct//stress-test-queue";
    private static final String DURABLE_SUFFIX = "?durable='true'";

    public static final String CONNECTIONS_ARG = "connections";
    public static final String SESSIONS_ARG = "sessions";
    public static final String CONSUME_IMMEDIATELY_ARG = "consumeImmediately";
    public static final String CONSUMERS_ARG = "consumers";
    public static final String CLOSE_CONSUMERS_ARG = "closeconsumers";
    public static final String PRODUCERS_ARG = "producers";
    public static final String MESSAGE_COUNT_ARG = "messagecount";
    public static final String MESSAGE_SIZE_ARG = "size";
    public static final String SUFFIX_ARG = "suffix";
    public static final String REPETITIONS_ARG = "repetitions";
    public static final String PERSISTENT_ARG = "persistent";
    public static final String RANDOM_ARG = "random";
    public static final String TIMEOUT_ARG = "timeout";
    public static final String DELAYCLOSE_ARG = "delayclose";
    public static final String REPORT_MOD_ARG = "reportmod";
    public static final String LOW_PREFETCH_ARG = "lowprefetch";
    public static final String TRANSACTED_ARG = "transacted";
    public static final String TX_BATCH_ARG = "txbatch";

    public static final String CONNECTIONS_DEFAULT = "1";
    public static final String SESSIONS_DEFAULT = "1";
    public static final String CONSUME_IMMEDIATELY_DEFAULT = "true";
    public static final String CLOSE_CONSUMERS_DEFAULT = "true";
    public static final String PRODUCERS_DEFAULT = "1";
    public static final String CONSUMERS_DEFAULT = "1";
    public static final String MESSAGE_COUNT_DEFAULT = "1";
    public static final String MESSAGE_SIZE_DEFAULT = "256";
    public static final String SUFFIX_DEFAULT = "";
    public static final String REPETITIONS_DEFAULT = "1";
    public static final String PERSISTENT_DEFAULT = "false";
    public static final String RANDOM_DEFAULT = "true";
    public static final String TIMEOUT_DEFAULT = "30000";
    public static final String DELAYCLOSE_DEFAULT = "0";
    public static final String REPORT_MOD_DEFAULT = "1";
    public static final String LOW_PREFETCH_DEFAULT = "false";
    public static final String TRANSACTED_DEFAULT = "false";
    public static final String TX_BATCH_DEFAULT = "1";

    private static final String CLASS = "StressTestClient";

    public static void main(String[] args)
    {
        Map<String,String> options = new HashMap<>();
        options.put(CONNECTIONS_ARG, CONNECTIONS_DEFAULT);
        options.put(SESSIONS_ARG, SESSIONS_DEFAULT);
        options.put(CONSUME_IMMEDIATELY_ARG, CONSUME_IMMEDIATELY_DEFAULT);
        options.put(PRODUCERS_ARG, PRODUCERS_DEFAULT);
        options.put(CONSUMERS_ARG, CONSUMERS_DEFAULT);
        options.put(CLOSE_CONSUMERS_ARG, CLOSE_CONSUMERS_DEFAULT);
        options.put(MESSAGE_COUNT_ARG, MESSAGE_COUNT_DEFAULT);
        options.put(MESSAGE_SIZE_ARG, MESSAGE_SIZE_DEFAULT);
        options.put(SUFFIX_ARG, SUFFIX_DEFAULT);
        options.put(REPETITIONS_ARG, REPETITIONS_DEFAULT);
        options.put(PERSISTENT_ARG, PERSISTENT_DEFAULT);
        options.put(RANDOM_ARG, RANDOM_DEFAULT);
        options.put(TIMEOUT_ARG, TIMEOUT_DEFAULT);
        options.put(DELAYCLOSE_ARG, DELAYCLOSE_DEFAULT);
        options.put(REPORT_MOD_ARG, REPORT_MOD_DEFAULT);
        options.put(LOW_PREFETCH_ARG, LOW_PREFETCH_DEFAULT);
        options.put(TRANSACTED_ARG, TRANSACTED_DEFAULT);
        options.put(TX_BATCH_ARG, TX_BATCH_DEFAULT);

        if(args.length == 1 &&
                (args[0].equals("-h") || args[0].equals("--help") || args[0].equals("help")))
        {
            System.out.println("arg=value options: \n" + options.keySet());
            return;
        }

        parseArgumentsIntoConfig(options, args);

        StressTestClient testClient = new StressTestClient();
        testClient.runTest(options);
    }

    public static void parseArgumentsIntoConfig(Map<String, String> initialValues, String[] args)
    {
        for(String arg: args)
        {
            String[] splitArg = arg.split("=");
            if(splitArg.length != 2)
            {
                throw new IllegalArgumentException("arguments must have format <name>=<value>: " + arg);
            }

            if(initialValues.put(splitArg[0], splitArg[1]) == null)
            {
                throw new IllegalArgumentException("not a valid configuration property: " + arg);
            }
        }
    }


    private void runTest(Map<String,String> options)
    {
        int numConnections = Integer.parseInt(options.get(CONNECTIONS_ARG));
        int numSessions = Integer.parseInt(options.get(SESSIONS_ARG));
        int numProducers = Integer.parseInt(options.get(PRODUCERS_ARG));
        int numConsumers = Integer.parseInt(options.get(CONSUMERS_ARG));
        boolean closeConsumers = Boolean.valueOf(options.get(CLOSE_CONSUMERS_ARG));
        boolean consumeImmediately = Boolean.valueOf(options.get(CONSUME_IMMEDIATELY_ARG));
        int numMessage = Integer.parseInt(options.get(MESSAGE_COUNT_ARG));
        int messageSize = Integer.parseInt(options.get(MESSAGE_SIZE_ARG));
        int repetitions = Integer.parseInt(options.get(REPETITIONS_ARG));
        String queueString = QUEUE_NAME_PREFIX + options.get(SUFFIX_ARG) + DURABLE_SUFFIX;
        int deliveryMode = Boolean.valueOf(options.get(PERSISTENT_ARG)) ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT;
        boolean random = Boolean.valueOf(options.get(RANDOM_ARG));
        long recieveTimeout = Long.parseLong(options.get(TIMEOUT_ARG));
        long delayClose = Long.parseLong(options.get(DELAYCLOSE_ARG));
        int reportingMod = Integer.parseInt(options.get(REPORT_MOD_ARG));
        boolean lowPrefetch = Boolean.valueOf(options.get(LOW_PREFETCH_ARG));
        boolean transacted = Boolean.valueOf(options.get(TRANSACTED_ARG));
        int txBatch = Integer.parseInt(options.get(TX_BATCH_ARG));

        System.out.println(CLASS + ": Using options: " + options);

        System.out.println(CLASS + ": Creating message payload of " + messageSize + " (bytes)");
        byte[] sentBytes = generateMessage(random, messageSize);

        try
        {
            // Load JNDI properties
            Properties properties = new Properties();
            try(InputStream is = this.getClass().getClassLoader().getResourceAsStream("stress-test-client.properties"))
            {
                properties.load(is);
            }
            Context ctx = new InitialContext(properties);

            ConnectionFactory conFac;
            if(lowPrefetch)
            {
                System.out.println(CLASS + ": Using lowprefetch connection factory");
                conFac = (ConnectionFactory)ctx.lookup("qpidConnectionfactoryLowPrefetch");
            }
            else
            {
                conFac = (ConnectionFactory)ctx.lookup("qpidConnectionfactory");
            }

            //ensure the queue to be used exists and is bound
            System.out.println(CLASS + ": Creating queue: " + queueString);
            Connection startupConn = conFac.createConnection();
            Session startupSess = startupConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination startupDestination = startupSess.createQueue(queueString);
            MessageConsumer startupConsumer = startupSess.createConsumer(startupDestination);
            startupConsumer.close();
            startupSess.close();
            startupConn.close();

            for(int rep = 1 ; rep <= repetitions; rep++)
            {
                ArrayList<Connection> connectionList = new ArrayList<>();

                for (int co= 1; co<= numConnections ; co++)
                {
                    if( co % reportingMod == 0)
                    {
                        System.out.println(CLASS + ": Creating connection " + co);
                    }
                    Connection conn = conFac.createConnection();
                    conn.setExceptionListener(new ExceptionListener()
                    {
                        public void onException(JMSException jmse)
                        {
                            System.err.println(CLASS + ": The sample received an exception through the ExceptionListener");
                            jmse.printStackTrace();
                            System.exit(0);
                        }
                    });

                    connectionList.add(conn);
                    conn.start();
                    for (int se= 1; se<= numSessions ; se++)
                    {
                        if( se % reportingMod == 0)
                        {
                            System.out.println(CLASS + ": Creating Session " + se);
                        }
                        try
                        {
                            Session sess;
                            if(transacted)
                            {
                                sess = conn.createSession(true, Session.SESSION_TRANSACTED);
                            }
                            else
                            {
                                sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
                            }

                            BytesMessage message = sess.createBytesMessage();

                            message.writeBytes(sentBytes);

                            if(!random && numMessage == 1 && numSessions == 1 && numConnections == 1 && repetitions == 1)
                            {
                                //null the array to save memory
                                sentBytes = null;
                            }

                            Destination destination = sess.createQueue(queueString);

                            MessageConsumer consumer = null;
                            for(int cns = 1 ; cns <= numConsumers ; cns++)
                            {
                                if( cns % reportingMod == 0)
                                {
                                    System.out.println(CLASS + ": Creating Consumer " + cns);
                                }
                                consumer = sess.createConsumer(destination);
                            }

                            for(int pr = 1 ; pr <= numProducers ; pr++)
                            {
                                if( pr % reportingMod == 0)
                                {
                                    System.out.println(CLASS + ": Creating Producer " + pr);
                                }
                                MessageProducer prod = sess.createProducer(destination);
                                for(int me = 1; me <= numMessage ; me++)
                                {
                                    if( me % reportingMod == 0)
                                    {
                                        System.out.println(CLASS + ": Sending Message " + me);
                                    }
                                    prod.send(message, deliveryMode,
                                            Message.DEFAULT_PRIORITY,
                                            Message.DEFAULT_TIME_TO_LIVE);
                                    if(transacted && me % txBatch == 0)
                                    {
                                        sess.commit();
                                    }
                                }
                            }

                            if(numConsumers > 0 && consumeImmediately)
                            {
                                for(int cs = 1 ; cs <= numMessage ; cs++)
                                {
                                    if( cs % reportingMod == 0)
                                    {
                                        System.out.println(CLASS + ": Consuming Message " + cs);
                                    }
                                    BytesMessage msg = (BytesMessage) consumer.receive(recieveTimeout);

                                    if(transacted && cs % txBatch == 0)
                                    {
                                        sess.commit();
                                    }

                                    if(msg == null)
                                    {
                                        throw new RuntimeException("Expected message not received in allowed time: " + recieveTimeout);
                                    }

                                    validateReceivedMessageContent(sentBytes, msg, random, messageSize);
                                }

                                if(closeConsumers)
                                {
                                    consumer.close();
                                }
                            }

                        }
                        catch (Exception exp)
                        {
                            System.err.println(CLASS + ": Caught an Exception: " + exp);
                            exp.printStackTrace();
                        }

                    }
                }

                if(numConsumers == -1 && !consumeImmediately)
                {
                    System.out.println(CLASS + ": Consuming left over messages, using recieve timeout:" + recieveTimeout);

                    Connection conn = conFac.createConnection();
                    Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
                    Destination destination = sess.createQueue(queueString);
                    MessageConsumer consumer = sess.createConsumer(destination);
                    conn.start();

                    int count = 0;
                    while(true)
                    {
                        BytesMessage msg = (BytesMessage) consumer.receive(recieveTimeout);

                        if(msg == null)
                        {
                            System.out.println(CLASS + ": Received " + count + " messages");
                            break;
                        }
                        else
                        {
                            count++;
                        }

                        validateReceivedMessageContent(sentBytes, msg, random, messageSize);
                    }

                    consumer.close();
                    sess.close();
                    conn.close();
                }

                if(delayClose > 0)
                {
                    System.out.println(CLASS + ": Delaying closing connections: " + delayClose);
                    Thread.sleep(delayClose);
                }

                // Close the connections to the server
                System.out.println(CLASS + ": Closing connections");

                for(int connection = 0 ; connection < connectionList.size() ; connection++)
                {
                    if( (connection+1) % reportingMod == 0)
                    {
                        System.out.println(CLASS + ": Closing connection " + (connection+1));
                    }
                    Connection c = connectionList.get(connection);
                    c.close();
                }

                // Close the JNDI reference
                System.out.println(CLASS + ": Closing JNDI context");
                ctx.close();
            }
        }
        catch (Exception exp)
        {
            System.err.println(CLASS + ": Caught an Exception: " + exp);
            exp.printStackTrace();
        }
    }


    private byte[] generateMessage(boolean random, int messageSize)
    {
        byte[] sentBytes = new byte[messageSize];
        if(random)
        {
            //fill the array with numbers from 0-9
            Random rand = new Random(System.currentTimeMillis());
            for(int r = 0 ; r < messageSize ; r++)
            {
                sentBytes[r] = (byte) (48 + rand.nextInt(10));
            }
        }
        else
        {
            //use sequential numbers from 0-9
            for(int r = 0 ; r < messageSize ; r++)
            {
                sentBytes[r] = (byte) (48 + (r % 10));
            }
        }
        return sentBytes;
    }


    private void validateReceivedMessageContent(byte[] sentBytes,
            BytesMessage msg, boolean random, int messageSize) throws JMSException
    {
        Long length = msg.getBodyLength();

        if(length != messageSize)
        {
            throw new RuntimeException("Incorrect number of bytes received");
        }

        byte[] recievedBytes = new byte[length.intValue()];
        msg.readBytes(recievedBytes);

        if(random)
        {
            if(!Arrays.equals(sentBytes, recievedBytes))
            {
                throw new RuntimeException("Incorrect value of bytes received");
            }
        }
        else
        {
            for(int r = 0 ; r < messageSize ; r++)
            {
                if(! (recievedBytes[r] == (byte) (48 + (r % 10))))
                {
                    throw new RuntimeException("Incorrect value of bytes received");
                }
            }
        }
    }
}

