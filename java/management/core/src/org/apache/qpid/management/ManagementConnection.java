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
package org.apache.qpid.management;

import org.apache.qpid.AMQException;
import org.apache.qpid.url.URLSyntaxException;
import org.apache.qpid.management.messaging.ManagementDestination;
import org.apache.qpid.jms.Session;
import org.apache.qpid.jms.MessageProducer;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.log4j.Logger;

import javax.jms.*;

public class ManagementConnection
{
    private static final Logger _log = Logger.getLogger(ManagementConnection.class);

    private String _brokerHost;

    private int _brokerPort;

    private String _username;

    private String _password;

    private String _virtualPath;

    private AMQConnection _connection;

    private Session _session;

    private MessageConsumer _consumer;

    private MessageProducer _producer;

    private AMQQueue _replyQueue;

    public ManagementConnection(String brokerHost, int brokerPort, String username,
                                String password, String virtualPath)
    {
        _brokerHost = brokerHost;
        _brokerPort = brokerPort;
        _username = username;
        _password = password;
        _virtualPath = virtualPath;
    }

    public void connect() throws AMQException, JMSException, URLSyntaxException
    {
        _connection = new AMQConnection(_brokerHost, _brokerPort, _username, _password,
                                        "clientName" + System.currentTimeMillis(), _virtualPath);
        _connection.setExceptionListener(new ExceptionListener()
        {
            public void onException(JMSException jmsException)
            {
                _log.error("Error occurred: " + jmsException, jmsException);
                try
                {
                    _connection.close();
                }
                catch (JMSException e)
                {
                    _log.error("Error closing connection: " + e, e);
                }
            }
        });
        _session = (Session)_connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        _replyQueue = new AMQQueue("response", true)
        {
            public String getEncodedName()
            {
                return getQueueName();
            }
        };
        _consumer = _session.createConsumer(_replyQueue, 100, true, true, null);

        _producer = (MessageProducer) _session.createProducer(new ManagementDestination());
        _connection.start();
    }

    /**
     * Send a request and wait for a response.
     * @param xmlRequest the request to send
     * @return the response received from the broker
     * @throws AMQException when an AMQ error occurs
     * @throws JMSException when a JMS error occurs
     */
    public TextMessage sendRequest(String xmlRequest) throws AMQException, JMSException
    {
        TextMessage requestMsg = _session.createTextMessage(xmlRequest);
        requestMsg.setJMSReplyTo(_replyQueue);
        _producer.send(requestMsg);
        return (TextMessage) _consumer.receive();
    }

    public void close() throws AMQException, JMSException
    {
        if (_connection != null)
        {
            _connection.close();
        }
    }
}
