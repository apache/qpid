/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.util;

import org.apache.qpid.server.security.auth.AuthenticationManager;
import org.apache.qpid.server.security.auth.AuthenticationResult;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

public class NullAuthenticationManager implements AuthenticationManager
{
    public String getMechanisms()
    {
        return "PLAIN";
    }

    public SaslServer createSaslServer(String mechanism, String localFQDN) throws SaslException
    {
        return new SaslServer()
        {
            public String getMechanismName()
            {
                return "PLAIN";
            }

            public byte[] evaluateResponse(byte[] response) throws SaslException
            {
                return new byte[0];
            }

            public boolean isComplete()
            {
                return true;
            }

            public String getAuthorizationID()
            {
                return "guest";
            }

            public byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException
            {
                return new byte[0];
            }

            public byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException
            {
                return new byte[0];
            }

            public Object getNegotiatedProperty(String propName)
            {
                return null;
            }

            public void dispose() throws SaslException
            {
            }
        };
    }

    public AuthenticationResult authenticate(SaslServer server, byte[] response)
    {
        return new AuthenticationResult(AuthenticationResult.AuthenticationStatus.SUCCESS);        
    }
}
