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
package org.apache.qpid.server.model;

import java.util.Collection;

@ManagedObject( creatable = false )
public interface Connection<X extends Connection<X>> extends ConfiguredObject<X>
{

    // Attributes

    public static final String STATE = "state";
    public static final String DURABLE = "durable";
    public static final String LIFETIME_POLICY = "lifetimePolicy";

    public static final String CLIENT_ID = "clientId";
    public static final String CLIENT_VERSION = "clientVersion";
    public static final String INCOMING = "incoming";
    public static final String LOCAL_ADDRESS = "localAddress";
    public static final String PRINCIPAL = "principal";
    public static final String PROPERTIES = "properties";
    public static final String REMOTE_ADDRESS = "remoteAddress";
    public static final String REMOTE_PROCESS_NAME = "remoteProcessName";
    public static final String REMOTE_PROCESS_PID = "remoteProcessPid";
    public static final String SESSION_COUNT_LIMIT = "sessionCountLimit";
    public static final String TRANSPORT = "transport";
    /** Name of port associated with the connection */
    public static final String PORT = "port";

    @ManagedAttribute( automate = true )
    String getClientId();

    @ManagedAttribute( automate = true )
    String getClientVersion();

    @ManagedAttribute( automate = true )
    boolean isIncoming();

    @ManagedAttribute( automate = true )
    String getLocalAddress();

    @ManagedAttribute
    String getPrincipal();

    @ManagedAttribute( automate = true )
    String getRemoteAddress();

    @ManagedAttribute( automate = true )
    String getRemoteProcessName();

    @ManagedAttribute( automate = true )
    String getRemoteProcessPid();

    @ManagedAttribute
    long getSessionCountLimit();

    @ManagedAttribute
    Transport getTransport();

    @ManagedAttribute
    Port getPort();

    @ManagedStatistic
    long getBytesIn();

    @ManagedStatistic
    long getBytesOut();

    @ManagedStatistic
    long getMessagesIn();

    @ManagedStatistic
    long getMessagesOut();

    @ManagedStatistic
    long getLastIoTime();

    @ManagedStatistic
    int getSessionCount();

    //children
    Collection<Session> getSessions();

    void delete();


}
