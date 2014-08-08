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
package org.apache.qpid.transport;

import static org.apache.qpid.configuration.ClientProperties.AMQJ_HEARTBEAT_DELAY;
import static org.apache.qpid.configuration.ClientProperties.AMQJ_HEARTBEAT_TIMEOUT_FACTOR;
import static org.apache.qpid.configuration.ClientProperties.IDLE_TIMEOUT_PROP_NAME;
import static org.apache.qpid.configuration.ClientProperties.QPID_HEARTBEAT_INTERVAL;
import static org.apache.qpid.configuration.ClientProperties.QPID_HEARTBEAT_INTERVAL_010_DEFAULT;
import static org.apache.qpid.configuration.ClientProperties.QPID_HEARTBEAT_TIMEOUT_FACTOR;
import static org.apache.qpid.configuration.ClientProperties.QPID_HEARTBEAT_TIMEOUT_FACTOR_DEFAULT;
import static org.apache.qpid.configuration.ClientProperties.AMQJ_TCP_NODELAY_PROP_NAME;
import static org.apache.qpid.configuration.ClientProperties.QPID_SSL_KEY_MANAGER_FACTORY_ALGORITHM_PROP_NAME;
import static org.apache.qpid.configuration.ClientProperties.QPID_SSL_KEY_STORE_CERT_TYPE_PROP_NAME;
import static org.apache.qpid.configuration.ClientProperties.QPID_SSL_TRUST_MANAGER_FACTORY_ALGORITHM_PROP_NAME;
import static org.apache.qpid.configuration.ClientProperties.QPID_SSL_TRUST_STORE_CERT_TYPE_PROP_NAME;
import static org.apache.qpid.configuration.ClientProperties.QPID_TCP_NODELAY_PROP_NAME;
import static org.apache.qpid.configuration.ClientProperties.RECEIVE_BUFFER_SIZE_PROP_NAME;
import static org.apache.qpid.configuration.ClientProperties.SEND_BUFFER_SIZE_PROP_NAME;
import static org.apache.qpid.configuration.ClientProperties.LEGACY_RECEIVE_BUFFER_SIZE_PROP_NAME;
import static org.apache.qpid.configuration.ClientProperties.LEGACY_SEND_BUFFER_SIZE_PROP_NAME;

import java.security.KeyStore;
import java.util.Map;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.apache.qpid.configuration.QpidProperty;


/**
 * A ConnectionSettings object can only be associated with
 * one Connection object. I have added an assertion that will
 * throw an exception if it is used by more than on Connection
 *
 */
public class ConnectionSettings
{
    public static final String WILDCARD_ADDRESS = "*";


    private String protocol = "tcp";
    private String host = "localhost";
    private String vhost;
    private String username = "guest";
    private String password = "guest";
    private int port = 5672;
    private boolean tcpNodelay = QpidProperty.booleanProperty(Boolean.TRUE, QPID_TCP_NODELAY_PROP_NAME, AMQJ_TCP_NODELAY_PROP_NAME).get();
    private int maxChannelCount = 32767;
    private int maxFrameSize = 65535;
    private Integer hearbeatIntervalLegacyMs = QpidProperty.intProperty(null, IDLE_TIMEOUT_PROP_NAME).get();
    private Integer heartbeatInterval = QpidProperty.intProperty(null, QPID_HEARTBEAT_INTERVAL, AMQJ_HEARTBEAT_DELAY).get();
    private float heartbeatTimeoutFactor = QpidProperty.floatProperty(QPID_HEARTBEAT_TIMEOUT_FACTOR_DEFAULT, QPID_HEARTBEAT_TIMEOUT_FACTOR, AMQJ_HEARTBEAT_TIMEOUT_FACTOR).get();
    private int connectTimeout = 30000;
    private int readBufferSize = QpidProperty.intProperty(65535, RECEIVE_BUFFER_SIZE_PROP_NAME, LEGACY_RECEIVE_BUFFER_SIZE_PROP_NAME).get();
    private int writeBufferSize = QpidProperty.intProperty(65535, SEND_BUFFER_SIZE_PROP_NAME, LEGACY_SEND_BUFFER_SIZE_PROP_NAME).get();;

    // SSL props
    private boolean useSSL;
    private String keyStorePath = System.getProperty("javax.net.ssl.keyStore");
    private String keyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword");
    private String keyStoreType = System.getProperty("javax.net.ssl.keyStoreType",KeyStore.getDefaultType());
    private String keyManagerFactoryAlgorithm = QpidProperty.stringProperty(KeyManagerFactory.getDefaultAlgorithm(), QPID_SSL_KEY_MANAGER_FACTORY_ALGORITHM_PROP_NAME, QPID_SSL_KEY_STORE_CERT_TYPE_PROP_NAME).get();
    private String trustManagerFactoryAlgorithm = QpidProperty.stringProperty(TrustManagerFactory.getDefaultAlgorithm(), QPID_SSL_TRUST_MANAGER_FACTORY_ALGORITHM_PROP_NAME, QPID_SSL_TRUST_STORE_CERT_TYPE_PROP_NAME).get();
    private String trustStorePath = System.getProperty("javax.net.ssl.trustStore");
    private String trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
    private String trustStoreType = System.getProperty("javax.net.ssl.trustStoreType",KeyStore.getDefaultType());
    private String certAlias;
    private boolean verifyHostname;
    
    // SASL props
    private String saslMechs = System.getProperty("qpid.sasl_mechs", null);
    private String saslProtocol = System.getProperty("qpid.sasl_protocol", "AMQP");
    private String saslServerName = System.getProperty("qpid.sasl_server_name", "localhost");
    private boolean useSASLEncryption;
   
    private Map<String, Object> _clientProperties;
    
    public boolean isTcpNodelay()
    {
        return tcpNodelay;
    }

    public void setTcpNodelay(boolean tcpNodelay)
    {
        this.tcpNodelay = tcpNodelay;
    }

    /**
     * Gets the heartbeat interval (seconds) for 0-8/9/9-1 protocols.
     * 0 means heartbeating is disabled.
     * null means use the broker-supplied value.
     * @return the heartbeat interval
     */
    public Integer getHeartbeatInterval08()
    {
        if (heartbeatInterval != null)
        {
            return heartbeatInterval;
        }
        else if (hearbeatIntervalLegacyMs != null)
        {
            return hearbeatIntervalLegacyMs / 1000;
        }
        else
        {
            return null;
        }
    }

    /**
     * Gets the heartbeat interval (seconds) for the 0-10 protocol.
     * 0 means heartbeating is disabled.
     * @return the heartbeat interval
     */
    public int getHeartbeatInterval010()
    {
        if (heartbeatInterval != null)
        {
            return heartbeatInterval;
        }
        else if (hearbeatIntervalLegacyMs != null)
        {
            return hearbeatIntervalLegacyMs / 1000;
        }
        else
        {
            return QPID_HEARTBEAT_INTERVAL_010_DEFAULT;
        }
    }

    public void setHeartbeatInterval(int heartbeatInterval)
    {
        this.heartbeatInterval = heartbeatInterval;
    }

    public float getHeartbeatTimeoutFactor()
    {
        return this.heartbeatTimeoutFactor;
    }

    public String getProtocol()
    {
        return protocol;
    }

    public void setProtocol(String protocol)
    {
        this.protocol = protocol;
    }

    public String getHost()
    {
        return host;
    }

    public void setHost(String host)
    {
        this.host = host;
    }

    public int getPort()
    {
        return port;
    }

    public void setPort(int port)
    {
        this.port = port;
    }

    public String getVhost()
    {
        return vhost;
    }

    public void setVhost(String vhost)
    {
        this.vhost = vhost;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public boolean isUseSSL()
    {
        return useSSL;
    }

    public void setUseSSL(boolean useSSL)
    {
        this.useSSL = useSSL;
    }

    public boolean isUseSASLEncryption()
    {
        return useSASLEncryption;
    }

    public void setUseSASLEncryption(boolean useSASLEncryption)
    {
        this.useSASLEncryption = useSASLEncryption;
    }

    public String getSaslMechs()
    {
        return saslMechs;
    }

    public void setSaslMechs(String saslMechs)
    {
        this.saslMechs = saslMechs;
    }

    public String getSaslProtocol()
    {
        return saslProtocol;
    }

    public void setSaslProtocol(String saslProtocol)
    {
        this.saslProtocol = saslProtocol;
    }

    public String getSaslServerName()
    {
        return saslServerName;
    }

    public void setSaslServerName(String saslServerName)
    {
        this.saslServerName = saslServerName;
    }

    public int getMaxChannelCount()
    {
        return maxChannelCount;
    }

    public void setMaxChannelCount(int maxChannelCount)
    {
        this.maxChannelCount = maxChannelCount;
    }

    public int getMaxFrameSize()
    {
        return maxFrameSize;
    }

    public void setMaxFrameSize(int maxFrameSize)
    {
        this.maxFrameSize = maxFrameSize;
    }

    public void setClientProperties(final Map<String, Object> clientProperties)
    {
        _clientProperties = clientProperties;
    }

    public Map<String, Object> getClientProperties()
    {
        return _clientProperties;
    }
    
    public String getKeyStorePath()
    {
        return keyStorePath;
    }

    public void setKeyStorePath(String keyStorePath)
    {
        this.keyStorePath = keyStorePath;
    }

    public String getKeyStorePassword()
    {
        return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword)
    {
        this.keyStorePassword = keyStorePassword;
    }

    public void setKeyStoreType(String keyStoreType)
    {
        this.keyStoreType = keyStoreType;
    }

    public String getKeyStoreType()
    {
        return keyStoreType;
    }

    public String getTrustStorePath()
    {
        return trustStorePath;
    }

    public void setTrustStorePath(String trustStorePath)
    {
        this.trustStorePath = trustStorePath;
    }

    public String getTrustStorePassword()
    {
        return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword)
    {
        this.trustStorePassword = trustStorePassword;
    }

    public String getCertAlias()
    {
        return certAlias;
    }

    public void setCertAlias(String certAlias)
    {
        this.certAlias = certAlias;
    }

    public boolean isVerifyHostname()
    {
        return verifyHostname;
    }

    public void setVerifyHostname(boolean verifyHostname)
    {
        this.verifyHostname = verifyHostname;
    }
    
    public String getKeyManagerFactoryAlgorithm()
    {
        return keyManagerFactoryAlgorithm;
    }

    public void setKeyManagerFactoryAlgorithm(String keyManagerFactoryAlgorithm)
    {
        this.keyManagerFactoryAlgorithm = keyManagerFactoryAlgorithm;
    }

    public String getTrustManagerFactoryAlgorithm()
    {
        return trustManagerFactoryAlgorithm;
    }

    public void setTrustManagerFactoryAlgorithm(String trustManagerFactoryAlgorithm)
    {
        this.trustManagerFactoryAlgorithm = trustManagerFactoryAlgorithm;
    }

    public String getTrustStoreType()
    {
        return trustStoreType;
    }

    public void setTrustStoreType(String trustStoreType)
    {
        this.trustStoreType = trustStoreType;
    }

    public int getConnectTimeout()
    {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout)
    {
        this.connectTimeout = connectTimeout;
    }

    public int getReadBufferSize()
    {
        return readBufferSize;
    }

    public void setReadBufferSize(int readBufferSize)
    {
        this.readBufferSize = readBufferSize;
    }

    public int getWriteBufferSize()
    {
        return writeBufferSize;
    }

    public void setWriteBufferSize(int writeBufferSize)
    {
        this.writeBufferSize = writeBufferSize;
    }

}
