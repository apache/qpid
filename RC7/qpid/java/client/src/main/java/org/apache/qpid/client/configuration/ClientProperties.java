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

package org.apache.qpid.client.configuration;

/**
 * This class centralized the Qpid client properties.
 */
public class ClientProperties
{

    /**
     * Currently with Qpid it is not possible to change the client ID.
     * If one is not specified upon connection construction, an id is generated automatically.
     * Therefore an exception is always thrown unless this property is set to true.
     * type: boolean
     */
    public static final String IGNORE_SET_CLIENTID_PROP_NAME = "ignore_setclientID";

    /**
     * This property is currently used within the 0.10 code path only 
     * The maximum number of pre-fetched messages per destination
     * This property is used for all the connection unless it is overwritten by the connectionURL
     * type: long
     */
    public static final String MAX_PREFETCH_PROP_NAME = "max_prefetch";
    public static final String MAX_PREFETCH_DEFAULT = "5000";

    /**
     * When true a sync command is sent after every persistent messages.
     * type: boolean
     */
    public static final String SYNC_PERSISTENT_PROP_NAME = "sync_persistence";

     /**
     * ==========================================================
     * Those properties are used when the io size should be bounded
     * ==========================================================
     */

    /**
     * When set to true the io layer throttle down producers and consumers
     * when written or read messages are accumulating and exceeding a certain size.
     * This is especially useful when a the producer rate is greater than the network
     * speed.
     * type: boolean
     */
    public static final String PROTECTIO_PROP_NAME = "protectio";

    //=== The following properties are only used when the previous one is true.
    /**
     * Max size of read messages that can be stored within the MINA layer
     * type: int
     */
    public static final String READ_BUFFER_LIMIT_PROP_NAME = "qpid.read.buffer.limit";
    public static final String READ_BUFFER_LIMIT_DEFAULT = "262144";
    /**
     * Max size of written messages that can be stored within the MINA layer
     * type: int
     */
    public static final String WRITE_BUFFER_LIMIT_PROP_NAME = "qpid.read.buffer.limit";
    public static final String WRITE_BUFFER_LIMIT_DEFAULT = "262144";
}
