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

public class ConnectionSettings
{
    String protocol = "tcp";
    String host = "localhost";
    String vhost;
    String username = "guest";
    String password = "guest";
    String saslMechs = "PLAIN";
    String saslProtocol = "AMQP";
    String saslServerName = "localhost";
    int port = 5672;
    int maxChannelCount = 32767;
    int maxFrameSize = 65535;
    int heartbeatInterval;
    boolean useSSL;
    boolean useSASLEncryption;
    boolean tcpNodelay;
    private Map<String, Object> _clientProperties;

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
}
