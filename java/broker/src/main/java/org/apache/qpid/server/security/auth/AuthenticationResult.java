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
package org.apache.qpid.server.security.auth;

import javax.security.auth.Subject;

/**
 * Encapsulates the result of an attempt to authenticate.
 * <p>
 * The authentication status describes the overall outcome.
 * <p>
 * <ol>
 *  <li>If authentication status is SUCCESS, the subject will be populated.
 *  </li>
 *  <li>If authentication status is CONTINUE, the authentication has failed because the user
 *      supplied incorrect credentials (etc).  If the authentication requires it, the next challenge
 *      is made available.
 *  </li>
 *  <li>If authentication status is ERROR , the authentication decision could not be made due
 *      to a failure (such as an external system), the {@link AuthenticationResult#getCause()}
 *      will provide the underlying exception.
 *  </li>
 * </ol>
 *
 */
public class AuthenticationResult
{
    public enum AuthenticationStatus
    {
        /** Authentication successful */
        SUCCESS,
        /** Authentication not successful due to credentials problem etc */
        CONTINUE,
        /** Problem prevented the authentication from being made e.g. failure of an external system */
        ERROR
    }

    public final AuthenticationStatus _status;
    public final byte[] _challenge;
    private final Exception _cause;
    private final Subject _subject;

    public AuthenticationResult(final AuthenticationStatus status)
    {
        this(null, status, null);
    }

    public AuthenticationResult(final byte[] challenge, final AuthenticationStatus status)
    {
        this(challenge, status, null);
    }

    public AuthenticationResult(final AuthenticationStatus error, final Exception cause)
    {
        this(null, error, cause);
    }

    public AuthenticationResult(final byte[] challenge, final AuthenticationStatus status, final Exception cause)
    {
        this._status = status;
        this._challenge = challenge;
        this._cause = cause;
        this._subject = null;
    }

    public AuthenticationResult(final Subject subject)
    {
        this._status = AuthenticationStatus.SUCCESS;
        this._challenge = null;
        this._cause = null;
        this._subject = subject;
    }

    public Exception getCause()
    {
        return _cause;
    }

    public AuthenticationStatus getStatus()
    {
        return _status;
    }

    public byte[] getChallenge()
    {
        return _challenge;
    }

    public Subject getSubject()
    {
        return _subject;
    }

}
