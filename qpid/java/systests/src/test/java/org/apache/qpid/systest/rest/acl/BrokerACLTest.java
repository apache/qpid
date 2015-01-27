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
package org.apache.qpid.systest.rest.acl;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.apache.qpid.server.management.plugin.HttpManagement;
import org.apache.qpid.server.model.AccessControlProvider;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ExternalFileBasedAuthenticationManager;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.model.adapter.FileBasedGroupProvider;
import org.apache.qpid.server.model.adapter.FileBasedGroupProviderImpl;
import org.apache.qpid.server.security.FileKeyStore;
import org.apache.qpid.server.security.FileTrustStore;
import org.apache.qpid.server.security.access.FileAccessControlProviderConstants;
import org.apache.qpid.server.security.acl.AbstractACLTestCase;
import org.apache.qpid.server.security.auth.manager.AnonymousAuthenticationManager;
import org.apache.qpid.server.security.auth.manager.PlainPasswordDatabaseAuthenticationManager;
import org.apache.qpid.systest.rest.QpidRestTestCase;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.test.utils.TestFileUtils;
import org.apache.qpid.test.utils.TestSSLConstants;

public class BrokerACLTest extends QpidRestTestCase
{
    private static final String ALLOWED_USER = "user1";
    private static final String DENIED_USER = "user2";
    private String _secondaryAclFileContent = "";

    @Override
    protected void customizeConfiguration() throws IOException
    {
        super.customizeConfiguration();
        getRestTestHelper().configureTemporaryPasswordFile(this, ALLOWED_USER, DENIED_USER);

        AbstractACLTestCase.writeACLFileUtil(this, "ACL ALLOW-LOG ALL ACCESS MANAGEMENT",
                "ACL ALLOW-LOG " + ALLOWED_USER + " CONFIGURE BROKER",
                "ACL DENY-LOG " + DENIED_USER + " CONFIGURE BROKER",
                "ACL DENY-LOG ALL ALL");

                _secondaryAclFileContent =
                "ACL ALLOW-LOG ALL ACCESS MANAGEMENT\n" +
                "ACL ALLOW-LOG " + ALLOWED_USER + " CONFIGURE BROKER\n" +
                "ACL DENY-LOG " + DENIED_USER + " CONFIGURE BROKER\n" +
                "ACL DENY-LOG ALL ALL";

        getBrokerConfiguration().setObjectAttribute(Plugin.class, TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT,
                HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED, true);
    }

    /* === AuthenticationProvider === */

    public void testCreateAuthenticationProviderAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String authenticationProviderName = getTestName();

        int responseCode = createAuthenticationProvider(authenticationProviderName);
        assertEquals("Provider creation should be allowed", 201, responseCode);

        assertAuthenticationProviderExists(authenticationProviderName);
    }

    public void testCreateAuthenticationProviderDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        String authenticationProviderName = getTestName();

        int responseCode = createAuthenticationProvider(authenticationProviderName);
        assertEquals("Provider creation should be denied", 403, responseCode);

        assertAuthenticationProviderDoesNotExist(authenticationProviderName);
    }

    public void testDeleteAuthenticationProviderAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String providerName = getTestName();

        int responseCode = createAuthenticationProvider(providerName);
        assertEquals("Provider creation should be allowed", 201, responseCode);

        assertAuthenticationProviderExists(providerName);

        responseCode = getRestTestHelper().submitRequest("authenticationprovider/" + providerName, "DELETE");
        assertEquals("Provider deletion should be allowed", 200, responseCode);

        assertAuthenticationProviderDoesNotExist(TEST2_VIRTUALHOST);
    }

    public void testDeleteAuthenticationProviderDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String providerName = getTestName();

        int responseCode = createAuthenticationProvider(providerName);
        assertEquals("Provider creation should be allowed", 201, responseCode);

        assertAuthenticationProviderExists(providerName);

        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);
        responseCode = getRestTestHelper().submitRequest("authenticationprovider/" + providerName, "DELETE");
        assertEquals("Provider deletion should be denied", 403, responseCode);

        assertAuthenticationProviderExists(providerName);
    }

    public void testSetAuthenticationProviderAttributesAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String providerName = TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER;

        assertAuthenticationProviderExists(providerName);

        File file = TestFileUtils.createTempFile(this, ".users", "guest:guest\n" + ALLOWED_USER + ":" + ALLOWED_USER + "\n"
                + DENIED_USER + ":" + DENIED_USER);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(AuthenticationProvider.TYPE, PlainPasswordDatabaseAuthenticationManager.PROVIDER_TYPE);
        attributes.put(ExternalFileBasedAuthenticationManager.PATH, file.getAbsolutePath());

        int responseCode = getRestTestHelper().submitRequest("authenticationprovider/" + providerName, "PUT", attributes);
        assertEquals("Setting of provider attribites should be allowed", 200, responseCode);
    }

    public void testSetAuthenticationProviderAttributesDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String providerName = TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER;

        Map<String, Object> providerData = getRestTestHelper().getJsonAsSingletonList("authenticationprovider/" + providerName);

        File file = TestFileUtils.createTempFile(this, ".users", "guest:guest\n" + ALLOWED_USER + ":" + ALLOWED_USER + "\n"
                + DENIED_USER + ":" + DENIED_USER);

        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(AuthenticationProvider.TYPE, AnonymousAuthenticationManager.PROVIDER_TYPE);
        attributes.put(ExternalFileBasedAuthenticationManager.PATH, file.getAbsolutePath());

        int responseCode = getRestTestHelper().submitRequest("authenticationprovider/" + providerName, "PUT", attributes);
        assertEquals("Setting of provider attribites should be allowed", 403, responseCode);

        Map<String, Object> provider = getRestTestHelper().getJsonAsSingletonList("authenticationprovider/" + providerName);
        assertEquals("Unexpected PATH attribute value",
                providerData.get(ExternalFileBasedAuthenticationManager.PATH),
                provider.get(ExternalFileBasedAuthenticationManager.PATH));
    }

    /* === Port === */

    public void testCreatePortAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String portName = getTestName();

        int responseCode = createPort(portName);
        assertEquals("Port creation should be allowed", 201, responseCode);

        assertPortExists(portName);
    }

    public void testCreatePortDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        String portName = getTestName();

        int responseCode = createPort(portName);
        assertEquals("Port creation should be denied", 403, responseCode);

        assertPortDoesNotExist(portName);
    }

    public void testDeletePortDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String portName = TestBrokerConfiguration.ENTRY_NAME_AMQP_PORT;
        assertPortExists(portName);

        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        int responseCode = getRestTestHelper().submitRequest("port/" + portName, "DELETE");
        assertEquals("Port deletion should be denied", 403, responseCode);

        assertPortExists(portName);
    }

    public void testDeletePortAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String portName = TestBrokerConfiguration.ENTRY_NAME_AMQP_PORT;
        assertPortExists(portName);

        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        int responseCode = getRestTestHelper().submitRequest("port/" + portName, "DELETE");
        assertEquals("Port deletion should be allowed", 200, responseCode);

        assertPortDoesNotExist(portName);
    }

    // TODO:  test disabled until allowing the updating of active ports outside management mode
    public void DISABLED_testSetPortAttributesAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String portName = getTestName();

        int responseCode = createPort(portName);
        assertEquals("Port creation should be allowed", 201, responseCode);

        assertPortExists(portName);


        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.NAME, portName);
        attributes.put(Port.AUTHENTICATION_PROVIDER, ANONYMOUS_AUTHENTICATION_PROVIDER);
        responseCode = getRestTestHelper().submitRequest("port/" + portName, "PUT", attributes);
        assertEquals("Setting of port attribites should be allowed", 200, responseCode);

        Map<String, Object> port = getRestTestHelper().getJsonAsSingletonList("port/" + portName);
        assertEquals("Unexpected authentication provider attribute value", ANONYMOUS_AUTHENTICATION_PROVIDER,
                port.get(Port.AUTHENTICATION_PROVIDER));
    }

    public void testSetPortAttributesDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String portName = getTestName();

        int responseCode = createPort(portName);
        assertEquals("Port creation should be allowed", 201, responseCode);

        assertPortExists(portName);

        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.NAME, portName);
        attributes.put(Port.PROTOCOLS, Arrays.asList(Protocol.AMQP_0_9));
        attributes.put(Port.AUTHENTICATION_PROVIDER, ANONYMOUS_AUTHENTICATION_PROVIDER);
        responseCode = getRestTestHelper().submitRequest("port/" + portName, "PUT", attributes);
        assertEquals("Setting of port attribites should be denied", 403, responseCode);

        Map<String, Object> port = getRestTestHelper().getJsonAsSingletonList("port/" + portName);
        assertEquals("Unexpected authentication provider attribute value",
                TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER, port.get(Port.AUTHENTICATION_PROVIDER));
    }

    /* === KeyStore === */

    public void testCreateKeyStoreAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String keyStoreName = getTestName();

        assertKeyStoreExistence(keyStoreName, false);

        int responseCode = createKeyStore(keyStoreName, "app1");
        assertEquals("keyStore creation should be allowed", 201, responseCode);

        assertKeyStoreExistence(keyStoreName, true);
    }

    public void testCreateKeyStoreDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        String keyStoreName = getTestName();

        assertKeyStoreExistence(keyStoreName, false);

        int responseCode = createKeyStore(keyStoreName, "app1");
        assertEquals("keyStore creation should be allowed", 403, responseCode);

        assertKeyStoreExistence(keyStoreName, false);
    }

    public void testDeleteKeyStoreDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String keyStoreName = getTestName();

        assertKeyStoreExistence(keyStoreName, false);

        int responseCode = createKeyStore(keyStoreName, "app1");
        assertEquals("keyStore creation should be allowed", 201, responseCode);

        assertKeyStoreExistence(keyStoreName, true);

        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        responseCode = getRestTestHelper().submitRequest("keystore/" + keyStoreName, "DELETE");
        assertEquals("keystore deletion should be denied", 403, responseCode);

        assertKeyStoreExistence(keyStoreName, true);
    }

    public void testDeleteKeyStoreAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String keyStoreName = getTestName();

        assertKeyStoreExistence(keyStoreName, false);

        int responseCode = createKeyStore(keyStoreName, "app1");
        assertEquals("keyStore creation should be allowed", 201, responseCode);

        assertKeyStoreExistence(keyStoreName, true);

        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        responseCode = getRestTestHelper().submitRequest("keystore/" + keyStoreName, "DELETE");
        assertEquals("keystore deletion should be allowed", 200, responseCode);

        assertKeyStoreExistence(keyStoreName, false);
    }

    public void testSetKeyStoreAttributesAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String keyStoreName = getTestName();
        String initialCertAlias = "app1";
        String updatedCertAlias = "app2";

        assertKeyStoreExistence(keyStoreName, false);

        int responseCode = createKeyStore(keyStoreName, initialCertAlias);
        assertEquals("keyStore creation should be allowed", 201, responseCode);

        assertKeyStoreExistence(keyStoreName, true);
        Map<String, Object> keyStore = getRestTestHelper().getJsonAsSingletonList("keystore/" + keyStoreName);
        assertEquals("Unexpected certificateAlias attribute value", initialCertAlias, keyStore.get(FileKeyStore.CERTIFICATE_ALIAS));

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(KeyStore.NAME, keyStoreName);
        attributes.put(FileKeyStore.CERTIFICATE_ALIAS, updatedCertAlias);
        responseCode = getRestTestHelper().submitRequest("keystore/" + keyStoreName, "PUT", attributes);
        assertEquals("Setting of keystore attributes should be allowed", 200, responseCode);

        keyStore = getRestTestHelper().getJsonAsSingletonList("keystore/" + keyStoreName);
        assertEquals("Unexpected certificateAlias attribute value", updatedCertAlias, keyStore.get(FileKeyStore.CERTIFICATE_ALIAS));
    }

    public void testSetKeyStoreAttributesDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String keyStoreName = getTestName();
        String initialCertAlias = "app1";
        String updatedCertAlias = "app2";

        assertKeyStoreExistence(keyStoreName, false);

        int responseCode = createKeyStore(keyStoreName, initialCertAlias);
        assertEquals("keyStore creation should be allowed", 201, responseCode);

        assertKeyStoreExistence(keyStoreName, true);
        Map<String, Object> keyStore = getRestTestHelper().getJsonAsSingletonList("keystore/" + keyStoreName);
        assertEquals("Unexpected certificateAlias attribute value", initialCertAlias, keyStore.get(FileKeyStore.CERTIFICATE_ALIAS));

        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(KeyStore.NAME, keyStoreName);
        attributes.put(FileKeyStore.CERTIFICATE_ALIAS, updatedCertAlias);
        responseCode = getRestTestHelper().submitRequest("keystore/" + keyStoreName, "PUT", attributes);
        assertEquals("Setting of keystore attributes should be denied", 403, responseCode);

        keyStore = getRestTestHelper().getJsonAsSingletonList("keystore/" + keyStoreName);
        assertEquals("Unexpected certificateAlias attribute value", initialCertAlias, keyStore.get(FileKeyStore.CERTIFICATE_ALIAS));
    }

    /* === TrustStore === */

    public void testCreateTrustStoreAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String trustStoreName = getTestName();

        assertTrustStoreExistence(trustStoreName, false);

        int responseCode = createTrustStore(trustStoreName, false);
        assertEquals("trustStore creation should be allowed", 201, responseCode);

        assertTrustStoreExistence(trustStoreName, true);
    }

    public void testCreateTrustStoreDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        String trustStoreName = getTestName();

        assertTrustStoreExistence(trustStoreName, false);

        int responseCode = createTrustStore(trustStoreName, false);
        assertEquals("trustStore creation should be allowed", 403, responseCode);

        assertTrustStoreExistence(trustStoreName, false);
    }

    public void testDeleteTrustStoreDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String trustStoreName = getTestName();

        assertTrustStoreExistence(trustStoreName, false);

        int responseCode = createTrustStore(trustStoreName, false);
        assertEquals("trustStore creation should be allowed", 201, responseCode);

        assertTrustStoreExistence(trustStoreName, true);

        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        responseCode = getRestTestHelper().submitRequest("truststore/" + trustStoreName, "DELETE");
        assertEquals("truststore deletion should be denied", 403, responseCode);

        assertTrustStoreExistence(trustStoreName, true);
    }

    public void testDeleteTrustStoreAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String trustStoreName = getTestName();

        assertTrustStoreExistence(trustStoreName, false);

        int responseCode = createTrustStore(trustStoreName, false);
        assertEquals("trustStore creation should be allowed", 201, responseCode);

        assertTrustStoreExistence(trustStoreName, true);

        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        responseCode = getRestTestHelper().submitRequest("truststore/" + trustStoreName, "DELETE");
        assertEquals("truststore deletion should be allowed", 200, responseCode);

        assertTrustStoreExistence(trustStoreName, false);
    }

    public void testSetTrustStoreAttributesAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String trustStoreName = getTestName();
        boolean initialPeersOnly = false;
        boolean updatedPeersOnly = true;

        assertTrustStoreExistence(trustStoreName, false);

        int responseCode = createTrustStore(trustStoreName, initialPeersOnly);
        assertEquals("trustStore creation should be allowed", 201, responseCode);

        assertTrustStoreExistence(trustStoreName, true);
        Map<String, Object> trustStore = getRestTestHelper().getJsonAsSingletonList("truststore/" + trustStoreName);
        assertEquals("Unexpected peersOnly attribute value", initialPeersOnly, trustStore.get(FileTrustStore.PEERS_ONLY));

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(TrustStore.NAME, trustStoreName);
        attributes.put(FileTrustStore.PEERS_ONLY, updatedPeersOnly);
        responseCode = getRestTestHelper().submitRequest("truststore/" + trustStoreName, "PUT", attributes);
        assertEquals("Setting of truststore attributes should be allowed", 200, responseCode);

        trustStore = getRestTestHelper().getJsonAsSingletonList("truststore/" + trustStoreName);
        assertEquals("Unexpected peersOnly attribute value", updatedPeersOnly, trustStore.get(FileTrustStore.PEERS_ONLY));
    }

    public void testSetTrustStoreAttributesDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String trustStoreName = getTestName();
        boolean initialPeersOnly = false;
        boolean updatedPeersOnly = true;

        assertTrustStoreExistence(trustStoreName, false);

        int responseCode = createTrustStore(trustStoreName, initialPeersOnly);
        assertEquals("trustStore creation should be allowed", 201, responseCode);

        assertTrustStoreExistence(trustStoreName, true);
        Map<String, Object> trustStore = getRestTestHelper().getJsonAsSingletonList("truststore/" + trustStoreName);
        assertEquals("Unexpected peersOnly attribute value", initialPeersOnly, trustStore.get(FileTrustStore.PEERS_ONLY));

        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(TrustStore.NAME, trustStoreName);
        attributes.put(FileTrustStore.PEERS_ONLY, updatedPeersOnly);
        responseCode = getRestTestHelper().submitRequest("truststore/" + trustStoreName, "PUT", attributes);
        assertEquals("Setting of truststore attributes should be denied", 403, responseCode);

        trustStore = getRestTestHelper().getJsonAsSingletonList("truststore/" + trustStoreName);
        assertEquals("Unexpected peersOnly attribute value", initialPeersOnly, trustStore.get(FileTrustStore.PEERS_ONLY));
    }

    /* === Broker === */

    public void testSetBrokerAttributesAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        int initialSessionCountLimit = 256;
        int updatedSessionCountLimit = 299;

        Map<String, Object> brokerAttributes = getRestTestHelper().getJsonAsSingletonList("broker");
        assertEquals("Unexpected alert repeat gap", initialSessionCountLimit,
                brokerAttributes.get(Broker.CONNECTION_SESSION_COUNT_LIMIT));

        Map<String, Object> newAttributes = new HashMap<String, Object>();
        newAttributes.put(Broker.CONNECTION_SESSION_COUNT_LIMIT, updatedSessionCountLimit);

        int responseCode = getRestTestHelper().submitRequest("broker", "PUT", newAttributes);
        assertEquals("Setting of port attribites should be allowed", 200, responseCode);

        brokerAttributes = getRestTestHelper().getJsonAsSingletonList("broker");
        assertEquals("Unexpected default alert repeat gap", updatedSessionCountLimit,
                brokerAttributes.get(Broker.CONNECTION_SESSION_COUNT_LIMIT));
    }

    public void testSetBrokerAttributesDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        int initialSessionCountLimit = 256;
        int updatedSessionCountLimit = 299;

        Map<String, Object> brokerAttributes = getRestTestHelper().getJsonAsSingletonList("broker");
        assertEquals("Unexpected alert repeat gap", initialSessionCountLimit,
                brokerAttributes.get(Broker.CONNECTION_SESSION_COUNT_LIMIT));

        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);
        Map<String, Object> newAttributes = new HashMap<String, Object>();
        newAttributes.put(Broker.CONNECTION_SESSION_COUNT_LIMIT, updatedSessionCountLimit);

        int responseCode = getRestTestHelper().submitRequest("broker", "PUT", newAttributes);
        assertEquals("Setting of port attribites should be allowed", 403, responseCode);

        brokerAttributes = getRestTestHelper().getJsonAsSingletonList("broker");
        assertEquals("Unexpected default alert repeat gap", initialSessionCountLimit,
                brokerAttributes.get(Broker.CONNECTION_SESSION_COUNT_LIMIT));
    }

    /* === GroupProvider === */

    public void testCreateGroupProviderAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String groupProviderName = getTestName();

        assertGroupProviderExistence(groupProviderName, false);

        int responseCode = createGroupProvider(groupProviderName);
        assertEquals("Group provider creation should be allowed", 201, responseCode);

        assertGroupProviderExistence(groupProviderName, true);
    }

    public void testCreateGroupProviderDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        String groupProviderName = getTestName();

        assertGroupProviderExistence(groupProviderName, false);

        int responseCode = createGroupProvider(groupProviderName);
        assertEquals("Group provider creation should be denied", 403, responseCode);

        assertGroupProviderExistence(groupProviderName, false);
    }

    public void testDeleteGroupProviderDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String groupProviderName = getTestName();

        assertGroupProviderExistence(groupProviderName, false);

        int responseCode = createGroupProvider(groupProviderName);
        assertEquals("Group provider creation should be allowed", 201, responseCode);

        assertGroupProviderExistence(groupProviderName, true);

        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        responseCode = getRestTestHelper().submitRequest("groupprovider/" + groupProviderName, "DELETE");
        assertEquals("Group provider deletion should be denied", 403, responseCode);

        assertGroupProviderExistence(groupProviderName, true);
    }

    public void testDeleteGroupProviderAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String groupProviderName = getTestName();

        assertGroupProviderExistence(groupProviderName, false);

        int responseCode = createGroupProvider(groupProviderName);
        assertEquals("Group provider creation should be allowed", 201, responseCode);

        assertGroupProviderExistence(groupProviderName, true);

        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        responseCode = getRestTestHelper().submitRequest("groupprovider/" + groupProviderName, "DELETE");
        assertEquals("Group provider deletion should be allowed", 200, responseCode);

        assertGroupProviderExistence(groupProviderName, false);
    }

    public void testSetGroupProviderAttributesAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String groupProviderName = getTestName();

        assertGroupProviderExistence(groupProviderName, false);

        int responseCode = createGroupProvider(groupProviderName);
        assertEquals("Group provider creation should be allowed", 201, responseCode);

        assertGroupProviderExistence(groupProviderName, true);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(GroupProvider.NAME, groupProviderName);
        attributes.put(GroupProvider.TYPE, FileBasedGroupProviderImpl.GROUP_FILE_PROVIDER_TYPE);
        attributes.put(FileBasedGroupProvider.PATH, "/path/to/file");
        responseCode = getRestTestHelper().submitRequest("groupprovider/" + groupProviderName, "PUT", attributes);
        assertEquals("Setting of group provider attributes should be allowed but not supported", 409, responseCode);
    }

    public void testSetGroupProviderAttributesDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String groupProviderName = getTestName();

        assertGroupProviderExistence(groupProviderName, false);

        int responseCode = createGroupProvider(groupProviderName);
        assertEquals("Group provider creation should be allowed", 201, responseCode);

        assertGroupProviderExistence(groupProviderName, true);

        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(GroupProvider.NAME, groupProviderName);
        attributes.put(GroupProvider.TYPE, FileBasedGroupProviderImpl.GROUP_FILE_PROVIDER_TYPE);
        attributes.put(FileBasedGroupProvider.PATH, "/path/to/file");
        responseCode = getRestTestHelper().submitRequest("groupprovider/" + groupProviderName, "PUT", attributes);
        assertEquals("Setting of group provider attributes should be denied", 403, responseCode);
    }

    /* === AccessControlProvider === */


    public void testCreateAccessControlProviderDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        String accessControlProviderName = getTestName();

        assertAccessControlProviderExistence(accessControlProviderName, false);

        int responseCode = createAccessControlProvider(accessControlProviderName);
        assertEquals("Access control provider creation should be denied", 403, responseCode);

        assertAccessControlProviderExistence(accessControlProviderName, false);
    }

    public void testDeleteAccessControlProviderDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String accessControlProviderName = TestBrokerConfiguration.ENTRY_NAME_ACL_FILE;

        assertAccessControlProviderExistence(accessControlProviderName, true);

        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        int responseCode = getRestTestHelper().submitRequest("accesscontrolprovider/" + accessControlProviderName, "DELETE");
        assertEquals("Access control provider deletion should be denied", 403, responseCode);

        assertAccessControlProviderExistence(accessControlProviderName, true);
    }

    public void testDeleteAccessControlProviderAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String accessControlProviderName = TestBrokerConfiguration.ENTRY_NAME_ACL_FILE;


        assertAccessControlProviderExistence(accessControlProviderName, true);

        int responseCode = getRestTestHelper().submitRequest("accesscontrolprovider/" + accessControlProviderName, "DELETE");
        assertEquals("Access control provider deletion should be allowed", 200, responseCode);

        assertAccessControlProviderExistence(accessControlProviderName, false);
    }

    public void testSetAccessControlProviderAttributesAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String accessControlProviderName = TestBrokerConfiguration.ENTRY_NAME_ACL_FILE;

        assertAccessControlProviderExistence(accessControlProviderName, true);

        File aclFile = TestFileUtils.createTempFile(this, ".acl", "ACL ALLOW all all");

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AccessControlProvider.NAME, accessControlProviderName);
        attributes.put(FileBasedGroupProvider.PATH, aclFile.getAbsolutePath());
        int responseCode = getRestTestHelper().submitRequest("accesscontrolprovider/" + accessControlProviderName, "PUT", attributes);
        assertEquals("Setting of access control provider attributes should be allowed", 200, responseCode);
    }

    public void testSetAccessControlProviderAttributesDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        String accessControlProviderName = TestBrokerConfiguration.ENTRY_NAME_ACL_FILE;

        assertAccessControlProviderExistence(accessControlProviderName, true);

        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(GroupProvider.NAME, accessControlProviderName);
        attributes.put(GroupProvider.TYPE, FileBasedGroupProviderImpl.GROUP_FILE_PROVIDER_TYPE);
        attributes.put(FileBasedGroupProvider.PATH, "/path/to/file");
        int responseCode = getRestTestHelper().submitRequest("accesscontrolprovider/" + accessControlProviderName, "PUT", attributes);
        assertEquals("Setting of access control provider attributes should be denied", 403, responseCode);
    }

    /* === HTTP management === */

    public void testSetHttpManagementAttributesAllowed() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(ALLOWED_USER, ALLOWED_USER);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(HttpManagement.NAME, TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT);
        attributes.put(HttpManagement.HTTPS_BASIC_AUTHENTICATION_ENABLED, false);
        attributes.put(HttpManagement.HTTPS_SASL_AUTHENTICATION_ENABLED, false);
        attributes.put(HttpManagement.HTTP_SASL_AUTHENTICATION_ENABLED, false);
        attributes.put(HttpManagement.TIME_OUT, 10000);

        int responseCode = getRestTestHelper().submitRequest(
                "plugin/" + TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT, "PUT", attributes);
        assertEquals("Setting of http management should be allowed", 200, responseCode);

        Map<String, Object> details = getRestTestHelper().getJsonAsSingletonList(
                "plugin/" + TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT);

        assertEquals("Unexpected session timeout", 10000, details.get(HttpManagement.TIME_OUT));
        assertEquals("Unexpected http basic auth enabled", true, details.get(HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED));
        assertEquals("Unexpected https basic auth enabled", false, details.get(HttpManagement.HTTPS_BASIC_AUTHENTICATION_ENABLED));
        assertEquals("Unexpected http sasl auth enabled", false, details.get(HttpManagement.HTTP_SASL_AUTHENTICATION_ENABLED));
        assertEquals("Unexpected https sasl auth enabled", false, details.get(HttpManagement.HTTPS_SASL_AUTHENTICATION_ENABLED));
    }

    public void testSetHttpManagementAttributesDenied() throws Exception
    {
        getRestTestHelper().setUsernameAndPassword(DENIED_USER, DENIED_USER);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(HttpManagement.NAME, TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT);
        attributes.put(HttpManagement.HTTPS_BASIC_AUTHENTICATION_ENABLED, false);
        attributes.put(HttpManagement.HTTPS_SASL_AUTHENTICATION_ENABLED, false);
        attributes.put(HttpManagement.HTTP_SASL_AUTHENTICATION_ENABLED, false);
        attributes.put(HttpManagement.TIME_OUT, 10000);

        int responseCode = getRestTestHelper().submitRequest(
                "plugin/" + TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT, "PUT", attributes);
        assertEquals("Setting of http management should be denied", 403, responseCode);

        Map<String, Object> details = getRestTestHelper().getJsonAsSingletonList(
                "plugin/" + TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT);

        assertEquals("Unexpected session timeout", HttpManagement.DEFAULT_TIMEOUT_IN_SECONDS,
                details.get(HttpManagement.TIME_OUT));
        assertEquals("Unexpected http basic auth enabled", true,
                details.get(HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED));
        assertEquals("Unexpected https basic auth enabled", true,
                details.get(HttpManagement.HTTPS_BASIC_AUTHENTICATION_ENABLED));
        assertEquals("Unexpected http sasl auth enabled", true,
                details.get(HttpManagement.HTTP_SASL_AUTHENTICATION_ENABLED));
        assertEquals("Unexpected https sasl auth enabled", true,
                details.get(HttpManagement.HTTPS_SASL_AUTHENTICATION_ENABLED));
    }

    /* === Utility Methods === */

    private int createPort(String portName) throws Exception
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.NAME, portName);
        attributes.put(Port.PORT, findFreePort());
        attributes.put(Port.AUTHENTICATION_PROVIDER, TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER);

        return getRestTestHelper().submitRequest("port/" + portName, "PUT", attributes);
    }

    private void assertPortExists(String portName) throws Exception
    {
        assertPortExistence(portName, true);
    }

    private void assertPortDoesNotExist(String portName) throws Exception
    {
        assertPortExistence(portName, false);
    }

    private void assertPortExistence(String portName, boolean exists) throws Exception
    {
        List<Map<String, Object>> hosts = getRestTestHelper().getJsonAsList("port/" + portName);
        assertEquals("Unexpected result", exists, !hosts.isEmpty());
    }

    private void assertKeyStoreExistence(String keyStoreName, boolean exists) throws Exception
    {
        List<Map<String, Object>> keyStores = getRestTestHelper().getJsonAsList("keystore/" + keyStoreName);
        assertEquals("Unexpected result", exists, !keyStores.isEmpty());
    }

    private void assertTrustStoreExistence(String trustStoreName, boolean exists) throws Exception
    {
        List<Map<String, Object>> trustStores = getRestTestHelper().getJsonAsList("truststore/" + trustStoreName);
        assertEquals("Unexpected result", exists, !trustStores.isEmpty());
    }

    private int createAuthenticationProvider(String authenticationProviderName) throws Exception
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.NAME, authenticationProviderName);
        attributes.put(AuthenticationProvider.TYPE, AnonymousAuthenticationManager.PROVIDER_TYPE);

        return getRestTestHelper().submitRequest("authenticationprovider/" + authenticationProviderName, "PUT", attributes);
    }

    private void assertAuthenticationProviderDoesNotExist(String authenticationProviderName) throws Exception
    {
        assertAuthenticationProviderExistence(authenticationProviderName, false);
    }

    private void assertAuthenticationProviderExists(String authenticationProviderName) throws Exception
    {
        assertAuthenticationProviderExistence(authenticationProviderName, true);
    }

    private void assertAuthenticationProviderExistence(String authenticationProviderName, boolean exists) throws Exception
    {
        String path = "authenticationprovider/" + authenticationProviderName;
        List<Map<String, Object>> providers = getRestTestHelper().getJsonAsList(path);
        assertEquals("Unexpected result", exists, !providers.isEmpty());
    }

    private int createKeyStore(String name, String certAlias) throws IOException, JsonGenerationException, JsonMappingException
    {
        Map<String, Object> keyStoreAttributes = new HashMap<String, Object>();
        keyStoreAttributes.put(KeyStore.NAME, name);
        keyStoreAttributes.put(FileKeyStore.PATH, TestSSLConstants.KEYSTORE);
        keyStoreAttributes.put(FileKeyStore.PASSWORD, TestSSLConstants.KEYSTORE_PASSWORD);
        keyStoreAttributes.put(FileKeyStore.CERTIFICATE_ALIAS, certAlias);

        return getRestTestHelper().submitRequest("keystore/" + name, "PUT", keyStoreAttributes);
    }

    private int createTrustStore(String name, boolean peersOnly) throws IOException, JsonGenerationException, JsonMappingException
    {
        Map<String, Object> trustStoreAttributes = new HashMap<String, Object>();
        trustStoreAttributes.put(TrustStore.NAME, name);
        trustStoreAttributes.put(FileTrustStore.PATH, TestSSLConstants.KEYSTORE);
        trustStoreAttributes.put(FileTrustStore.PASSWORD, TestSSLConstants.KEYSTORE_PASSWORD);
        trustStoreAttributes.put(FileTrustStore.PEERS_ONLY, peersOnly);

        return getRestTestHelper().submitRequest("truststore/" + name, "PUT", trustStoreAttributes);
    }

    private void assertGroupProviderExistence(String groupProviderName, boolean exists) throws Exception
    {
        String path = "groupprovider/" + groupProviderName;
        List<Map<String, Object>> providers = getRestTestHelper().getJsonAsList(path);
        assertEquals("Unexpected result", exists, !providers.isEmpty());
    }

    private int createGroupProvider(String groupProviderName) throws Exception
    {
        File file = TestFileUtils.createTempFile(this, ".groups");
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(GroupProvider.NAME, groupProviderName);
        attributes.put(GroupProvider.TYPE, FileBasedGroupProviderImpl.GROUP_FILE_PROVIDER_TYPE);
        attributes.put(FileBasedGroupProvider.PATH, file.getAbsoluteFile());

        return getRestTestHelper().submitRequest("groupprovider/" + groupProviderName, "PUT", attributes);
    }

    private void assertAccessControlProviderExistence(String accessControlProviderName, boolean exists) throws Exception
    {
        String path = "accesscontrolprovider/" + accessControlProviderName;
        List<Map<String, Object>> providers = getRestTestHelper().getJsonAsList(path);
        assertEquals("Unexpected result", exists, !providers.isEmpty());
    }

    private int createAccessControlProvider(String accessControlProviderName) throws Exception
    {
        File file = TestFileUtils.createTempFile(this, ".acl", _secondaryAclFileContent);
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AccessControlProvider.NAME, accessControlProviderName);
        attributes.put(AccessControlProvider.TYPE, FileAccessControlProviderConstants.ACL_FILE_PROVIDER_TYPE);
        attributes.put(FileAccessControlProviderConstants.PATH, file.getAbsoluteFile());

        return getRestTestHelper().submitRequest("accesscontrolprovider/" + accessControlProviderName, "PUT", attributes);
    }
}
