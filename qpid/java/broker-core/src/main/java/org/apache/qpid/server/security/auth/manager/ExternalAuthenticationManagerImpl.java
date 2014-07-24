/*
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
 */
package org.apache.qpid.server.security.auth.manager;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.security.auth.sasl.external.ExternalSaslServer;

public class ExternalAuthenticationManagerImpl extends AbstractAuthenticationManager<ExternalAuthenticationManagerImpl>
        implements ExternalAuthenticationManager<ExternalAuthenticationManagerImpl>
{
    private static final String EXTERNAL = "EXTERNAL";

    @ManagedAttributeField
    private boolean _useFullDN;

    @ManagedObjectFactoryConstructor
    protected ExternalAuthenticationManagerImpl(final Map<String, Object> attributes, final Broker broker)
    {
        super(attributes, broker);
    }

    @Override
    public boolean getUseFullDN()
    {
        return _useFullDN;
    }

    @Override
    public List<String> getMechanisms()
    {
        return Collections.singletonList(EXTERNAL);
    }

    @Override
    public SaslServer createSaslServer(String mechanism, String localFQDN, Principal externalPrincipal) throws SaslException
    {
        if(EXTERNAL.equals(mechanism))
        {
            return new ExternalSaslServer(externalPrincipal, _useFullDN);
        }
        else
        {
            throw new SaslException("Unknown mechanism: " + mechanism);
        }
    }

    @Override
    public AuthenticationResult authenticate(SaslServer server, byte[] response)
    {
        // Process response from the client
        try
        {
            server.evaluateResponse(response != null ? response : new byte[0]);

            Principal principal = ((ExternalSaslServer)server).getAuthenticatedPrincipal();

            if(principal != null)
            {
                return new AuthenticationResult(principal);
            }
            else
            {
                return new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR);
            }
        }
        catch (SaslException e)
        {
            return new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR,e);
        }

    }

    @Override
    public AuthenticationResult authenticate(String username, String password)
    {
        return new AuthenticationResult(new UsernamePrincipal(username));
    }

}
