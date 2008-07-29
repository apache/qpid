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
package org.apache.qpid;

import java.util.Map;

/**
 * This interface represents a broker and provides the basic information
 * required for opening a connection with a broker.
 */
public interface BrokerDetails
{
    /**
     * Those are the supported protocols
     */
    public static final String PROTOCOL_TCP = "tcp";
    public static final String PROTOCOL_TLS = "tls";
    
    public static final String VIRTUAL_HOST = "virtualhost";
    public static final String CLIENT_ID = "client_id";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";

    /**
     * Get the broker host name.
     *
     * @return The broker host name.
     */
    public String getHost();

    /**
     * Get the broker port number.
     *
     * @return The broker port number.
     */
    public int getPort();

    /**
     * Get the virtual host to connect to.
     *
     * @return The virtual host of this broker.
     */
    public String getVirtualHost();

    /**
     * Get the user name.
     *
     * @return The user name
     */
    public String getUserName();

    /**
     * Get the user password
     *
     * @return The user password
     */
    public String getPassword();

    /**
     * Get the protocol used to connect to hise broker.
     *
     * @return the protocol used to connect to the broker.
     */
    public String getProtocol();

    /**
     * Set the broker host name.
     *
     * @param host The broker host name.
     */
    public void setHost(String host);

    /**
     * Set the broker port number.
     *
     * @param port The broker port number.
     */
    public void setPort(int port);

    /**
     * Set the virtual host to connect to.
     *
     * @param virtualHost The virtual host of this broker.
     */
    public void setVirtualHost(String virtualHost);

    /**
     * Set the user name.
     *
     * @param userName The user name
     */
    public void setUserName(String userName);

    /**
     * Set the user password
     *
     * @param password The user password
     */
    public void setPassword(String password);

    /**
     * Set the protocol used to connect to hise broker.
     *
     * @param protocol the protocol used to connect to the broker.
     */
    public void setProtocol(String protocol);
    
    /**
     * Ex: keystore path
     * 
     * @return the Properties associated with this connection.
     */
    public Map<String,String> getProperties();
    
    /**
     * Sets the properties associated with this connection
     * 
     * @param props the new p[roperties. 
     */
    public void setProperties(Map<String,String> props);
    
    public void setProperty(String key,String value);
}
