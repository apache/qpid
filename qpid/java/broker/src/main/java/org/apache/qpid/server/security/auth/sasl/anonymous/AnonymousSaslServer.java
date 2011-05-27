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

import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.framing.AMQFrameDecodingException;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.FieldTableFactory;

public class AnonymousSaslServer implements SaslServer
{
    public static final String MECHANISM = "ANONYMOUS";

    private boolean _complete = false;

    public AnonymousSaslServer()
    {
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
        return null;
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
}