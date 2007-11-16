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
package org.apache.qpid.jms;

import org.apache.qpid.client.SSLConfiguration;

public interface BrokerDetails
{

    /*
     * Known URL Options
     * @see ConnectionURL
    */
    public static final String OPTIONS_RETRY = "retries";
    public static final String OPTIONS_CONNECT_TIMEOUT = "connecttimeout";
    public static final int DEFAULT_PORT = 5672;

    public static final String TCP = "tcp";
    public static final String VM = "vm";

    public static final String DEFAULT_TRANSPORT = TCP;

    public static final String URL_FORMAT_EXAMPLE =
            "<transport>://<hostname>[:<port Default=\"" + DEFAULT_PORT + "\">][?<option>='<value>'[,<option>='<value>']]";

    public static final long DEFAULT_CONNECT_TIMEOUT = 30000L;
    public static final boolean USE_SSL_DEFAULT = false;

    String getHost();

    void setHost(String host);

    int getPort();

    void setPort(int port);

    String getTransport();

    void setTransport(String transport);

    String getOption(String key);

    void setOption(String key, String value);

    long getTimeout();

    void setTimeout(long timeout);
    
    SSLConfiguration getSSLConfiguration();
    
    void setSSLConfiguration(SSLConfiguration sslConfiguration);

    String toString();

    boolean equals(Object o);
}
