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

package org.apache.qpid.server.logging.subjects;

/**
 *  LogSubjectFormat class contains a list of formatting string 
 *  that can be statically imported where needed.
 *  The formatting strings are to be used via a MessageFormat call
 *  to insert the required values at the corresponding place holder
 *  indices.
 *    
 */

public class LogSubjectFormat
{
    
    /**
     * LOG FORMAT for the Subscription Log Subject
     * 0 - Subscription ID
     */
    public static final String SUBSCRIPTION_FORMAT = "sub:{0}";
    
    /** 
     * LOG FORMAT for Connection Log Subject - SOCKET format 
     * 0 - Connection ID
     * 1 - Remote Address
     */
    public static final String SOCKET_FORMAT = "con:{0}({1})";
    
    /** 
     * LOG FORMAT for Connection Log Subject - USER format
     * 0 - Connection ID
     * 1 - User ID
     * 2 - IP
     */
    public static final String USER_FORMAT = "con:{0}({1}@{2})";
    
    /**
     * LOG FORMAT for the Connection Log Subject - CON format
     * 0 - Connection ID
     * 1 - User ID
     * 2 - IP
     * 3 - Virtualhost
     */
    public static final String CONNECTION_FORMAT = "con:{0}({1}@{2}/{3})";
    
    /**
     * LOG FORMAT for the Channel LogSubject
     * 0 - Connection ID
     * 1 - User ID
     * 2 - IP
     * 3 - Virtualhost
     * 4 - Channel ID
     */
    public static final String CHANNEL_FORMAT = CONNECTION_FORMAT + "/ch:{4}";
    
    /**
     * LOG FORMAT for the Exchange LogSubject,
     * 0 - Virtualhost Name
     * 1 - Exchange Type
     * 2 - Exchange Name
     */
    public static final String EXCHANGE_FORMAT = "vh(/{0})/ex({1}/{2})";
    
    /**
     * LOG FORMAT for a Binding LogSubject
     * 0 - Virtualhost Name
     * 1 - Exchange Type
     * 2 - Exchange Name
     * 3 - Queue Name
     * 4 - Binding RoutingKey
     */
    public static final String BINDING_FORMAT = "vh(/{0})/ex({1}/{2})/qu({3})/rk({4})";
    
    /**
     * LOG FORMAT for the MessagesStore LogSubject
     * 0 - Virtualhost Name
     * 1 - Message Store Type
     */
    public static final String STORE_FORMAT = "vh(/{0})/ms({1})";
    
    /**
     * LOG FORMAT for the Queue LogSubject,
     * 0 - Virtualhost name
     * 1 - queue name
     */
    public static final String QUEUE_FORMAT = "vh(/{0})/qu({1})";
}
