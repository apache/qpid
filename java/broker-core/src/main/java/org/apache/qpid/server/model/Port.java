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
import java.util.Set;

import com.google.common.util.concurrent.ListenableFuture;

@ManagedObject
public interface Port<X extends Port<X>> extends ConfiguredObject<X>
{
    String BINDING_ADDRESS                      = "bindingAddress";
    String PORT                                 = "port";
    String PROTOCOLS                            = "protocols";
    String TRANSPORTS                           = "transports";
    String TCP_NO_DELAY                         = "tcpNoDelay";
    String NEED_CLIENT_AUTH                     = "needClientAuth";
    String WANT_CLIENT_AUTH                     = "wantClientAuth";
    String AUTHENTICATION_PROVIDER              = "authenticationProvider";
    String KEY_STORE                            = "keyStore";
    String TRUST_STORES                         = "trustStores";


    String CONNECTION_MAXIMUM_AUTHENTICATION_DELAY = "connection.maximumAuthenticationDelay";

    @ManagedContextDefault(name = CONNECTION_MAXIMUM_AUTHENTICATION_DELAY)
    long DEFAULT_MAX_CONNECTION_AUTHENTICATION_DELAY = 10000l;

    // Attributes

    @ManagedAttribute( mandatory = true )
    int getPort();

    @ManagedAttribute
    Set<Protocol> getProtocols();

    @ManagedAttribute( defaultValue = "TCP" )
    Set<Transport> getTransports();

    @ManagedAttribute
    KeyStore getKeyStore();

    @ManagedAttribute
    Collection<TrustStore> getTrustStores();

    @ManagedContextDefault(name = "qpid.port.enabledCipherSuites" )
    String DEFAULT_ENABLED_CIPHER_SUITES="[]";

    @ManagedAttribute( defaultValue = "${qpid.port.enabledCipherSuites}")
    Collection<String> getEnabledCipherSuites();

    @ManagedContextDefault(name = "qpid.port.disabledCipherSuites" )
    String DEFAULT_DISABLED_CIPHER_SUITES="[]";

    @ManagedAttribute( defaultValue = "${qpid.port.disabledCipherSuites}")
    Collection<String> getDisabledCipherSuites();

    Collection<Connection> getConnections();

    void start();

    ListenableFuture<Void> startAsync();

}
