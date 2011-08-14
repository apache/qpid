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
package org.apache.qpid.server.security.auth.sasl.anonymous;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.SaslServerFactory;

import org.apache.commons.configuration.Configuration;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;
import org.apache.qpid.server.security.auth.sasl.AuthenticationProviderInitialiser;
import org.apache.qpid.server.security.auth.sasl.UsernamePasswordInitialiser;

import java.io.IOException;
import java.util.Map;

public class AnonymousInitialiser implements AuthenticationProviderInitialiser
{
    public String getMechanismName()
    {
        return "ANONYMOUS";
    }

    public void initialise(String baseConfigPath, Configuration configuration, Map<String, PrincipalDatabase> principalDatabases) throws Exception
    {
    }

    public void initialise(PrincipalDatabase db)
    {
    }

    public CallbackHandler getCallbackHandler()
    {
        return new CallbackHandler()
        {

            public Callback[] _callbacks;

            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException
            {
                _callbacks =callbacks;
            }
        };
    }

    public Map<String, ?> getProperties()
    {
        return null;
    }

    public Class<? extends SaslServerFactory> getServerFactoryClassForJCARegistration()
    {
        return AnonymousSaslServerFactory.class;
    }
}