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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;

import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.security.FileKeyStore;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.test.utils.TestSSLConstants;

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
        assertKeyStoreAttributes(keystore, TestBrokerConfiguration.ENTRY_NAME_SSL_KEYSTORE,
                QPID_HOME + "/../" + TestSSLConstants.BROKER_KEYSTORE, null);
    }

    public void testCreate() throws Exception
    {
        super.setUp();

        String name = getTestName();
        String certAlias = "app2";

        assertNumberOfKeyStores(1);
        createKeyStore(name, certAlias);
        assertNumberOfKeyStores(2);

        List<Map<String, Object>> keyStores = getRestTestHelper().getJsonAsList("keystore/" + name);
        assertNotNull("details cannot be null", keyStores);

        assertKeyStoreAttributes(keyStores.get(0), name, TestSSLConstants.KEYSTORE, certAlias);
    }

    public void testDelete() throws Exception
    {
        super.setUp();

        String name = getTestName();
        String certAlias = "app2";

        assertNumberOfKeyStores(1);
        createKeyStore(name, certAlias);
        assertNumberOfKeyStores(2);

        int responseCode = getRestTestHelper().submitRequest("keystore/" + name , "DELETE");
        assertEquals("Unexpected response code for provider deletion", 200, responseCode);

        List<Map<String, Object>> keyStore = getRestTestHelper().getJsonAsList("keystore/" + name);
        assertNotNull("details should not be null", keyStore);
        assertTrue("details should be empty as the keystore no longer exists", keyStore.isEmpty());

        //check only the default systests key store remains
        List<Map<String, Object>> keyStores = assertNumberOfKeyStores(1);
        Map<String, Object> keystore = keyStores.get(0);
        assertKeyStoreAttributes(keystore, TestBrokerConfiguration.ENTRY_NAME_SSL_KEYSTORE,
                QPID_HOME + "/../" + TestSSLConstants.BROKER_KEYSTORE, null);
    }

    public void testDeleteFailsWhenKeyStoreInUse() throws Exception
    {
        String name = "testDeleteFailsWhenKeyStoreInUse";

        //add a new key store config to use
        Map<String, Object> sslKeyStoreAttributes = new HashMap<String, Object>();
        sslKeyStoreAttributes.put(KeyStore.NAME, name);
        sslKeyStoreAttributes.put(FileKeyStore.PATH, TestSSLConstants.BROKER_KEYSTORE);
        sslKeyStoreAttributes.put(FileKeyStore.PASSWORD, TestSSLConstants.BROKER_KEYSTORE_PASSWORD);
        getBrokerConfiguration().addObjectConfiguration(KeyStore.class,sslKeyStoreAttributes);

        //add the SSL port using it
        Map<String, Object> sslPortAttributes = new HashMap<String, Object>();
        sslPortAttributes.put(Port.TRANSPORTS, Collections.singleton(Transport.SSL));
        sslPortAttributes.put(Port.PORT, DEFAULT_SSL_PORT);
        sslPortAttributes.put(Port.NAME, TestBrokerConfiguration.ENTRY_NAME_SSL_PORT);
        sslPortAttributes.put(Port.AUTHENTICATION_PROVIDER, TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER);
        sslPortAttributes.put(Port.KEY_STORE, name);
        getBrokerConfiguration().addObjectConfiguration(Port.class,sslPortAttributes);

        super.setUp();

        //verify the keystore is there
        assertNumberOfKeyStores(2);

        List<Map<String, Object>> keyStore = getRestTestHelper().getJsonAsList("keystore/" + name);
        assertNotNull("details should not be null", keyStore);
        assertKeyStoreAttributes(keyStore.get(0), name, TestSSLConstants.BROKER_KEYSTORE, null);

        //try to delete it, which should fail as it is in use
        int responseCode = getRestTestHelper().submitRequest("keystore/" + name , "DELETE");
        assertEquals("Unexpected response code for provider deletion", 409, responseCode);

        //check its still there
        assertNumberOfKeyStores(2);
        keyStore = getRestTestHelper().getJsonAsList("keystore/" + name);
        assertNotNull("details should not be null", keyStore);
        assertKeyStoreAttributes(keyStore.get(0), name, TestSSLConstants.BROKER_KEYSTORE, null);
    }

    public void testUpdateWithGoodPathSucceeds() throws Exception
    {
        super.setUp();

        String name = getTestName();

        assertNumberOfKeyStores(1);
        createKeyStore(name, null);
        assertNumberOfKeyStores(2);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(KeyStore.NAME, name);
        attributes.put(FileKeyStore.PATH, TestSSLConstants.UNTRUSTED_KEYSTORE);

        int responseCode = getRestTestHelper().submitRequest("keystore/" + name , "PUT", attributes);
        assertEquals("Unexpected response code for keystore update", 200, responseCode);

        List<Map<String, Object>> keyStore = getRestTestHelper().getJsonAsList("keystore/" + name);
        assertNotNull("details should not be null", keyStore);

        assertKeyStoreAttributes(keyStore.get(0), name, TestSSLConstants.UNTRUSTED_KEYSTORE, null);
    }

    public void testUpdateWithNonExistentPathFails() throws Exception
    {
        super.setUp();

        String name = getTestName();

        assertNumberOfKeyStores(1);
        createKeyStore(name, null);
        assertNumberOfKeyStores(2);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(KeyStore.NAME, name);
        attributes.put(FileKeyStore.PATH, "does.not.exist");

        int responseCode = getRestTestHelper().submitRequest("keystore/" + name , "PUT", attributes);
        assertEquals("Unexpected response code for keystore update", 409, responseCode);

        List<Map<String, Object>> keyStore = getRestTestHelper().getJsonAsList("keystore/" + name);
        assertNotNull("details should not be null", keyStore);

        //verify the details remain unchanged
        assertKeyStoreAttributes(keyStore.get(0), name, TestSSLConstants.KEYSTORE, null);
    }

    public void testUpdateCertificateAlias() throws Exception
    {
        super.setUp();

        String name = getTestName();

        assertNumberOfKeyStores(1);
        createKeyStore(name, "app1");
        assertNumberOfKeyStores(2);

        List<Map<String, Object>> keyStore = getRestTestHelper().getJsonAsList("keystore/" + name);
        assertNotNull("details should not be null", keyStore);
        assertKeyStoreAttributes(keyStore.get(0), name, TestSSLConstants.KEYSTORE, "app1");

        //Update the certAlias from app1 to app2
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(KeyStore.NAME, name);
        attributes.put(FileKeyStore.CERTIFICATE_ALIAS, "app2");

        int responseCode = getRestTestHelper().submitRequest("keystore/" + name , "PUT", attributes);
        assertEquals("Unexpected response code for keystore update", 200, responseCode);

        keyStore = getRestTestHelper().getJsonAsList("keystore/" + name);
        assertNotNull("details should not be null", keyStore);

        assertKeyStoreAttributes(keyStore.get(0), name, TestSSLConstants.KEYSTORE, "app2");

        //Update the certAlias to clear it (i.e go from from app1 to null)
        attributes = new HashMap<String, Object>();
        attributes.put(KeyStore.NAME, name);
        attributes.put(FileKeyStore.CERTIFICATE_ALIAS, null);

        responseCode = getRestTestHelper().submitRequest("keystore/" + name , "PUT", attributes);
        assertEquals("Unexpected response code for keystore update", 200, responseCode);

        keyStore = getRestTestHelper().getJsonAsList("keystore/" + name);
        assertNotNull("details should not be null", keyStore);

        assertKeyStoreAttributes(keyStore.get(0), name, TestSSLConstants.KEYSTORE, null);
    }

    private List<Map<String, Object>> assertNumberOfKeyStores(int numberOfKeystores) throws IOException,
    JsonParseException, JsonMappingException
    {
        List<Map<String, Object>> keyStores = getRestTestHelper().getJsonAsList("keystore");
        assertNotNull("keystores should not be null", keyStores);
        assertEquals("Unexpected number of keystores", numberOfKeystores, keyStores.size());

        return keyStores;
    }

    private void createKeyStore(String name, String certAlias) throws IOException, JsonGenerationException, JsonMappingException
    {
        Map<String, Object> keyStoreAttributes = new HashMap<String, Object>();
        keyStoreAttributes.put(KeyStore.NAME, name);
        keyStoreAttributes.put(FileKeyStore.PATH, TestSSLConstants.KEYSTORE);
        keyStoreAttributes.put(FileKeyStore.PASSWORD, TestSSLConstants.KEYSTORE_PASSWORD);
        keyStoreAttributes.put(FileKeyStore.CERTIFICATE_ALIAS, certAlias);

        int responseCode = getRestTestHelper().submitRequest("keystore/" + name, "PUT", keyStoreAttributes);
        assertEquals("Unexpected response code", 201, responseCode);
    }

    private void assertKeyStoreAttributes(Map<String, Object> keystore, String name, String path, String certAlias)
    {
        assertEquals("default systests key store is missing",
                name, keystore.get(KeyStore.NAME));
        assertEquals("unexpected path to key store",
                path, keystore.get(FileKeyStore.PATH));
        assertEquals("unexpected (dummy) password of default systests key store",
                     AbstractConfiguredObject.SECURED_STRING_VALUE, keystore.get(FileKeyStore.PASSWORD));
        assertEquals("unexpected type of default systests key store",
                java.security.KeyStore.getDefaultType(), keystore.get(FileKeyStore.KEY_STORE_TYPE));
        assertEquals("unexpected certificateAlias value",
                certAlias, keystore.get(FileKeyStore.CERTIFICATE_ALIAS));
        if(certAlias == null)
        {
            assertFalse("should not be a certificateAlias attribute",
                            keystore.containsKey(FileKeyStore.CERTIFICATE_ALIAS));
        }
    }
}
