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
package org.apache.qpid.server.security.auth.sasl.plain;

import java.util.Arrays;

import javax.security.auth.callback.PasswordCallback;

/**
 * Custom PasswordCallback for use during the PLAIN authentication process.
 * 
 * To be used in combination with PrincipalDatabase implementations that
 * can either set a plain text value in the parent callback, or use the
 * setAuthenticated(bool) method after observing the incoming plain text. 
 * 
 * isAuthenticated() should then be used to determine the final result.
 *
 */
public class PlainPasswordCallback extends PasswordCallback
{
    private char[] _plainPassword;
    private boolean _authenticated = false;

    /**
     * Constructs a new PlainPasswordCallback with the incoming plain text password.
     * 
     * @throws NullPointerException if the incoming plain text is null
     */
    public PlainPasswordCallback(String prompt, boolean echoOn, String plainPassword)
    {
        super(prompt, echoOn);
        
        if(plainPassword == null)
        {
            throw new NullPointerException("Incoming plain text cannot be null");
        }

        _plainPassword = plainPassword.toCharArray();
    }

    public String getPlainPassword()
    {
        return new String(_plainPassword);
    }

    public void setAuthenticated(boolean authenticated)
    {
        _authenticated = authenticated;
    }

    /**
     * Method to determine if the incoming plain password is authenticated
     * 
     * @return true if the stored password matches the incoming text, or setAuthenticated(true) has been called
     */
    public boolean isAuthenticated()
    {
        char[] storedPassword = getPassword();

        return Arrays.equals(_plainPassword, storedPassword) || _authenticated;
    }
}

