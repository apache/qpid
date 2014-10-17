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
package org.apache.qpid.server.model;

import java.security.Principal;
import java.util.Collection;
import java.util.List;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.AuthenticationResult;

@ManagedObject( creatable = false )
public interface AuthenticationProvider<X extends AuthenticationProvider<X>> extends ConfiguredObject<X>
{
    //children
    Collection<VirtualHostAlias> getVirtualHostPortBindings();

    /**
     * A temporary method to create SubjectCreator.
     *
     * TODO: move all the functionality from SubjectCreator into AuthenticationProvider
     * @param secure
     */
    SubjectCreator getSubjectCreator(final boolean secure);

    /**
     * Returns the preferences provider associated with this authentication provider
     * @return PreferencesProvider
     */
    PreferencesProvider<?> getPreferencesProvider();

    /**
     * Sets the preferences provider
     * @param preferencesProvider
     */
    void setPreferencesProvider(PreferencesProvider<?> preferencesProvider);

    void recoverUser(User user);

    /**
     * Gets the SASL mechanisms known to this manager.
     *
     * @return SASL mechanism names, space separated.
     */
    @DerivedAttribute
    List<String> getMechanisms();


    @ManagedAttribute( defaultValue = "[ \"PLAIN\" ]")
    List<String> getSecureOnlyMechanisms();
    /**
     * Creates a SASL server for the specified mechanism name for the given
     * fully qualified domain name.
     *
     * @param mechanism mechanism name
     * @param localFQDN domain name
     * @param externalPrincipal externally authenticated Principal
     * @return SASL server
     * @throws javax.security.sasl.SaslException
     */
    SaslServer createSaslServer(String mechanism, String localFQDN, Principal externalPrincipal) throws SaslException;

    /**
     * Authenticates a user using SASL negotiation.
     *
     * @param server SASL server
     * @param response SASL response to process
     *
     * @return authentication result
     */
    AuthenticationResult authenticate(SaslServer server, byte[] response);

    /**
     * Authenticates a user using their username and password.
     *
     * @param username username
     * @param password password
     *
     * @return authentication result
     */
    AuthenticationResult authenticate(String username, String password);

}
