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

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.message.TestMessageFactory;
import org.apache.qpid.client.message.JMSTextMessage;
import org.apache.qpid.jms.MessageConsumer;
import org.apache.qpid.jms.MessageProducer;
import org.apache.qpid.jms.Session;
import org.apache.qpid.url.URLSyntaxException;

import javax.jms.*;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * A client that behaves as follows:
 * <ul><li>Connects to a queue, whose name is specified as a cmd-line argument</li>
 * <li>Creates a temporary queue</li>
 * <li>Creates messages containing a property(reply-to) that is the name of the temporary queue</li>
 * <li>Fires off a message on the original queue and registers the callbackHandler to listen to the response on the temporary queue</li>
 * <li>Start the loop to send all messages</li>
 * <li>CallbackHandler keeps listening to the responses and exits if all the messages have been received back or
 *  if the waiting time for next message is elapsed</li>
 * </ul>
 */
public class ServiceRequestingClient implements ExceptionListener
{
    private static final Logger _log = Logger.getLogger(ServiceRequestingClient.class);

    private long _messageIdentifier = 0;

    // time for which callbackHandler should wait for a message before exiting. Default time= 60 secs
    private static long _callbackHandlerWaitingTime = 60000;

    private String MESSAGE_DATA;

    private AMQConnection _connection;

    private Session _session;
    private Session _producerSession;

    private long _averageLatency;

    private int _messageCount;
    private boolean _isTransactional;

    private volatile boolean _completed;

    private AMQDestination _tempDestination;

    private MessageProducer _producer;

    private Object _waiter;

    private class CallbackHandler implements MessageListener
    {
        private int _actualMessageCount;

        private long _startTime;
        // The time when the last message was received by the callbackHandler
        private long _messageReceivedTime = 0;
        private Object _timerCallbackHandler = new Object();

        public CallbackHandler(long startTime)
        {
            _startTime = startTime;
            // Start the timer thread, which will keep checking if test should exit because the waiting time has elapsed
            (new Thread(new TimerThread())).start();
        }

        public void onMessage(Message m)
        {
            _messageReceivedTime = System.currentTimeMillis();
            if (_log.isDebugEnabled())
            {
                _log.debug("Message received: " + m);
            }
            try
            {
                m.getPropertyNames();
                if (m.propertyExists("timeSent"))
                {
                    long timeSent = Long.parseLong(m.getStringProperty("timeSent"));
                    if (_averageLatency == 0)
                    {
                        _averageLatency = _messageReceivedTime - timeSent;
                        _log.info("Latency " + _averageLatency);
                    }
                    else
                    {
                        _log.info("Individual latency: " + (_messageReceivedTime - timeSent));
                        _averageLatency = (_averageLatency + (_messageReceivedTime - timeSent)) / 2;
                        _log.info("Average latency now: " + _averageLatency);
                    }
                }
                if(_isTransactional)
                {
                    _session.commit();
                }
            }
            catch (JMSException e)
            {
                _log.error("Error getting latency data: " + e, e);
            }
            _actualMessageCount++;
            if (_actualMessageCount % 1000 == 0)
            {
                _log.info("Received message count: " + _actualMessageCount);
            }

            checkForMessageID(m);

            if (_actualMessageCount == _messageCount)
            {
                finishTesting(_actualMessageCount);
            }
        }

        /**
         * sets completed flag to true, closes the callbackHandler connection and notifies the waiter thread,
         * so that the callbackHandler can finish listening for messages. This causes the test to finish.
         * @param receivedMessageCount
         */
        private void finishTesting(int receivedMessageCount)
        {
            _completed = true;
            notifyWaiter();
            notifyTimerThread();

            long timeTaken = System.currentTimeMillis() - _startTime;
            _log.info("***** Result *****");
            _log.info("Total messages received = " + receivedMessageCount);
            _log.info("Total time taken to receive " + receivedMessageCount + " messages was " +
                      timeTaken + "ms, equivalent to " +
                      (receivedMessageCount / (timeTaken / 1000.0)) + " messages per second");

            try
            {
                _connection.close();
                _log.info("Connection closed");
            }
            catch (JMSException e)
            {
                _log.error("Error closing connection");
            }
        }

        private void notifyTimerThread()
        {
            if (_timerCallbackHandler != null)
            {
                synchronized (_timerCallbackHandler)
                {
                    _timerCallbackHandler.notify();
                }
            }
        }

        /**
         * Thread class implementing the timer for callbackHandler. The thread will exit the test if the waiting time
         * has elapsed before next message is received.
         */
        private class TimerThread implements Runnable
        {
            public void run()
            {
                do
                {
                    try
                    {
                        synchronized(_timerCallbackHandler)
                        {
                            _timerCallbackHandler.wait(_callbackHandlerWaitingTime);
                        }
                    }
                    catch (InterruptedException ignore)
                    {

                    }

                    // exit if callbackHandler has received all messages
                    if (_completed)
                    {
                        _log.info("timer " + new java.util.Date());
                        return;
                    }
                }
                while ((System.currentTimeMillis() - _messageReceivedTime) < _callbackHandlerWaitingTime);

                // waiting time has elapsed, so exit the test
                _log.info("");
                _log.info("Exited after waiting for " + _callbackHandlerWaitingTime/1000 + " secs");
                finishTesting(_actualMessageCount);
            }
        }
    } // end of CallbackHandler class

    /**
     * Checks if the received AMQ Message ID(delivery tag) is in sequence, by comparing it with the AMQ MessageID
     * of previous message.
     * @param receivedMsg
     */
    private void checkForMessageID(Message receivedMsg)
    {
        try
        {
            JMSTextMessage msg = (JMSTextMessage)receivedMsg;
            if (! (msg.getDeliveryTag() == _messageIdentifier + 1))
            {
                _log.info("Out of sequence message received. Previous AMQ MessageID= " + _messageIdentifier +
                          ", Received AMQ messageID= " + receivedMsg.getJMSMessageID());
            }
            _messageIdentifier = msg.getDeliveryTag();
        }
        catch (Exception ex)
        {
            _log.error("Error in checking messageID ", ex);
        }

    }

    private void notifyWaiter()
    {
        if (_waiter != null)
        {
            synchronized (_waiter)
            {
                _waiter.notify();
            }
        }
    }

    public ServiceRequestingClient(String brokerHosts, String clientID, String username, String password,
                                   String vpath, String commandQueueName,
                                   int deliveryMode, boolean transactedMode,
                                   final int messageCount, final int messageDataLength) throws AMQException, URLSyntaxException
    {
        _isTransactional = transactedMode;

        _log.info("Delivery Mode: " + (deliveryMode == DeliveryMode.NON_PERSISTENT ? "Non Persistent" : "Persistent") +
                  "\t isTransactional: " + _isTransactional);

        _messageCount = messageCount;
        MESSAGE_DATA = TestMessageFactory.createMessagePayload(messageDataLength);
        try
        {
            createConnection(brokerHosts, clientID, username, password, vpath);
            _session = (Session) _connection.createSession(_isTransactional, Session.AUTO_ACKNOWLEDGE);
            _producerSession = (Session) _connection.createSession(_isTransactional, Session.AUTO_ACKNOWLEDGE);

            _connection.setExceptionListener(this);

            AMQQueue destination = new AMQQueue(commandQueueName);
            _producer = (MessageProducer) _producerSession.createProducer(destination);
            _producer.setDisableMessageTimestamp(true);
            _producer.setDeliveryMode(deliveryMode);

            _tempDestination = new AMQQueue("TempResponse" +
                                            Long.toString(System.currentTimeMillis()), true);
            MessageConsumer messageConsumer = (MessageConsumer) _session.createConsumer(_tempDestination, 100, true,
                                                                                        true, null);

            //Send first message, then wait a bit to allow the provider to get initialised
            TextMessage first = _session.createTextMessage(MESSAGE_DATA);
            first.setJMSReplyTo(_tempDestination);
             _producer.send(first);
            if (_isTransactional)
            {
                _producerSession.commit();
            }
            try
            {
                Thread.sleep(1000);
            }
            catch (InterruptedException ignore)
            {
            }

            //now start the clock and the test...
            final long startTime = System.currentTimeMillis();

            messageConsumer.setMessageListener(new CallbackHandler(startTime));
        }
        catch (JMSException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    /**
     * Run the test and notify an object upon receipt of all responses.
     *
     * @param waiter the object that will be notified
     * @throws JMSException
     */
    public void run(Object waiter) throws JMSException
    {
        _waiter = waiter;
        _connection.start();
        for (int i = 1; i < _messageCount; i++)
        {
            TextMessage msg = _producerSession.createTextMessage(MESSAGE_DATA + i);
            msg.setJMSReplyTo(_tempDestination);
            if (i % 1000 == 0)
            {
                long timeNow = System.currentTimeMillis();
                msg.setLongProperty("timeSent", timeNow);
            }
             _producer.send(msg);
            if (_isTransactional)
            {
                _producerSession.commit();
            }

        }
        _log.info("Finished sending " + _messageCount + " messages");
    }

    public boolean isCompleted()
    {
        return _completed;
    }

    private void createConnection(String brokerHosts, String clientID, String username, String password,
                                  String vpath) throws AMQException, URLSyntaxException
    {
        _connection = new AMQConnection(brokerHosts, username, password, clientID, vpath);
    }

    /**
     * @param args argument 1 if present specifies the name of the temporary queue to create. Leaving it blank
     *             means the server will allocate a name.
     */
    public static void main(String[] args)
    {
        if ((args.length < 6) || (args.length == 8))
        {
            System.err.println("Usage: ServiceRequestingClient <brokerDetails> <username> <password> <vpath> " +
                    "<command queue name> <number of messages> [<message size>] " +
                    "[<P[ersistent]|N[onPersistent] (Default N)>  <T[ransacted]|N[onTransacted] (Default N)>] " +
                    "[<waiting time for response in sec (default 60 sec)>]");
            System.exit(1);
        }
        try
        {
            int messageSize = 4096;
            boolean transactedMode = false;
            int deliveryMode = DeliveryMode.NON_PERSISTENT;

            if (args.length > 6)
            {
                messageSize = Integer.parseInt(args[6]);
            }
            if (args.length > 7)
            {
                deliveryMode = args[7].toUpperCase().charAt(0) == 'P' ? DeliveryMode.PERSISTENT
                               : DeliveryMode.NON_PERSISTENT;

                transactedMode = args[8].toUpperCase().charAt(0) == 'T' ? true : false;
            }

            if (args.length > 9)
            {
                _callbackHandlerWaitingTime = Long.parseLong(args[9]) * 1000;
            }

            _log.info("Each message size = " + messageSize + " bytes");

            InetAddress address = InetAddress.getLocalHost();
            String clientID = address.getHostName() + System.currentTimeMillis();
            ServiceRequestingClient client = new ServiceRequestingClient(args[0], clientID, args[1], args[2], args[3],
                                                                         args[4], deliveryMode, transactedMode, Integer.parseInt(args[5]),
                                                                         messageSize);
            Object waiter = new Object();
            client.run(waiter);

            // Start a thread to
            synchronized (waiter)
            {
                while (!client.isCompleted())
                {
                    waiter.wait();
                }
            }
        }
        catch (UnknownHostException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        catch (Exception e)
        {
            System.err.println("Error in client: " + e);
            e.printStackTrace();
        }
    }

    /**
     * @see javax.jms.ExceptionListener#onException(javax.jms.JMSException)
     */
    public void onException(JMSException e)
    {
        System.err.println(e.getMessage());
        e.printStackTrace(System.err);
    }
}
