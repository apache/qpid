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

import java.security.AccessControlException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public interface Port extends ConfiguredObject
{
    String CREATED                              = "created";
    String DURABLE                              = "durable";
    String ID                                   = "id";
    String LIFETIME_POLICY                      = "lifetimePolicy";
    String NAME                                 = "name";
    String STATE                                = "state";
    String TIME_TO_LIVE                         = "timeToLive";
    String UPDATED                              = "updated";
    String BINDING_ADDRESS                      = "bindingAddress";
    String PORT                                 = "port";
    String PROTOCOLS                            = "protocols";
    String TRANSPORTS                           = "transports";

    // Attributes
    public static final Collection<String> AVAILABLE_ATTRIBUTES =
            Collections.unmodifiableList(
                    Arrays.asList(
                            ID,
                            NAME,
                            STATE,
                            DURABLE,
                            LIFETIME_POLICY,
                            TIME_TO_LIVE,
                            CREATED,
                            UPDATED,
                            BINDING_ADDRESS,
                            PORT,
                            PROTOCOLS,
                            TRANSPORTS
                                 ));


    String getBindingAddress();

    int getPort();

    Collection<Transport> getTransports();

    void addTransport(Transport transport) throws IllegalStateException,
                                                  AccessControlException,
                                                  IllegalArgumentException;

    Transport removeTransport(Transport transport) throws IllegalStateException,
                                                          AccessControlException,
                                                          IllegalArgumentException;


    Collection<Protocol> getProtocols();

    void addProtocol(Protocol protocol) throws IllegalStateException,
                                               AccessControlException,
                                               IllegalArgumentException;

    Protocol removeProtocol(Protocol protocol) throws IllegalStateException,
                                                      AccessControlException,
                                                      IllegalArgumentException;


    //children
    Collection<VirtualHostAlias> getVirtualHostBindings();
    Collection<Connection> getConnections();
}
