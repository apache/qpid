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
package org.apache.qpid.configuration;

/**
 * Centralised record of Qpid common properties.
 *
 * @see ClientProperties
 */
public class CommonProperties
{
    /**
     * The timeout used by the IO layer for timeouts such as send timeout in IoSender, and the close timeout for IoSender and IoReceiver
     */
    public static final String IO_NETWORK_TRANSPORT_TIMEOUT_PROP_NAME = "qpid.io_network_transport_timeout";
    public static final int IO_NETWORK_TRANSPORT_TIMEOUT_DEFAULT = 60000;

    public static final String HANDSHAKE_TIMEOUT_PROP_NAME = "qpid.handshake_timeout";
    public static final int HANDSHAKE_TIMEOUT_DEFAULT = 2;


    private CommonProperties()
    {
        //no instances
    }
}
