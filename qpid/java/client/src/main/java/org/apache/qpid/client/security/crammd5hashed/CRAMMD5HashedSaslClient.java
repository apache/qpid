/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.qpid.client.security.crammd5hashed;

import java.util.Map;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.apache.qpid.client.security.UsernameHashedPasswordCallbackHandler;

/**
 * A {@link CRAMMD5HashedSaslClient} merely wraps an instance of a CRAM-MD5 SASL client delegating
 * all method calls to it, except {@link #getMechanismName()} which returns "CRAM-MD5-HASHED".
 *
 * This mechanism must be used with {@link UsernameHashedPasswordCallbackHandler} which is responsible
 * for the additional hash of the password.
 */
public class CRAMMD5HashedSaslClient implements SaslClient
{
    private final SaslClient _cramMd5SaslClient;

    public CRAMMD5HashedSaslClient(String authorizationId, String protocol, String serverName, Map<String, ?> props, CallbackHandler cbh) throws SaslException
    {
        super();
        String[] mechanisms = {"CRAM-MD5"};
        _cramMd5SaslClient = Sasl.createSaslClient(mechanisms, authorizationId, protocol, serverName, props, cbh);
    }

    public void dispose() throws SaslException
    {
        _cramMd5SaslClient.dispose();
    }

    public String getMechanismName()
    {
        return CRAMMD5HashedSaslClientFactory.MECHANISM;
    }

    public byte[] evaluateChallenge(byte[] challenge) throws SaslException
    {
        return _cramMd5SaslClient.evaluateChallenge(challenge);
    }


    public Object getNegotiatedProperty(String propName)
    {
        return _cramMd5SaslClient.getNegotiatedProperty(propName);
    }

    public boolean hasInitialResponse()
    {
        return _cramMd5SaslClient.hasInitialResponse();
    }

    public boolean isComplete()
    {
        return _cramMd5SaslClient.isComplete();
    }

    public byte[] unwrap(byte[] incoming, int offset, int len)
            throws SaslException
    {
        return _cramMd5SaslClient.unwrap(incoming, offset, len);
    }

    public byte[] wrap(byte[] outgoing, int offset, int len)
            throws SaslException
    {
        return _cramMd5SaslClient.wrap(outgoing, offset, len);
    }
}
