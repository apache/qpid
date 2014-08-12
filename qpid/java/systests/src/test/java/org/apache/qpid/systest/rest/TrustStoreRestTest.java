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
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.security.FileTrustStore;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.test.utils.TestSSLConstants;

public class TrustStoreRestTest extends QpidRestTestCase
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

        //verify existence of the default trust store used by the systests
        List<Map<String, Object>> trustStores = assertNumberOfTrustStores(1);

        Map<String, Object> truststore = trustStores.get(0);
        assertTrustStoreAttributes(truststore, TestBrokerConfiguration.ENTRY_NAME_SSL_TRUSTSTORE,
                System.getProperty(QPID_HOME) + "/../" + TestSSLConstants.BROKER_TRUSTSTORE, false);
    }

    public void testCreate() throws Exception
    {
        super.setUp();

        String name = getTestName();

        assertNumberOfTrustStores(1);
        createTrustStore(name, true);
        assertNumberOfTrustStores(2);

        List<Map<String, Object>> trustStores = getRestTestHelper().getJsonAsList("truststore/" + name);
        assertNotNull("details cannot be null", trustStores);

        assertTrustStoreAttributes(trustStores.get(0), name, TestSSLConstants.TRUSTSTORE, true);
    }

    public void testDelete() throws Exception
    {
        super.setUp();

        String name = getTestName();

        assertNumberOfTrustStores(1);
        createTrustStore(name, false);
        assertNumberOfTrustStores(2);

        int responseCode = getRestTestHelper().submitRequest("truststore/" + name , "DELETE");
        assertEquals("Unexpected response code for provider deletion", 200, responseCode);

        List<Map<String, Object>> trustStore = getRestTestHelper().getJsonAsList("truststore/" + name);
        assertNotNull("details should not be null", trustStore);
        assertTrue("details should be empty as the truststore no longer exists", trustStore.isEmpty());

        //check only the default systests trust store remains
        List<Map<String, Object>> trustStores = assertNumberOfTrustStores(1);
        Map<String, Object> truststore = trustStores.get(0);
        assertTrustStoreAttributes(truststore, TestBrokerConfiguration.ENTRY_NAME_SSL_TRUSTSTORE,
                System.getProperty(QPID_HOME) + "/../" + TestSSLConstants.BROKER_TRUSTSTORE, false);
    }

    public void testDeleteFailsWhenTrustStoreInUse() throws Exception
    {
        String name = "testDeleteFailsWhenTrustStoreInUse";

        //add a new trust store config to use
        Map<String, Object> sslTrustStoreAttributes = new HashMap<String, Object>();
        sslTrustStoreAttributes.put(TrustStore.NAME, name);
        sslTrustStoreAttributes.put(FileTrustStore.PATH, TestSSLConstants.TRUSTSTORE);
        sslTrustStoreAttributes.put(FileTrustStore.PASSWORD, TestSSLConstants.TRUSTSTORE_PASSWORD);
        getBrokerConfiguration().addObjectConfiguration(TrustStore.class,sslTrustStoreAttributes);

        //add the SSL port using it
        Map<String, Object> sslPortAttributes = new HashMap<String, Object>();
        sslPortAttributes.put(Port.TRANSPORTS, Collections.singleton(Transport.SSL));
        sslPortAttributes.put(Port.PORT, DEFAULT_SSL_PORT);
        sslPortAttributes.put(Port.NAME, TestBrokerConfiguration.ENTRY_NAME_SSL_PORT);
        sslPortAttributes.put(Port.AUTHENTICATION_PROVIDER, TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER);
        sslPortAttributes.put(Port.KEY_STORE, TestBrokerConfiguration.ENTRY_NAME_SSL_KEYSTORE);
        sslPortAttributes.put(Port.TRUST_STORES, Collections.singleton(name));
        getBrokerConfiguration().addObjectConfiguration(Port.class, sslPortAttributes);

        super.setUp();

        //verify the truststore is there
        assertNumberOfTrustStores(2);

        List<Map<String, Object>> trustStore = getRestTestHelper().getJsonAsList("truststore/" + name);
        assertNotNull("details should not be null", trustStore);
        assertTrustStoreAttributes(trustStore.get(0), name, TestSSLConstants.TRUSTSTORE, false);

        //try to delete it, which should fail as it is in use
        int responseCode = getRestTestHelper().submitRequest("truststore/" + name , "DELETE");
        assertEquals("Unexpected response code for provider deletion", 409, responseCode);

        //check its still there
        assertNumberOfTrustStores(2);
        trustStore = getRestTestHelper().getJsonAsList("truststore/" + name);
        assertNotNull("details should not be null", trustStore);
        assertTrustStoreAttributes(trustStore.get(0), name, TestSSLConstants.TRUSTSTORE, false);
    }

    public void testUpdateWithGoodPathSucceeds() throws Exception
    {
        super.setUp();

        String name = getTestName();

        assertNumberOfTrustStores(1);
        createTrustStore(name, false);
        assertNumberOfTrustStores(2);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(TrustStore.NAME, name);
        attributes.put(FileTrustStore.PATH, TestSSLConstants.TRUSTSTORE);

        int responseCode = getRestTestHelper().submitRequest("truststore/" + name , "PUT", attributes);
        assertEquals("Unexpected response code for truststore update", 200, responseCode);

        List<Map<String, Object>> trustStore = getRestTestHelper().getJsonAsList("truststore/" + name);
        assertNotNull("details should not be null", trustStore);

        assertTrustStoreAttributes(trustStore.get(0), name, TestSSLConstants.TRUSTSTORE, false);
    }

    public void testUpdateWithNonExistentPathFails() throws Exception
    {
        super.setUp();

        String name = getTestName();

        assertNumberOfTrustStores(1);
        createTrustStore(name, false);
        assertNumberOfTrustStores(2);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(TrustStore.NAME, name);
        attributes.put(FileTrustStore.PATH, "does.not.exist");

        int responseCode = getRestTestHelper().submitRequest("truststore/" + name , "PUT", attributes);
        assertEquals("Unexpected response code for trust store update", 409, responseCode);

        List<Map<String, Object>> trustStore = getRestTestHelper().getJsonAsList("truststore/" + name);
        assertNotNull("details should not be null", trustStore);

        //verify the details remain unchanged
        assertTrustStoreAttributes(trustStore.get(0), name, TestSSLConstants.TRUSTSTORE, false);
    }

    public void testUpdatePeersOnly() throws Exception
    {
        super.setUp();

        String name = getTestName();

        assertNumberOfTrustStores(1);
        createTrustStore(name, false);
        assertNumberOfTrustStores(2);

        //update the peersOnly attribute from false to true
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(TrustStore.NAME, name);
        attributes.put(FileTrustStore.PEERS_ONLY, true);

        int responseCode = getRestTestHelper().submitRequest("truststore/" + name , "PUT", attributes);
        assertEquals("Unexpected response code for trust store update", 200, responseCode);

        List<Map<String, Object>> trustStore = getRestTestHelper().getJsonAsList("truststore/" + name);
        assertNotNull("details should not be null", trustStore);

        assertTrustStoreAttributes(trustStore.get(0), name, TestSSLConstants.TRUSTSTORE, true);

        //Update peersOnly to clear it (i.e go from from true to null, which will default to false)
        attributes = new HashMap<String, Object>();
        attributes.put(TrustStore.NAME, name);
        attributes.put(FileTrustStore.PEERS_ONLY, null);

        responseCode = getRestTestHelper().submitRequest("truststore/" + name , "PUT", attributes);
        assertEquals("Unexpected response code for trust store update", 200, responseCode);

        trustStore = getRestTestHelper().getJsonAsList("truststore/" + name);
        assertNotNull("details should not be null", trustStore);

        assertTrustStoreAttributes(trustStore.get(0), name, TestSSLConstants.TRUSTSTORE, false);
    }

    private List<Map<String, Object>> assertNumberOfTrustStores(int numberOfTrustStores) throws IOException,
    JsonParseException, JsonMappingException
    {
        List<Map<String, Object>> trustStores = getRestTestHelper().getJsonAsList("truststore");
        assertNotNull("trust stores should not be null", trustStores);
        assertEquals("Unexpected number of trust stores", numberOfTrustStores, trustStores.size());

        return trustStores;
    }

    private void createTrustStore(String name, boolean peersOnly) throws IOException, JsonGenerationException, JsonMappingException
    {
        Map<String, Object> trustStoreAttributes = new HashMap<String, Object>();
        trustStoreAttributes.put(TrustStore.NAME, name);
        //deliberately using the client trust store to differentiate from the one we are already for broker
        trustStoreAttributes.put(FileTrustStore.PATH, TestSSLConstants.TRUSTSTORE);
        trustStoreAttributes.put(FileTrustStore.PASSWORD, TestSSLConstants.TRUSTSTORE_PASSWORD);
        trustStoreAttributes.put(FileTrustStore.PEERS_ONLY, peersOnly);

        int responseCode = getRestTestHelper().submitRequest("truststore/" + name, "PUT", trustStoreAttributes);
        assertEquals("Unexpected response code", 201, responseCode);
    }

    private void assertTrustStoreAttributes(Map<String, Object> truststore, String name, String path, boolean peersOnly)
    {
        assertEquals("default systests trust store is missing",
                name, truststore.get(TrustStore.NAME));
        assertEquals("unexpected path to trust store",
                path, truststore.get(FileTrustStore.PATH));
        assertEquals("unexpected (dummy) password of default systests trust store",
                     AbstractConfiguredObject.SECURED_STRING_VALUE, truststore.get(FileTrustStore.PASSWORD));
        assertEquals("unexpected type of default systests trust store",
                java.security.KeyStore.getDefaultType(), truststore.get(FileTrustStore.TRUST_STORE_TYPE));
        assertEquals("unexpected peersOnly value",
                peersOnly, truststore.get(FileTrustStore.PEERS_ONLY));
    }
}
