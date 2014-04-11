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
package org.apache.qpid.server.model.port;

import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ManagedAttribute;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.Transport;

abstract public class PortWithAuthProvider<X extends PortWithAuthProvider<X>> extends AbstractPort<X>
{
    public static final String DEFAULT_AMQP_NEED_CLIENT_AUTH = "false";
    public static final String DEFAULT_AMQP_WANT_CLIENT_AUTH = "false";

    @ManagedAttributeField
    private AuthenticationProvider _authenticationProvider;

    @ManagedAttributeField
    private boolean _needClientAuth;

    @ManagedAttributeField
    private boolean _wantClientAuth;

    public PortWithAuthProvider(final UUID id,
                                final Broker<?> broker,
                                final Map<String, Object> attributes,
                                final Map<String, Object> defaults,
                                final TaskExecutor taskExecutor)
    {
        super(id, broker, attributes, taskExecutor);
    }

    @ManagedAttribute( automate = true, defaultValue = DEFAULT_AMQP_NEED_CLIENT_AUTH )
    public boolean getNeedClientAuth()
    {
        return _needClientAuth;
    }

    @ManagedAttribute( automate = true, defaultValue = DEFAULT_AMQP_WANT_CLIENT_AUTH )
    public boolean getWantClientAuth()
    {
        return _wantClientAuth;
    }

    @ManagedAttribute( automate = true, mandatory = true )
    public AuthenticationProvider getAuthenticationProvider()
    {
        Broker<?> broker = getParent(Broker.class);
        if(broker.isManagementMode())
        {
            return broker.getManagementModeAuthenticationProvider();
        }
        return _authenticationProvider;
    }

    @Override
    public void validate()
    {
        super.validate();
        boolean useClientAuth = getNeedClientAuth() || getWantClientAuth();

        if(useClientAuth && (getTrustStores() == null || getTrustStores().isEmpty()))
        {
            throw new IllegalConfigurationException("Can't create port which requests SSL client certificates but has no trust stores configured.");
        }

        boolean useTLSTransport = getTransports().contains(Transport.SSL) || getTransports().contains(Transport.WSS);
        if(useClientAuth && !useTLSTransport)
        {
            throw new IllegalConfigurationException(
                    "Can't create port which requests SSL client certificates but doesn't use SSL transport.");
        }

    }
}
