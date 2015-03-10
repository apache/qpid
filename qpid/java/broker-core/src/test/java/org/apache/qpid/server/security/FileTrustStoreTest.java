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
 */

package org.apache.qpid.server.security;


import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.TrustManager;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.IntegrityViolationException;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.auth.manager.SimpleLDAPAuthenticationManager;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestSSLConstants;
import org.apache.qpid.transport.network.security.ssl.QpidMultipleTrustManager;
import org.apache.qpid.util.DataUrlUtils;
import org.apache.qpid.util.FileUtils;

public class FileTrustStoreTest extends QpidTestCase
{
    private final Broker _broker = mock(Broker.class);
    private final TaskExecutor _taskExecutor = CurrentThreadTaskExecutor.newStartedInstance();
    private final SecurityManager _securityManager = mock(SecurityManager.class);
    private final Model _model = BrokerModel.getInstance();
    private final ConfiguredObjectFactory _factory = _model.getObjectFactory();

    public void setUp() throws Exception
    {
        super.setUp();

        when(_broker.getTaskExecutor()).thenReturn(_taskExecutor);
        when(_broker.getModel()).thenReturn(_model);
        when(_broker.getSecurityManager()).thenReturn(_securityManager);
        when(_broker.getCategoryClass()).thenReturn(Broker.class);
    }

    public void testCreateTrustStoreFromFile_Success() throws Exception
    {
        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileTrustStore.NAME, "myFileTrustStore");
        attributes.put(FileTrustStore.STORE_URL, TestSSLConstants.TRUSTSTORE);
        attributes.put(FileTrustStore.PASSWORD, TestSSLConstants.TRUSTSTORE_PASSWORD);

        FileTrustStoreImpl fileTrustStore =
                (FileTrustStoreImpl) _factory.create(TrustStore.class, attributes,  _broker);

        TrustManager[] trustManagers = fileTrustStore.getTrustManagers();
        assertNotNull(trustManagers);
        assertEquals("Unexpected number of trust managers", 1, trustManagers.length);
        assertNotNull("Trust manager unexpected null", trustManagers[0]);
    }

    public void testCreateTrustStoreFromFile_WrongPassword() throws Exception
    {
        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileTrustStore.NAME, "myFileTrustStore");
        attributes.put(FileTrustStore.STORE_URL, TestSSLConstants.TRUSTSTORE);
        attributes.put(FileTrustStore.PASSWORD, "wrong");

        try
        {
            _factory.create(TrustStore.class, attributes,  _broker);
            fail("Exception not thrown");
        }
        catch (IllegalConfigurationException ice)
        {
            String message = ice.getMessage();
            assertTrue("Exception text not as unexpected:" + message, message.contains("Check trust store password"));
        }
    }

    public void testCreatePeersOnlyTrustStoreFromFile_Success() throws Exception
    {
        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileTrustStore.NAME, "myFileTrustStore");
        attributes.put(FileTrustStore.STORE_URL, TestSSLConstants.BROKER_PEERSTORE);
        attributes.put(FileTrustStore.PASSWORD, TestSSLConstants.BROKER_PEERSTORE_PASSWORD);
        attributes.put(FileTrustStore.PEERS_ONLY, true);

        FileTrustStoreImpl fileTrustStore =
                (FileTrustStoreImpl) _factory.create(TrustStore.class, attributes,  _broker);

        TrustManager[] trustManagers = fileTrustStore.getTrustManagers();
        assertNotNull(trustManagers);
        assertEquals("Unexpected number of trust managers", 1, trustManagers.length);
        assertNotNull("Trust manager unexpected null", trustManagers[0]);
        assertTrue("Trust manager unexpected null", trustManagers[0] instanceof QpidMultipleTrustManager);
    }


    public void testCreateTrustStoreFromDataUrl_Success() throws Exception
    {
        String trustStoreAsDataUrl = createDataUrlForFile(TestSSLConstants.TRUSTSTORE);

        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileTrustStore.NAME, "myFileTrustStore");
        attributes.put(FileTrustStore.STORE_URL, trustStoreAsDataUrl);
        attributes.put(FileTrustStore.PASSWORD, TestSSLConstants.TRUSTSTORE_PASSWORD);

        FileTrustStoreImpl fileTrustStore =
                (FileTrustStoreImpl) _factory.create(TrustStore.class, attributes,  _broker);

        TrustManager[] trustManagers = fileTrustStore.getTrustManagers();
        assertNotNull(trustManagers);
        assertEquals("Unexpected number of trust managers", 1, trustManagers.length);
        assertNotNull("Trust manager unexpected null", trustManagers[0]);
    }

    public void testCreateTrustStoreFromDataUrl_WrongPassword() throws Exception
    {
        String trustStoreAsDataUrl = createDataUrlForFile(TestSSLConstants.TRUSTSTORE);

        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileTrustStore.NAME, "myFileTrustStore");
        attributes.put(FileTrustStore.PASSWORD, "wrong");
        attributes.put(FileTrustStore.STORE_URL, trustStoreAsDataUrl);

        try
        {
            _factory.create(TrustStore.class, attributes,  _broker);
            fail("Exception not thrown");
        }
        catch (IllegalConfigurationException ice)
        {
            String message = ice.getMessage();
            assertTrue("Exception text not as unexpected:" + message, message.contains("Check trust store password"));
        }
    }

    public void testCreateTrustStoreFromDataUrl_BadTruststoreBytes() throws Exception
    {
        String trustStoreAsDataUrl = DataUrlUtils.getDataUrlForBytes("notatruststore".getBytes());

        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileTrustStore.NAME, "myFileTrustStore");
        attributes.put(FileTrustStore.PASSWORD, TestSSLConstants.TRUSTSTORE_PASSWORD);
        attributes.put(FileTrustStore.STORE_URL, trustStoreAsDataUrl);

        try
        {
            _factory.create(TrustStore.class, attributes,  _broker);
            fail("Exception not thrown");
        }
        catch (IllegalConfigurationException ice)
        {
            String message = ice.getMessage();
            assertTrue("Exception text not as unexpected:" + message, message.contains("Cannot instantiate trust store"));

        }
    }

    public void testUpdateTrustStore_Success() throws Exception
    {
        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileTrustStore.NAME, "myFileTrustStore");
        attributes.put(FileTrustStore.STORE_URL, TestSSLConstants.TRUSTSTORE);
        attributes.put(FileTrustStore.PASSWORD, TestSSLConstants.TRUSTSTORE_PASSWORD);

        FileTrustStoreImpl fileTrustStore =
                (FileTrustStoreImpl) _factory.create(TrustStore.class, attributes,  _broker);

        assertEquals("Unexpected path value before change", TestSSLConstants.TRUSTSTORE, fileTrustStore.getStoreUrl());

        try
        {
            Map<String,Object> unacceptableAttributes = new HashMap<>();
            unacceptableAttributes.put(FileTrustStore.STORE_URL, "/not/a/truststore");

            fileTrustStore.setAttributes(unacceptableAttributes);
            fail("Exception not thrown");
        }
        catch (IllegalConfigurationException ice)
        {
            String message = ice.getMessage();
            assertTrue("Exception text not as unexpected:" + message, message.contains("Cannot instantiate trust store"));
        }

        assertEquals("Unexpected path value after failed change", TestSSLConstants.TRUSTSTORE, fileTrustStore.getStoreUrl());

        Map<String,Object> changedAttributes = new HashMap<>();
        changedAttributes.put(FileTrustStore.STORE_URL, TestSSLConstants.BROKER_TRUSTSTORE);
        changedAttributes.put(FileTrustStore.PASSWORD, TestSSLConstants.BROKER_TRUSTSTORE_PASSWORD);

        fileTrustStore.setAttributes(changedAttributes);

        assertEquals("Unexpected path value after change that is expected to be successful",
                     TestSSLConstants.BROKER_TRUSTSTORE,
                     fileTrustStore.getStoreUrl());
    }

    public void testDeleteTrustStore_Success() throws Exception
    {
        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileTrustStore.NAME, "myFileTrustStore");
        attributes.put(FileTrustStore.STORE_URL, TestSSLConstants.TRUSTSTORE);
        attributes.put(FileTrustStore.PASSWORD, TestSSLConstants.TRUSTSTORE_PASSWORD);

        FileTrustStoreImpl fileTrustStore =
                (FileTrustStoreImpl) _factory.create(TrustStore.class, attributes,  _broker);

        fileTrustStore.delete();
    }

    public void testDeleteTrustStore_TrustManagerInUseByAuthProvider() throws Exception
    {
        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileTrustStore.NAME, "myFileTrustStore");
        attributes.put(FileTrustStore.STORE_URL, TestSSLConstants.TRUSTSTORE);
        attributes.put(FileTrustStore.PASSWORD, TestSSLConstants.TRUSTSTORE_PASSWORD);

        FileTrustStoreImpl fileTrustStore =
                (FileTrustStoreImpl) _factory.create(TrustStore.class, attributes,  _broker);

        SimpleLDAPAuthenticationManager ldap = mock(SimpleLDAPAuthenticationManager.class);
        when(ldap.getTrustStore()).thenReturn(fileTrustStore);

        Collection<AuthenticationProvider<?>> authenticationProviders = Collections.<AuthenticationProvider<?>>singletonList(ldap);
        when(_broker.getAuthenticationProviders()).thenReturn(authenticationProviders);

        try
        {
            fileTrustStore.delete();
            fail("Exception not thrown");
        }
        catch (IntegrityViolationException ive)
        {
            // PASS
        }
    }

    public void testDeleteTrustStore_TrustManagerInUseByPort() throws Exception
    {
        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileTrustStore.NAME, "myFileTrustStore");
        attributes.put(FileTrustStore.STORE_URL, TestSSLConstants.TRUSTSTORE);
        attributes.put(FileTrustStore.PASSWORD, TestSSLConstants.TRUSTSTORE_PASSWORD);

        FileTrustStoreImpl fileTrustStore =
                (FileTrustStoreImpl) _factory.create(TrustStore.class, attributes,  _broker);

        Port<?> port = mock(Port.class);
        when(port.getTrustStores()).thenReturn(Collections.<TrustStore>singletonList(fileTrustStore));

        when(_broker.getPorts()).thenReturn(Collections.<Port<?>>singletonList(port));

        try
        {
            fileTrustStore.delete();
            fail("Exception not thrown");
        }
        catch (IntegrityViolationException ive)
        {
            // PASS
        }
    }

    private static String createDataUrlForFile(String filename)
    {
        byte[] fileAsBytes = FileUtils.readFileAsBytes(filename);
        return DataUrlUtils.getDataUrlForBytes(fileAsBytes);
    }
}