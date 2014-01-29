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

package org.apache.qpid.server.security.auth.sasl;

import junit.framework.TestCase;

import org.apache.qpid.server.security.auth.database.PrincipalDatabase;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

public abstract class SaslServerTestCase extends TestCase
{
    protected SaslServer server;
    protected String username = "u";
    protected String password = "p";
    protected String notPassword = "a";
    protected PrincipalDatabase db = new TestPrincipalDatabase();
    
    protected byte[] correctResponse;
    protected byte[] wrongResponse;
    
    public void testSuccessfulAuth() throws SaslException
    {
        byte[] resp = this.server.evaluateResponse(correctResponse);
        assertNull(resp);
    }
    
    public void testFailAuth()
    {
        boolean exceptionCaught  = false;
        try
        {
            byte[] resp = this.server.evaluateResponse(wrongResponse);
        }
        catch (SaslException e)
        {
            assertTrue(e.getMessage().contains("Authentication failed"));
            exceptionCaught = true;
        }
        if (!exceptionCaught)
        {
            fail("Should have thrown SaslException");
        }
    }
    
}
