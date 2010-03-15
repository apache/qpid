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

import java.util.Map;

/**
 * A ConnectionSettings object can only be associated with
 * one Connection object. I have added an assertion that will
 * throw an exception if it is used by more than on Connection
 *
 */
public class ConnectionSettings
{
    String protocol = "tcp";
    String host = "localhost";
    String vhost;
    String username = "guest";
    String password = "guest";
    int port = 5672;
    boolean tcpNodelay;
    int maxChannelCount = 32767;
    int maxFrameSize = 65535;
    int heartbeatInterval;
    
    // SSL props
    boolean useSSL;
    String keyStorePath;
    String keyStorePassword;
    String keyStoreCertType;
    String trustStoreCertType;
    String trustStorePath;
    String trustStorePassword;
    String certAlias;
    boolean verifyHostname;
    
    // SASL props
    String saslMechs = "PLAIN";
    String saslProtocol = "AMQP";
    String saslServerName = "localhost";
    boolean useSASLEncryption;
    
    private Connection owner;
    
    private Map<String, Object> _clientProperties;

    public Connection getConnection()
    {
        return owner;
    }

    public void setConnection(Connection owner)
    {
        if (this.owner != null)
        {
            throw new IllegalStateException(
                    "A ConnectionSettings instance can be associated" +
                    " with one and only one Connection instance");
        }
        this.owner = owner;
    }
    
    public boolean isTcpNodelay()
    {
        return tcpNodelay;
    }

    public void setTcpNodelay(boolean tcpNodelay)
    {
        this.tcpNodelay = tcpNodelay;
    }

    public int getHeartbeatInterval()
    {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(int heartbeatInterval)
    {
        this.heartbeatInterval = heartbeatInterval;
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
    
    public String getKeyStoreCertType()
    {
        return keyStoreCertType;
    }

    public void setKeyStoreCertType(String keyStoreCertType)
    {
        this.keyStoreCertType = keyStoreCertType;
    }

    public String getTrustStoreCertType()
    {
        return trustStoreCertType;
    }

    public void setTrustStoreCertType(String trustStoreCertType)
    {
        this.trustStoreCertType = trustStoreCertType;
    }

}
