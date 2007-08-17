/* Licensed to the Apache Software Foundation (ASF) under one
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
 */
package org.apache.qpidity;

/**
 * Implements the interface BrokerDetails
 */
public class BrokerDetailsImpl implements BrokerDetails
{
    //--- Those are the default values
    private final String DEFAULT_USERNAME = "guest";
    private final String DEFAULT_PASSWORD = "guest";
    private final String DEFAULT_VIRTUALHOST = "";

    //---- The brker details
    private String _host;
    private int _port;
    private String _virtualHost;
    private String _userName;
    private String _password;
    private String _protocol;
    //--- Constructors

    /**
     * Create a new broker details given all the reuqired information
     *
     * @param protocol    The protocol used for this broker connection
     * @param host        The host name.
     * @param port        The port number.
     * @param virtualHost The virtual host.
     * @param userName    The user name.
     * @param password    The user password.
     */
    public BrokerDetailsImpl(String protocol, String host, int port, String virtualHost, String userName,
                             String password)
    {
        _protocol = protocol;
        _host = host;
        _port = port;
        _virtualHost = virtualHost;
        _userName = userName;
        _password = password;
    }

    /**
     * Create a new broker details given the host name and the procol type,
     * default values are used for the other details.
     *
     * @param protocol The protocol used for this broker connection
     * @param host The host name.
     */
    public BrokerDetailsImpl(String protocol, String host)
    {
        _protocol = protocol;
        _host = host;
        if (protocol.equals(BrokerDetails.PROTOCOL_TCP))
        {
            _port = 1234;
        }
        else if (protocol.equals(BrokerDetails.PROTOCOL_TLS))
        {
            _port = 5555;
        }
        _virtualHost = DEFAULT_VIRTUALHOST;
        _userName = DEFAULT_USERNAME;
        _password = DEFAULT_PASSWORD;
    }

    //--- API BrokerDetails
    /**
     * Get the user password
     *
     * @return The user password
     */
    public String getPassword()
    {
        return _password;
    }

    /**
     * Get the broker host name.
     *
     * @return The broker host name.
     */
    public String getHost()
    {
        return _host;
    }

    /**
     * Get the broker port number.
     *
     * @return The broker port number.
     */
    public int getPort()
    {
        return _port;
    }

    /**
     * Get the virtual host to connect to.
     *
     * @return The virtual host of this broker.
     */
    public String getVirtualHost()
    {
        return _virtualHost;
    }

    /**
     * Get the user name.
     *
     * @return The user name
     */
    public String getUserName()
    {
        return _userName;
    }

    /**
     * Get the protocol used to connect to hise broker.
     *
     * @return the protocol used to connect to the broker.
     */
    public String getProtocol()
    {
        return _protocol;
    }

    /**
     * Set the broker host name.
     *
     * @param host The broker host name.
     */
    public void setHost(String host)
    {
        _host = host;
    }

    /**
     * Set the broker port number.
     *
     * @param port The broker port number.
     */
    public void setPort(int port)
    {
        _port = port;
    }

    /**
     * Set the virtual host to connect to.
     *
     * @param virtualHost The virtual host of this broker.
     */
    public void setVirtualHost(String virtualHost)
    {
        _virtualHost = virtualHost;
    }

    /**
     * Set the user name.
     *
     * @param userName The user name
     */
    public void getUserName(String userName)
    {
        _userName = userName;
    }

    /**
     * Set the user password
     *
     * @param password The user password
     */
    public void setPassword(String password)
    {
        _password = password;
    }

    /**
     * Set the protocol used to connect to hise broker.
     *
     * @param protocol the protocol used to connect to the broker.
     */
    public void setProtocol(String protocol)
    {
        _protocol = protocol;
    }
}
