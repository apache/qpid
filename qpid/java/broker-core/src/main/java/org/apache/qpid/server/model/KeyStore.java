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

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import javax.net.ssl.KeyManager;

public interface KeyStore<X extends KeyStore<X>> extends ConfiguredObject<X>
{
    String DURABLE = "durable";
    String LIFETIME_POLICY = "lifetimePolicy";
    String STATE = "state";
    String DESCRIPTION = "description";

    String PATH = "path";
    String PASSWORD = "password";
    String KEY_STORE_TYPE = "keyStoreType";
    String CERTIFICATE_ALIAS = "certificateAlias";
    String KEY_MANAGER_FACTORY_ALGORITHM = "keyManagerFactoryAlgorithm";

    @ManagedAttribute( secure = true, automate = true, mandatory = true )
    public String getPassword();
    
    @ManagedAttribute( automate = true, mandatory = true)
    public String getPath();

    @ManagedAttribute( automate = true )
    public String getCertificateAlias();

    @ManagedAttribute( automate = true )
    public String getKeyManagerFactoryAlgorithm();

    @ManagedAttribute( automate = true )
    public String getKeyStoreType();

    public void setPassword(String password);

    public KeyManager[] getKeyManagers() throws GeneralSecurityException;

}
