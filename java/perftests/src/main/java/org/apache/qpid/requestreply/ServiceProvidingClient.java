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
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.jms.ConnectionListener;
import org.apache.qpid.jms.Session;
import org.apache.qpid.url.URLSyntaxException;

import javax.jms.*;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class ServiceProvidingClient
{
    private static final Logger _logger = Logger.getLogger(ServiceProvidingClient.class);
    private static final String MESSAGE_IDENTIFIER = "MessageIdentifier";
    private MessageProducer _destinationProducer;

    private Destination _responseDest;

    private AMQConnection _connection;

    private Session _session;
    private Session _producerSession;

    private boolean _isTransactional;

    public ServiceProvidingClient(String brokerDetails, String username, String password,
                                  String clientName, String virtualPath, String serviceName,
                                  final int deliveryMode, boolean transactedMode, String selector)
            throws AMQException, JMSException, URLSyntaxException
    {
        _isTransactional = transactedMode;

        _logger.info("Delivery Mode: " + (deliveryMode == DeliveryMode.NON_PERSISTENT ? "Non Persistent" : "Persistent")
                     + "\t isTransactional: " + _isTransactional);

        _connection = new AMQConnection(brokerDetails, username, password,
                                        clientName, virtualPath);
        _connection.setConnectionListener(new ConnectionListener()
        {

            public void bytesSent(long count)
            {
            }

            public void bytesReceived(long count)
            {
            }

            public boolean preFailover(boolean redirect)
            {
                return true;
            }

            public boolean preResubscribe()
            {
                return true;
            }

            public void failoverComplete()
            {
                _logger.info("App got failover complete callback");
            }
        });
        _session = (Session) _connection.createSession(_isTransactional, Session.AUTO_ACKNOWLEDGE);
        _producerSession = (Session) _connection.createSession(_isTransactional, Session.AUTO_ACKNOWLEDGE);

        _logger.info("Service (queue) name is '" + serviceName + "'...");

        AMQQueue destination = new AMQQueue(serviceName);

        MessageConsumer consumer = _session.createConsumer(destination,
                                                           100, true, false, selector);

        consumer.setMessageListener(new MessageListener()
        {
            private int _messageCount;

            public void onMessage(Message message)
            {
                //_logger.info("Got message '" + message + "'");
                TextMessage tm = (TextMessage) message;
                try
                {
                    Destination responseDest = tm.getJMSReplyTo();
                    if (responseDest == null)
                    {
                        _logger.info("Producer not created because the response destination is null.");
                        return;
                    }

                    if (!responseDest.equals(_responseDest))
                    {
                        _responseDest = responseDest;

                        _logger.info("About to create a producer");
                        _destinationProducer = _producerSession.createProducer(responseDest);
                        _destinationProducer.setDisableMessageTimestamp(true);
                        _destinationProducer.setDeliveryMode(deliveryMode);
                        _logger.info("After create a producer");
                    }
                }
                catch (JMSException e)
                {
                    _logger.error("Error creating destination");
                }
                _messageCount++;
                if (_messageCount % 1000 == 0)
                {
                    _logger.info("Received message total: " + _messageCount);
                    _logger.info("Sending response to '" + _responseDest + "'");
                }

                try
                {
                    String payload = "This is a response: sing together: 'Mahnah mahnah...'" + tm.getText();
                    TextMessage msg = _producerSession.createTextMessage(payload);
                    if (tm.propertyExists("timeSent"))
                    {
                        _logger.info("timeSent property set on message");
                        long timesent = tm.getLongProperty("timeSent");
                        _logger.info("timeSent value is: " + timesent);
                        msg.setLongProperty("timeSent", timesent);
                    }
                    // this identifier set in the serviceRequestingClient is used to match the response with the request
                    if (tm.propertyExists(MESSAGE_IDENTIFIER))
                    {
                        msg.setIntProperty(MESSAGE_IDENTIFIER, tm.getIntProperty(MESSAGE_IDENTIFIER));
                    }
                    
                    _destinationProducer.send(msg);

                    if (_isTransactional)
                    {
                        _producerSession.commit();
                    }
                    if (_isTransactional)
                    {
                        _session.commit();
                    }
                    if (_messageCount % 1000 == 0)
                    {
                        _logger.info("Sent response to '" + _responseDest + "'");
                    }
                }
                catch (JMSException e)
                {
                    _logger.error("Error sending message: " + e, e);
                }
            }
        });
    }

    public void run() throws JMSException
    {
        _connection.start();
        _logger.info("Waiting...");
    }

    public static void main(String[] args)
    {
        _logger.info("Starting...");

        if (args.length < 5)
        {
            System.out.println("Usage: serviceProvidingClient <brokerDetails> <username> <password> <virtual-path> <serviceQueue> [<P[ersistent]|N[onPersistent]> <T[ransacted]|N[onTransacted]>] [selector]");
            System.exit(1);
        }
        String clientId = null;
        try
        {
            InetAddress address = InetAddress.getLocalHost();
            clientId = address.getHostName() + System.currentTimeMillis();
        }
        catch (UnknownHostException e)
        {
            _logger.error("Error: " + e, e);
        }


        int deliveryMode = DeliveryMode.NON_PERSISTENT;
        boolean transactedMode = false;

        if (args.length > 7)
        {
            deliveryMode = args[args.length - 2].toUpperCase().charAt(0) == 'P' ? DeliveryMode.PERSISTENT
                           : DeliveryMode.NON_PERSISTENT;

            transactedMode = args[args.length - 1].toUpperCase().charAt(0) == 'T' ? true : false;
        }

        String selector = null;
        if ((args.length == 8) || (args.length == 7))
        {
            selector = args[args.length - 1];
        }

        try
        {
            ServiceProvidingClient client = new ServiceProvidingClient(args[0], args[1], args[2],
                                                                       clientId, args[3], args[4],
                                                                       deliveryMode, transactedMode, selector);
            client.run();
        }
        catch (JMSException e)
        {
            _logger.error("Error: " + e, e);
        }
        catch (AMQException e)
        {
            _logger.error("Error: " + e, e);
        }
        catch (URLSyntaxException e)
        {
            _logger.error("Error: " + e, e);
        }


    }

}

