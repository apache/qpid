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

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ManagedAttribute;
import org.apache.qpid.server.model.Port;

import java.util.Map;
import java.util.UUID;

abstract public class PortWithAuthProvider<X extends PortWithAuthProvider<X>> extends AbstractPort<X>
{
    private AuthenticationProvider _authenticationProvider;

    public PortWithAuthProvider(final UUID id,
                                final Broker<?> broker,
                                final Map<String, Object> attributes,
                                final Map<String, Object> defaults,
                                final TaskExecutor taskExecutor)
    {
        super(id, broker, attributes, defaults, taskExecutor);
        String authProvider = (String)getAttribute(Port.AUTHENTICATION_PROVIDER);
        if (authProvider == null)
        {
            throw new IllegalConfigurationException("An authentication provider must be specified for port : " + getName());
        }
        _authenticationProvider = broker.findAuthenticationProviderByName(authProvider);

        if(_authenticationProvider == null)
        {
            throw new IllegalConfigurationException("The authentication provider '" + authProvider + "' could not be found for port : " + getName());
        }
    }


    @ManagedAttribute
    public AuthenticationProvider getAuthenticationProvider()
    {
        return _authenticationProvider;
    }
}
