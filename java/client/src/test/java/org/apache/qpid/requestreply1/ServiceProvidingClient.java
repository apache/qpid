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
package org.apache.qpid.requestreply1;

import org.apache.log4j.Logger;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.jms.Session;
import org.apache.qpid.jms.ConnectionListener;
import org.apache.qpid.AMQException;
import org.apache.qpid.url.URLSyntaxException;

import javax.jms.*;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class ServiceProvidingClient
{
    private static final Logger _logger = Logger.getLogger(ServiceProvidingClient.class);

    private MessageProducer _destinationProducer;

    private Destination _responseDest;

    private AMQConnection _connection;

    public ServiceProvidingClient(String brokerDetails, String username, String password,
                                  String clientName, String virtualPath, String serviceName)
            throws AMQException, JMSException, URLSyntaxException
    {
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
        final Session session = (Session) _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        _logger.info("Service (queue) name is '" + serviceName + "'...");

        AMQQueue destination = new AMQQueue(serviceName);

        MessageConsumer consumer = session.createConsumer(destination,
                                                          100, true, false, null);

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
                        _destinationProducer = session.createProducer(responseDest);
                        _destinationProducer.setDisableMessageTimestamp(true);
                        _destinationProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
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
                    TextMessage msg = session.createTextMessage(payload);
                    if (tm.propertyExists("timeSent"))
                    {
                        _logger.info("timeSent property set on message");
                        _logger.info("timeSent value is: " + tm.getLongProperty("timeSent"));
                        msg.setStringProperty("timeSent", tm.getStringProperty("timeSent"));
                    }
                    _destinationProducer.send(msg);
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
            System.out.println("Usage: brokerDetails username password virtual-path serviceQueue [selector]");
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

        try
        {
            ServiceProvidingClient client = new ServiceProvidingClient(args[0], args[1], args[2],
                                                                       clientId, args[3], args[4]);
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

