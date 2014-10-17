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
package org.apache.qpid.server.security.auth.manager;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.security.auth.sasl.crammd5.CRAMMD5Initialiser;
import org.apache.qpid.server.security.auth.sasl.plain.PlainAdapterSaslServer;
import org.apache.qpid.server.security.auth.sasl.plain.PlainSaslServer;

@ManagedObject( category = false, type = "Plain" )
public class PlainAuthenticationProvider
        extends ConfigModelPasswordManagingAuthenticationProvider<PlainAuthenticationProvider>
{
    private final List<String> _mechanisms = Collections.unmodifiableList(Arrays.asList(PlainSaslServer.MECHANISM,
                                                                                        CRAMMD5Initialiser.MECHANISM));


    @ManagedObjectFactoryConstructor
    protected PlainAuthenticationProvider(final Map<String, Object> attributes, final Broker broker)
    {
        super(attributes, broker);
    }

    @Override
    protected String createStoredPassword(final String password)
    {
        return password;
    }

    @Override
    void validateUser(final ManagedUser managedUser)
    {
        // NOOP
    }

    @Override
    public List<String> getMechanisms()
    {
        return _mechanisms;
    }

    @Override
    public SaslServer createSaslServer(final String mechanism,
                                       final String localFQDN,
                                       final Principal externalPrincipal)
            throws SaslException
    {
        if(PlainSaslServer.MECHANISM.equals(mechanism))
        {
            return new PlainAdapterSaslServer(this);
        }
        else if(CRAMMD5Initialiser.MECHANISM.equals(mechanism))
        {
            //simply delegate to the built in CRAM-MD5 SaslServer
            return Sasl.createSaslServer(mechanism, "AMQP", localFQDN, null, new ServerCallbackHandler());
        }
        else
        {
            throw new SaslException("Unsupported mechanism: " + mechanism);
        }
    }

    @Override
    public AuthenticationResult authenticate(final String username, final String password)
    {
        ManagedUser user = getUser(username);
        AuthenticationResult result;
        if(user != null && user.getPassword().equals(password))
        {
            result = new AuthenticationResult(new UsernamePrincipal(username));
        }
        else
        {
            result = new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR);
        }
        return result;
    }

    private class ServerCallbackHandler implements CallbackHandler
    {
        String _username;

        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException
        {
            List<Callback> callbackList = new ArrayList<>(Arrays.asList(callbacks));
            Iterator<Callback> iter = callbackList.iterator();
            while(iter.hasNext())
            {
                Callback callback = iter.next();
                if (callback instanceof NameCallback)
                {
                    _username = ((NameCallback) callback).getDefaultName();
                    iter.remove();
                    break;
                }
            }

            if(_username != null)
            {
                iter = callbackList.iterator();
                while (iter.hasNext())
                {
                    Callback callback = iter.next();
                    if (callback instanceof PasswordCallback)
                    {
                        iter.remove();
                        ManagedUser user = getUser(_username);
                        if(user != null)
                        {
                            ((PasswordCallback) callback).setPassword(user.getPassword().toCharArray());
                        }
                        else
                        {
                            ((PasswordCallback) callback).setPassword(null);
                        }
                        break;
                    }
                }
            }

            for (Callback callback : callbackList)
            {

                if (callback instanceof AuthorizeCallback)
                {
                    ((AuthorizeCallback) callback).setAuthorized(true);
                }
                else
                {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        }
    }
}
