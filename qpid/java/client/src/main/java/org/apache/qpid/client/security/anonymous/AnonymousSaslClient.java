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
package org.apache.qpid.client.security.anonymous;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

public class AnonymousSaslClient implements SaslClient
{
    public String getMechanismName() {
        return "ANONYMOUS";
    }
    public boolean hasInitialResponse() {
        return true;
    }
    public byte[] evaluateChallenge(byte[] challenge) throws SaslException {
        return new byte[0];
    }
    public boolean isComplete() {
        return true;
    }
    public byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException
    {
        throw new IllegalStateException("No security layer supported");
    }
    public byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException
    {
        throw new IllegalStateException("No security layer supported");
    }
    public Object getNegotiatedProperty(String propName) {
        return null;
    }
    public void dispose() throws SaslException {}
}
