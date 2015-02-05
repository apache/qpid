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
package org.apache.qpid.systest.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;


import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.security.FileKeyStore;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.test.utils.TestSSLConstants;
import org.apache.qpid.util.DataUrlUtils;
import org.apache.qpid.util.FileUtils;

public class KeyStoreRestTest extends QpidRestTestCase
{
    @Override
    public void setUp() throws Exception
    {
        // not calling super.setUp() to avoid broker start-up until
        // after any necessary configuration
    }

    public void testGet() throws Exception
    {
        super.setUp();

        //verify existence of the default keystore used by the systests
        List<Map<String, Object>> keyStores = assertNumberOfKeyStores(1);

        Map<String, Object> keystore = keyStores.get(0);

        assertEquals("Unexpected name", TestBrokerConfiguration.ENTRY_NAME_SSL_KEYSTORE, keystore.get(KeyStore.NAME));
        assertEquals("unexpected path to key store", AbstractConfiguredObject.SECURED_STRING_VALUE, keystore.get(FileKeyStore.STORE_URL));
        assertEquals("unexpected (dummy) password of default systests key store", AbstractConfiguredObject.SECURED_STRING_VALUE, keystore.get(FileKeyStore.PASSWORD));
        assertEquals("unexpected type of default systests key store", java.security.KeyStore.getDefaultType(), keystore.get(FileKeyStore.KEY_STORE_TYPE));
        assertFalse("should not be a certificateAlias attribute", keystore.containsKey(FileKeyStore.CERTIFICATE_ALIAS));
    }

    public void testCreate() throws Exception
    {
        super.setUp();

        String name = getTestName();
        String certAlias = "app2";

        assertNumberOfKeyStores(1);
        createKeyStore(name, certAlias, TestSSLConstants.KEYSTORE, TestSSLConstants.KEYSTORE_PASSWORD);
        assertNumberOfKeyStores(2);

        List<Map<String, Object>> keyStores = getRestTestHelper().getJsonAsList("keystore/" + name + "?actuals=true");
        assertNotNull("details cannot be null", keyStores);

        Map<String, Object> keystore = keyStores.get(0);
        assertEquals("Unexpected name", name, keystore.get(KeyStore.NAME));
        assertEquals("unexpected path to key store", TestSSLConstants.KEYSTORE, keystore.get(FileKeyStore.STORE_URL));
        assertEquals("unexpected password", TestSSLConstants.KEYSTORE_PASSWORD, keystore.get(FileKeyStore.PASSWORD));
        assertEquals("unexpected alias", certAlias, keystore.get(FileKeyStore.CERTIFICATE_ALIAS));
    }

    public void testCreateWithDataUrl() throws Exception
    {
        super.setUp();

        String name = getTestName();
        byte[] keystoreAsBytes = FileUtils.readFileAsBytes(TestSSLConstants.KEYSTORE);
        String dataUrlForKeyStore = DataUrlUtils.getDataUrlForBytes(keystoreAsBytes);

        assertNumberOfKeyStores(1);
        createKeyStore(name, null, dataUrlForKeyStore, TestSSLConstants.KEYSTORE_PASSWORD);
        assertNumberOfKeyStores(2);

        List<Map<String, Object>> keyStores = getRestTestHelper().getJsonAsList("keystore/" + name + "?actuals=true");
        assertNotNull("details cannot be null", keyStores);

        Map<String, Object> keystore = keyStores.get(0);
        assertEquals("Unexpected name", name, keystore.get(KeyStore.NAME));
        assertEquals("unexpected data", dataUrlForKeyStore, keystore.get(FileKeyStore.STORE_URL));
        assertEquals("unexpected password", TestSSLConstants.KEYSTORE_PASSWORD, keystore.get(FileKeyStore.PASSWORD));
        assertEquals("unexpected alias", null, keystore.get(FileKeyStore.CERTIFICATE_ALIAS));
    }

    public void testDelete() throws Exception
    {
        super.setUp();

        String name = getTestName();
        String certAlias = "app2";

        assertNumberOfKeyStores(1);
        createKeyStore(name, certAlias, TestSSLConstants.KEYSTORE, TestSSLConstants.KEYSTORE_PASSWORD);
        assertNumberOfKeyStores(2);

        getRestTestHelper().submitRequest("keystore/" + name, "DELETE", HttpServletResponse.SC_OK);

        List<Map<String, Object>> keyStore = getRestTestHelper().getJsonAsList("keystore/" + name + "?actuals=true");
        assertNotNull("details should not be null", keyStore);
        assertTrue("details should be empty as the keystore no longer exists", keyStore.isEmpty());

        //check only the default systests key store remains
        List<Map<String, Object>> keyStores = assertNumberOfKeyStores(1);
        Map<String, Object> keystore = keyStores.get(0);
        assertEquals("Unexpected name", TestBrokerConfiguration.ENTRY_NAME_SSL_KEYSTORE, keystore.get(KeyStore.NAME));
        assertEquals("unexpected path to key store", AbstractConfiguredObject.SECURED_STRING_VALUE, keystore.get(FileKeyStore.STORE_URL));
        assertEquals("unexpected (dummy) password of default systests key store", AbstractConfiguredObject.SECURED_STRING_VALUE, keystore.get(FileKeyStore.PASSWORD));
        assertFalse("should not be a certificateAlias attribute", keystore.containsKey(FileKeyStore.CERTIFICATE_ALIAS));
    }

    public void testUpdate() throws Exception
    {
        super.setUp();

        String name = getTestName();

        assertNumberOfKeyStores(1);
        createKeyStore(name, null, TestSSLConstants.KEYSTORE, TestSSLConstants.KEYSTORE_PASSWORD);
        assertNumberOfKeyStores(2);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(KeyStore.NAME, name);
        attributes.put(FileKeyStore.STORE_URL, TestSSLConstants.UNTRUSTED_KEYSTORE);

        getRestTestHelper().submitRequest("keystore/" + name, "PUT", attributes, HttpServletResponse.SC_OK);

        List<Map<String, Object>> keyStores = getRestTestHelper().getJsonAsList("keystore/" + name + "?actuals=true");
        assertNotNull("details should not be null", keyStores);

        Map<String, Object> keystore = keyStores.get(0);
        assertEquals("Unexpected name", name, keystore.get(KeyStore.NAME));
        assertEquals("unexpected data", TestSSLConstants.UNTRUSTED_KEYSTORE, keystore.get(FileKeyStore.STORE_URL));
        assertEquals("unexpected password", TestSSLConstants.KEYSTORE_PASSWORD, keystore.get(FileKeyStore.PASSWORD));
        assertEquals("unexpected alias", null, keystore.get(FileKeyStore.CERTIFICATE_ALIAS));
    }


    private List<Map<String, Object>> assertNumberOfKeyStores(int numberOfKeystores) throws Exception
    {
        List<Map<String, Object>> keyStores = getRestTestHelper().getJsonAsList("keystore");
        assertNotNull("keystores should not be null", keyStores);
        assertEquals("Unexpected number of keystores", numberOfKeystores, keyStores.size());

        return keyStores;
    }

    private void createKeyStore(String name, String certAlias, final String keyStorePath, final String keystorePassword) throws Exception
    {
        Map<String, Object> keyStoreAttributes = new HashMap<>();
        keyStoreAttributes.put(KeyStore.NAME, name);
        keyStoreAttributes.put(FileKeyStore.STORE_URL, keyStorePath);
        keyStoreAttributes.put(FileKeyStore.PASSWORD, keystorePassword);
        if (certAlias != null)
        {
            keyStoreAttributes.put(FileKeyStore.CERTIFICATE_ALIAS, certAlias);
        }

        getRestTestHelper().submitRequest("keystore/" + name, "PUT", keyStoreAttributes, HttpServletResponse.SC_CREATED);
    }

}
