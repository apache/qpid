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
package org.apache.qpid.server.security;

import javax.net.ssl.KeyManagerFactory;

import org.apache.qpid.server.model.DerivedAttribute;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.ManagedAttribute;
import org.apache.qpid.server.model.ManagedContextDefault;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.RuntimeDefault;

@ManagedObject( category = false, type = "FileKeyStore" )
public interface FileKeyStore<X extends FileKeyStore<X>> extends KeyStore<X>
{
    String KEY_MANAGER_FACTORY_ALGORITHM = "keyManagerFactoryAlgorithm";
    String CERTIFICATE_ALIAS = "certificateAlias";
    String KEY_STORE_TYPE = "keyStoreType";
    String PASSWORD = "password";
    String STORE_URL = "storeUrl";

    @ManagedContextDefault(name = "keyStoreFile.keyStoreType")
    RuntimeDefault<String> DEFAULT_KEYSTORE_TYPE =
            new RuntimeDefault<String>()
            {
                @Override
                public String value()
                {
                    return java.security.KeyStore.getDefaultType();
                }
            };
    @ManagedContextDefault(name = "keyStoreFile.keyManagerFactoryAlgorithm")
    RuntimeDefault<String> DEFAULT_KEY_MANAGER_FACTORY_ALGORITHM =
            new RuntimeDefault<String>()
            {
                @Override
                public String value()
                {
                    return KeyManagerFactory.getDefaultAlgorithm();
                }
            };

    @ManagedAttribute(defaultValue = "${this:path}")
    String getDescription();

    @ManagedAttribute(  mandatory = true, secure = true, oversize = true, oversizedAltText = OVER_SIZED_ATTRIBUTE_ALTERNATIVE_TEXT, secureValueFilter = "^data\\:.*")
    String getStoreUrl();

    @DerivedAttribute
    String getPath();

    @ManagedAttribute
    String getCertificateAlias();

    @ManagedAttribute( defaultValue = "${keyStoreFile.keyManagerFactoryAlgorithm}" )
    String getKeyManagerFactoryAlgorithm();

    @ManagedAttribute( defaultValue = "${keyStoreFile.keyStoreType}" )
    String getKeyStoreType();

    @ManagedAttribute( secure = true, mandatory = true )
    String getPassword();
}
