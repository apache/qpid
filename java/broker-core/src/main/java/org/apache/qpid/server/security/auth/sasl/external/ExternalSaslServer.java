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
package org.apache.qpid.server.security.auth.sasl.external;

import java.security.Principal;

import javax.security.auth.x500.X500Principal;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.transport.network.security.ssl.SSLUtil;

public class ExternalSaslServer implements SaslServer
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalSaslServer.class);

    public static final String MECHANISM = "EXTERNAL";

    private boolean _complete = false;
    private final Principal _externalPrincipal;
    private final boolean _useFullDN;

    public ExternalSaslServer(Principal externalPrincipal, boolean useFullDN)
    {
        _useFullDN = useFullDN;
        _externalPrincipal = externalPrincipal;
    }

    public String getMechanismName()
    {
        return MECHANISM;
    }

    public byte[] evaluateResponse(byte[] response) throws SaslException
    {
        _complete = true;
        return null;
    }

    public boolean isComplete()
    {
        return _complete;
    }

    public String getAuthorizationID()
    {
        return getAuthenticatedPrincipal() == null ? null : getAuthenticatedPrincipal().getName();
    }

    public byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException
    {
        throw new SaslException("Unsupported operation");
    }

    public byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException
    {
        throw new SaslException("Unsupported operation");
    }

    public Object getNegotiatedProperty(String propName)
    {
        return null;
    }

    public void dispose() throws SaslException
    {
    }

    public Principal getAuthenticatedPrincipal()
    {
        if (_externalPrincipal instanceof X500Principal && !_useFullDN)
        {
            // Construct username as <CN>@<DC1>.<DC2>.<DC3>....<DCN>
            String username;
            String dn = ((X500Principal) _externalPrincipal).getName(X500Principal.RFC2253);

            if(LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Parsing username from Principal DN: " + dn);
            }

            username = SSLUtil.getIdFromSubjectDN(dn);
            if (username.isEmpty())
            {
                // CN is empty => Cannot construct username => Authentication failed => return null
                if(LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("CN value was empty in Principal name, unable to construct username");
                }
                return null;
            }
            if(LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Constructing Principal with username: " + username);
            }
            return new UsernamePrincipal(username);
        }
        else
        {
            if(LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Using external Principal: " + _externalPrincipal);
            }
            return _externalPrincipal;
        }
    }
}
