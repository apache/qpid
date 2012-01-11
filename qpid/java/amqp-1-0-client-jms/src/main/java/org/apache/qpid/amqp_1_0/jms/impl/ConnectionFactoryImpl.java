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
package org.apache.qpid.amqp_1_0.jms.impl;

import org.apache.qpid.amqp_1_0.jms.Connection;
import org.apache.qpid.amqp_1_0.jms.ConnectionFactory;

import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import java.net.MalformedURLException;
import java.net.URL;

public class ConnectionFactoryImpl implements ConnectionFactory, TopicConnectionFactory, QueueConnectionFactory
{
    private String _host;
    private int _port;
    private String _username;
    private String _password;
    private String _clientId;
    private boolean _ssl;


    public ConnectionFactoryImpl(final String host,
                                 final int port,
                                 final String username,
                                 final String password,
                                 final String clientId)
    {
        this(host,port,username,password,clientId,false);
    }

    public ConnectionFactoryImpl(final String host,
                                 final int port,
                                 final String username,
                                 final String password,
                                 final String clientId,
                                 final boolean ssl)
    {
        _host = host;
        _port = port;
        _username = username;
        _password = password;
        _clientId = clientId;
        _ssl = ssl;
    }

    public ConnectionImpl createConnection() throws JMSException
    {
        return new ConnectionImpl(_host, _port, _username, _password, _clientId, _ssl);
    }

    public ConnectionImpl createConnection(final String username, final String password) throws JMSException
    {
        return new ConnectionImpl(_host, _port, username, password, _clientId, _ssl);
    }

    public static ConnectionFactoryImpl createFromURL(final String urlString) throws MalformedURLException
    {
        URL url = new URL(urlString);
        String host = url.getHost();
        int port = url.getPort();
        if(port == -1)
        {
            port = 5672;
        }
        String userInfo = url.getUserInfo();
        String username = null;
        String password = null;
        String clientId = null;
        boolean ssl = false;
        if(userInfo != null)
        {
            String[] components = userInfo.split(":",2);
            username = components[0];
            if(components.length == 2)
            {
                password = components[1];
            }
        }
        String query = url.getQuery();
        if(query != null)
        {
           for(String param : query.split("&"))
           {
               String[] keyValuePair = param.split("=",2);
               if(keyValuePair[0].equalsIgnoreCase("clientid"))
               {
                   clientId = keyValuePair[1];
               }
               else if(keyValuePair[0].equalsIgnoreCase("ssl"))
               {
                   ssl = Boolean.valueOf(keyValuePair[1]);
               }
           }
        }

        return new ConnectionFactoryImpl(host, port, username, password, clientId, ssl);

    }

    public QueueConnection createQueueConnection() throws JMSException
    {
        final ConnectionImpl connection = createConnection();
        connection.setQueueConnection(true);
        return connection;
    }

    public QueueConnection createQueueConnection(final String username, final String password) throws JMSException
    {
        final ConnectionImpl connection = createConnection(username, password);
        connection.setQueueConnection(true);
        return connection;
    }

    public TopicConnection createTopicConnection() throws JMSException
    {
        final ConnectionImpl connection = createConnection();
        connection.setTopicConnection(true);
        return connection;
    }

    public TopicConnection createTopicConnection(final String username, final String password) throws JMSException
    {
        final ConnectionImpl connection = createConnection(username, password);
        connection.setTopicConnection(true);
        return connection;
    }
}
