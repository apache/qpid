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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLStreamHandler;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.qpid.amqp_1_0.client.SSLUtil;
import org.apache.qpid.amqp_1_0.jms.ConnectionFactory;


public class ConnectionFactoryImpl implements ConnectionFactory, TopicConnectionFactory, QueueConnectionFactory
{
    private final String _protocol;
    private String _host;
    private int _port;
    private String _username;
    private String _password;
    private String _clientId;
    private String _remoteHost;
    private boolean _ssl;

    private String _queuePrefix;
    private String _topicPrefix;
    private boolean _useBinaryMessageId = Boolean.parseBoolean(System.getProperty("qpid.use_binary_message_id", "true"));
    private Boolean _syncPublish;
    private int _maxSessions = Integer.getInteger("qpid.max_sessions", 0);
    private int _maxPrefetch;
    private String _keyStorePath;
    private String _keyStorePassword;
    private String _keyStoreCertAlias;
    private String _trustStorePath;
    private String _trustStorePassword;
    private SSLContext _sslContext;


    public ConnectionFactoryImpl(final String host,
                                 final int port,
                                 final String username,
                                 final String password)
    {
        this(host,port,username,password,null,false);
    }

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
        this(host,port,username,password,clientId,null,ssl);
    }

    public ConnectionFactoryImpl(final String host,
                                 final int port,
                                 final String username,
                                 final String password,
                                 final String clientId,
                                 final String remoteHost,
                                 final boolean ssl)
    {
        this(host, port, username, password, clientId, remoteHost, ssl,0);
    }

    public ConnectionFactoryImpl(final String host,
                                 final int port,
                                 final String username,
                                 final String password,
                                 final String clientId,
                                 final String remoteHost,
                                 final boolean ssl,
                                 final int maxSessions)
    {
        this(ssl?"amqps":"amqp",host,port,username,password,clientId,remoteHost,ssl,maxSessions);
    }

    public ConnectionFactoryImpl(final String protocol,
                                 final String host,
                                 final int port,
                                 final String username,
                                 final String password,
                                 final String clientId,
                                 final String remoteHost,
                                 final boolean ssl,
                                 final int maxSessions)
    {
        _protocol = protocol;
        _host = host;
        _port = port;
        _username = username;
        _password = password;
        _clientId = clientId;
        _remoteHost = remoteHost;
        _ssl = ssl;
        _maxSessions = maxSessions;
        if(System.getProperties().containsKey("qpid.sync_publish"))
        {
            _syncPublish = Boolean.getBoolean("qpid.sync_publish");
        }
    }

    public ConnectionImpl createConnection() throws JMSException
    {
        return createConnection(_username, _password);
    }

    public ConnectionImpl createConnection(String username, final String password) throws JMSException
    {
        synchronized (this)
        {
            if(_ssl && _sslContext == null)
            {
                try
                {
                    _sslContext = SSLUtil.buildSslContext(_keyStoreCertAlias,_keyStorePath,
                                                          KeyStore.getDefaultType(),
                                                          _keyStorePassword,
                                                          KeyManagerFactory.getDefaultAlgorithm(),
                                                          _trustStorePath,_trustStorePassword,
                                                          KeyStore.getDefaultType(),
                                                          TrustManagerFactory.getDefaultAlgorithm());
                    if(username == null && _keyStoreCertAlias != null)
                    {
                        X509Certificate[] certs = SSLUtil.getClientCertificates(_keyStoreCertAlias,
                                                                                _keyStorePath,
                                                                                _keyStorePassword,
                                                                                KeyStore.getDefaultType(),
                                                                                KeyManagerFactory.getDefaultAlgorithm());
                        if(certs != null && certs.length != 0)
                        {
                            username = certs[0].getSubjectDN().getName();
                        }
                    }

                }
                catch (GeneralSecurityException e)
                {
                    final JMSException jmsException = new JMSException("Unable to create SSL context");
                    jmsException.setLinkedException(e);
                    jmsException.initCause(e);
                    throw jmsException;
                }
                catch (IOException e)
                {
                    final JMSException jmsException = new JMSException("Unable to create SSL context");
                    jmsException.setLinkedException(e);
                    jmsException.initCause(e);
                    throw jmsException;                }
            }
        }
        ConnectionImpl connection = new ConnectionImpl(_protocol,_host, _port, username, password, _clientId, _remoteHost, _sslContext, _maxSessions);
        connection.setQueuePrefix(_queuePrefix);
        connection.setTopicPrefix(_topicPrefix);
        connection.setUseBinaryMessageId(_useBinaryMessageId);
        connection.setSyncPublish(_syncPublish);
        if(_maxPrefetch != 0)
        {
            connection.setMaxPrefetch(_maxPrefetch);
        }
        return connection;
    }

    public void setMaxPrefetch(final int maxPrefetch)
    {
        _maxPrefetch = maxPrefetch;
    }

    public void setKeyStorePath(final String keyStorePath)
    {
        _keyStorePath = keyStorePath;
    }

    public void setKeyStorePassword(final String keyStorePassword)
    {
        _keyStorePassword = keyStorePassword;
    }

    public void setKeyStoreCertAlias(final String keyStoreCertAlias)
    {
        _keyStoreCertAlias = keyStoreCertAlias;
    }

    public void setTrustStorePath(final String trustStorePath)
    {
        _trustStorePath = trustStorePath;
    }

    public void setTrustStorePassword(final String trustStorePassword)
    {
        _trustStorePassword = trustStorePassword;
    }

    private static class ConnectionOptions
    {
        String username;
        String password;
        String clientId;
        String remoteHost;

        boolean binaryMessageId = true;
        Boolean syncPublish = Boolean.getBoolean("qpid.sync_publish");
        int maxSessions;
        public boolean ssl;
        public int maxPrefetch;
        public String trustStorePath;
        public String trustStorePassword;
        public String keyStorePath;
        public String keyStorePassword;
        public String keyStoreCertAlias;
    }



    private static abstract class OptionSetter
    {

        private static final Map<String, OptionSetter> OPTION_SETTER_MAP = new HashMap<String, OptionSetter>();
        private final String _name;
        private final String _description;

        public OptionSetter(String name, String description)
        {
            OPTION_SETTER_MAP.put(name.toLowerCase(), this);
            _name = name;
            _description = description;
        }

        public abstract void setOption(ConnectionOptions options, String value) throws MalformedURLException;

        public static void parseOptions(URL url, ConnectionOptions options) throws MalformedURLException
        {
            String query = url.getQuery();
            if(query != null)
            {
                for(String param : query.split("&"))
                {

                    String[] keyValuePair = param.split("=",2);
                    OptionSetter setter = OPTION_SETTER_MAP.get(keyValuePair[0]);
                    if(setter != null)
                    {
                        setter.setOption(options, keyValuePair[1]);
                    }
                    else
                    {
                        throw new MalformedURLException("Unknown URL option: '"+keyValuePair[0]+"' in connection URL");
                    }

                }
            }
        }
    }

    private static final OptionSetter[] _options =
        {
            new OptionSetter("clientid", "JMS client id / AMQP container id")
            {
                public void setOption(ConnectionOptions options, String value)
                {
                    options.clientId = value;
                }
            },
            new OptionSetter("ssl", "Set to \"true\" to use SSL encryption")
            {
                public void setOption(ConnectionOptions options, String value)
                {
                    options.ssl = Boolean.valueOf(value);
                }
            },
            new OptionSetter("remote-host", "AMQP remote host")
            {
                public void setOption(ConnectionOptions options, String value)
                {
                    options.remoteHost = value;
                }
            },
            new OptionSetter("binary-messageid", "Use binary (rather than String) message ids")
            {
                public void setOption(ConnectionOptions options, String value)
                {
                    options.binaryMessageId = Boolean.parseBoolean(value);
                }
            },
            new OptionSetter("sync-publish", "Wait for acknowledge when sending messages")
            {
                public void setOption(ConnectionOptions options, String value)
                {
                    options.syncPublish = Boolean.parseBoolean(value);
                }
            },
            new OptionSetter("max-sessions", "set maximum number of sessions allowed")
            {
                public void setOption(ConnectionOptions options, String value)
                {
                    options.maxSessions = Integer.parseInt(value);
                }
            },
            new OptionSetter("max-prefetch", "set maximum number of messages prefetched on a link")
            {
                public void setOption(ConnectionOptions options, String value)
                {
                    options.maxPrefetch = Integer.parseInt(value);
                }
            },
            new OptionSetter("trust-store","")
            {
                public void setOption(final ConnectionOptions options, final String value) throws MalformedURLException
                {
                    options.trustStorePath = value;
                }
            },
            new OptionSetter("trust-store-password","")
            {
                public void setOption(final ConnectionOptions options, final String value) throws MalformedURLException
                {
                    options.trustStorePassword = value;
                }
            },
            new OptionSetter("key-store","")
            {
                public void setOption(final ConnectionOptions options, final String value) throws MalformedURLException
                {
                    options.keyStorePath = value;
                }
            },
            new OptionSetter("key-store-password","")
            {
                public void setOption(final ConnectionOptions options, final String value) throws MalformedURLException
                {
                    options.keyStorePassword = value;
                }
            },
            new OptionSetter("ssl-cert-alias","")
            {
                public void setOption(final ConnectionOptions options, final String value) throws MalformedURLException
                {
                    options.keyStoreCertAlias = value;
                }
            }
        };

    public static ConnectionFactoryImpl createFromURL(final String urlString) throws MalformedURLException
    {
        URL url = new URL(null, urlString, new URLStreamHandler()
        {
            @Override
            protected URLConnection openConnection(URL u) throws IOException
            {
                throw new UnsupportedOperationException();
            }
        });
        String protocol = url.getProtocol();
        if (protocol == null || "".equals(protocol))
        {
            protocol = "amqp";
        }
        String host = url.getHost();
        int port = url.getPort();

        final ConnectionOptions options = new ConnectionOptions();
        if (port == -1)
        {
            if ("amqps".equals(protocol))
            {
                port = 5671;
                options.ssl = true;
            }
            else if("amqp".equals(protocol))
            {
                port = 5672;
            }
            else if("ws".equals(protocol))
            {
                port = 80;
            }
            else if("wss".equals(protocol))
            {
                port = 443;
            }
        }
        else if ("amqps".equals(protocol) || "wss".equals(protocol))
        {
            options.ssl = true;
        }


        String userInfo = url.getUserInfo();

        if (userInfo != null)
        {
            String[] components = userInfo.split(":", 2);
            options.username = URLDecoder.decode(components[0]);
            if (components.length == 2)
            {
                options.password = URLDecoder.decode(components[1]);
            }
        }

        if(System.getProperties().containsKey("qpid.sync_publish"))
        {
            options.syncPublish = Boolean.getBoolean("qpid.sync_publish");
        }

        OptionSetter.parseOptions(url, options);

        if (options.remoteHost == null)
        {
            options.remoteHost = host;
        }

        ConnectionFactoryImpl connectionFactory =
                new ConnectionFactoryImpl(protocol,
                                          host,
                                          port,
                                          options.username,
                                          options.password,
                                          options.clientId,
                                          options.remoteHost,
                                          options.ssl,
                                          options.maxSessions);
        connectionFactory.setUseBinaryMessageId(options.binaryMessageId);
        connectionFactory.setSyncPublish(options.syncPublish);
        if (options.maxPrefetch != 0)
        {
            connectionFactory.setMaxPrefetch(options.maxPrefetch);
        }
        if (options.keyStorePath != null)
        {
            connectionFactory.setKeyStorePath(options.keyStorePath);
        }
        if (options.keyStorePassword != null)
        {
            connectionFactory.setKeyStorePassword(options.keyStorePassword);
        }
        if (options.keyStoreCertAlias != null)
        {
            connectionFactory.setKeyStoreCertAlias(options.keyStoreCertAlias);
        }
        if (options.trustStorePath != null)
        {
            connectionFactory.setTrustStorePath(options.trustStorePath);
        }
        if (options.trustStorePassword != null)
        {
            connectionFactory.setTrustStorePassword(options.trustStorePassword);
        }

        return connectionFactory;

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

    public String getTopicPrefix()
    {
        return _topicPrefix;
    }

    public void setTopicPrefix(String topicPrefix)
    {
        _topicPrefix = topicPrefix;
    }

    public String getQueuePrefix()
    {
        return _queuePrefix;
    }

    public void setQueuePrefix(String queuePrefix)
    {
        _queuePrefix = queuePrefix;
    }

    public void setUseBinaryMessageId(boolean useBinaryMessageId)
    {
        _useBinaryMessageId = useBinaryMessageId;
    }

    public void setSyncPublish(Boolean syncPublish)
    {
        _syncPublish = syncPublish;
    }


}
