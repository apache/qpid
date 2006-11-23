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
package org.apache.qpid.server.security.auth;

public class AuthenticationResult
{
    public enum AuthenticationStatus
    {
        SUCCESS, CONTINUE, ERROR
    }

    public AuthenticationStatus status;
    public byte[] challenge;

    public AuthenticationResult(byte[] challenge, AuthenticationStatus status)
    {
        this.status = status;
        this.challenge = challenge;
    }

    public AuthenticationResult(AuthenticationStatus status)
    {
        this.status = status;
    }
}
