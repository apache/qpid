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
 *
 */
package org.apache.qpid.server.security.auth.manager;

import java.net.SocketAddress;

import java.util.Map;
import org.apache.qpid.common.Closeable;
import org.apache.qpid.server.security.SubjectCreator;

/**
 * Registry for {@link AuthenticationManager} instances, also exposing them wrapped in {@link SubjectCreator}'s
 * as a convenience.
 *
 * <p>It is important to {@link #close()} the registry after use and this allows the AuthenticationManagers
 * to reverse any security registrations they have performed.</p>
 */
public interface IAuthenticationManagerRegistry extends Closeable
{
    /**
     * Returns the {@link SubjectCreator} associated with a particular {@link SocketAddress}.
     * If no subject creator is associated with this address, a default will be
     * returned.  Null is never returned.
     */
    public SubjectCreator getSubjectCreator(SocketAddress address);

    Map<String, AuthenticationManager> getAvailableAuthenticationManagers();

    public static interface RegistryChangeListener
    {
        void authenticationManagerRegistered(AuthenticationManager authenticationManager);
        void authenticationManagerUnregistered(AuthenticationManager authenticationManager);
    }

    public void addRegistryChangeListener(RegistryChangeListener listener);
}