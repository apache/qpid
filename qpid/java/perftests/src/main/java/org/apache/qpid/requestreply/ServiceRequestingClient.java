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
import org.apache.qpid.url.URLSyntaxException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.jms.MessageConsumer;
import org.apache.qpid.jms.MessageProducer;
import org.apache.qpid.jms.Session;

import javax.jms.*;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * A client that behaves as follows:
 * <ul><li>Connects to a queue, whose name is specified as a cmd-line argument</li>
 * <li>Creates a temporary queue</li>
 * <li>Creates messages containing a property that is the name of the temporary queue</li>
 * <li>Fires off a message on the original queue and waits for a response on the temporary queue</li>
 * </ul>
 */
public class ServiceRequestingClient implements ExceptionListener
{
    private static final Logger _log = Logger.getLogger(ServiceRequestingClient.class);

    private static final String MESSAGE_DATA_BYTES = "jfd ghljgl hjvhlj cvhvjf ldhfsj lhfdsjf hldsjfk hdslkfj hsdflk  ";

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

    private static String createMessagePayload(int size)
    {
        _log.info("Message size set to " + size + " bytes");
        StringBuffer buf = new StringBuffer(size);
        int count = 0;
        while (count < size + MESSAGE_DATA_BYTES.length())
        {
            buf.append(MESSAGE_DATA_BYTES);
            count += MESSAGE_DATA_BYTES.length();
        }
        if (count < size)
        {
            buf.append(MESSAGE_DATA_BYTES, 0, size - count);
        }

        return buf.toString();
    }

    private class CallbackHandler implements MessageListener
    {
        private int _expectedMessageCount;

        private int _actualMessageCount;

        private long _startTime;

        public CallbackHandler(int expectedMessageCount, long startTime)
        {
            _expectedMessageCount = expectedMessageCount;
            _startTime = startTime;
        }

        public void onMessage(Message m)
        {
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
                    long now = System.currentTimeMillis();
                    if (_averageLatency == 0)
                    {
                        _averageLatency = now - timeSent;
                        _log.info("Latency " + _averageLatency);
                    }
                    else
                    {
                        _log.info("Individual latency: " + (now - timeSent));
                        _averageLatency = (_averageLatency + (now - timeSent)) / 2;
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

            if (_actualMessageCount == _expectedMessageCount)
            {
                _completed = true;
                notifyWaiter();
                long timeTaken = System.currentTimeMillis() - _startTime;
                _log.info("Total time taken to receive " + _expectedMessageCount + " messages was " +
                          timeTaken + "ms, equivalent to " +
                          (_expectedMessageCount / (timeTaken / 1000.0)) + " messages per second");

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
								   String deliveryModeString, String transactedMode,
                                   final int messageCount, final int messageDataLength) throws AMQException, URLSyntaxException
    {
		final int deliveryMode = deliveryModeString.toUpperCase().charAt(0) == 'P' ? DeliveryMode.PERSISTENT
																			   : DeliveryMode.NON_PERSISTENT;

		_isTransactional = transactedMode.toUpperCase().charAt(0) == 'T' ? true : false;

        _log.info("Delivery Mode: " + deliveryMode + "\t isTransactional: " + _isTransactional);

        _messageCount = messageCount;
        MESSAGE_DATA = createMessagePayload(messageDataLength);
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
			if(_isTransactional)
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

            messageConsumer.setMessageListener(new CallbackHandler(messageCount, startTime));
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
            if(_isTransactional)
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
        _connection = new AMQConnection(brokerHosts, username, password,
                                        clientID, vpath);
    }

    /**
     * @param args argument 1 if present specifies the name of the temporary queue to create. Leaving it blank
     *             means the server will allocate a name.
     */
    public static void main(String[] args)
    {
        if (args.length < 9)
        {
            System.err.println(
                    "Usage: ServiceRequestingClient <brokerDetails - semicolon separated host:port list> <username> <password> <vpath> <command queue name> <P[ersistent]|N[onPersistent]>  <T[ransacted]|N[onTransacted]> <number of messages> <message size>");
            System.exit(1);
        }
        try
        {
            int messageDataLength = args.length > 8 ? Integer.parseInt(args[8]) : 4096;

            InetAddress address = InetAddress.getLocalHost();
            String clientID = address.getHostName() + System.currentTimeMillis();
            ServiceRequestingClient client = new ServiceRequestingClient(args[0], clientID, args[1], args[2], args[3],
                                                                         args[4], args[5], args[6], Integer.parseInt(args[7]),
                                                                         messageDataLength);
            Object waiter = new Object();
            client.run(waiter);
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
