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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.KeyManager;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.IntegrityViolationException;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestSSLConstants;
import org.apache.qpid.util.DataUrlUtils;
import org.apache.qpid.util.FileUtils;

public class FileKeyStoreTest extends QpidTestCase
{
    private final Broker<?> _broker = mock(Broker.class);
    private final CurrentThreadTaskExecutor _taskExecutor = new CurrentThreadTaskExecutor();
    private final SecurityManager _securityManager = mock(SecurityManager.class);

    public void setUp() throws Exception
    {
        super.setUp();

        _taskExecutor.start();
        when(_broker.getTaskExecutor()).thenReturn(_taskExecutor);
        when(_broker.getModel()).thenReturn(BrokerModel.getInstance());

        when(_broker.getSecurityManager()).thenReturn(_securityManager);
    }

    public void testCreateKeyStoreFromFile_Success() throws Exception
    {
        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileKeyStore.NAME, "myFileKeyStore");
        attributes.put(FileKeyStore.PATH, TestSSLConstants.BROKER_KEYSTORE);
        attributes.put(FileKeyStore.PASSWORD, TestSSLConstants.BROKER_KEYSTORE_PASSWORD);

        FileKeyStoreImpl fileKeyStore = new FileKeyStoreImpl(attributes, _broker);

        fileKeyStore.create();

        KeyManager[] keyManager = fileKeyStore.getKeyManagers();
        assertNotNull(keyManager);
        assertEquals("Unexpected number of key managers", 1, keyManager.length);
        assertNotNull("Key manager unexpected null", keyManager[0]);
    }

    public void testCreateKeyStoreWithAliasFromFile_Success() throws Exception
    {
        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileKeyStore.NAME, "myFileKeyStore");
        attributes.put(FileKeyStore.PATH, TestSSLConstants.BROKER_KEYSTORE);
        attributes.put(FileKeyStore.PASSWORD, TestSSLConstants.BROKER_KEYSTORE_PASSWORD);
        attributes.put(FileKeyStore.CERTIFICATE_ALIAS, TestSSLConstants.BROKER_KEYSTORE_ALIAS);

        FileKeyStoreImpl fileKeyStore = new FileKeyStoreImpl(attributes, _broker);

        fileKeyStore.create();

        KeyManager[] keyManager = fileKeyStore.getKeyManagers();
        assertNotNull(keyManager);
        assertEquals("Unexpected number of key managers", 1, keyManager.length);
        assertNotNull("Key manager unexpected null", keyManager[0]);
    }

    public void testCreateKeyStoreFromFile_WrongPassword() throws Exception
    {
        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileKeyStore.NAME, "myFileKeyStore");
        attributes.put(FileKeyStore.PATH, TestSSLConstants.BROKER_KEYSTORE);
        attributes.put(FileKeyStore.PASSWORD, "wrong");

        FileKeyStoreImpl fileKeyStore = new FileKeyStoreImpl(attributes, _broker);

        try
        {
            fileKeyStore.create();
            fail("Exception not thrown");
        }
        catch (IllegalConfigurationException ice)
        {
            String message = ice.getMessage();
            assertTrue("Exception text not as unexpected:" + message, message.contains("Check key store password"));
        }
    }

    public void testCreateKeyStoreFromFile_UnknownAlias() throws Exception
    {
        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileKeyStore.NAME, "myFileKeyStore");
        attributes.put(FileKeyStore.PATH, TestSSLConstants.KEYSTORE);
        attributes.put(FileKeyStore.PASSWORD, TestSSLConstants.KEYSTORE_PASSWORD);
        attributes.put(FileKeyStore.CERTIFICATE_ALIAS, "notknown");

        FileKeyStoreImpl fileKeyStore = new FileKeyStoreImpl(attributes, _broker);

        try
        {
            fileKeyStore.create();
            fail("Exception not thrown");
        }
        catch (IllegalConfigurationException ice)
        {
            String message = ice.getMessage();
            assertTrue("Exception text not as unexpected:" + message, message.contains("Cannot find a certificate with alias 'notknown' in key store"));
        }
    }

    public void testCreateKeyStoreFromDataUrl_Success() throws Exception
    {
        String trustStoreAsDataUrl = createDataUrlForFile(TestSSLConstants.BROKER_KEYSTORE);

        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileKeyStore.NAME, "myFileKeyStore");
        attributes.put(FileKeyStore.PATH, trustStoreAsDataUrl);
        attributes.put(FileKeyStore.PASSWORD, TestSSLConstants.BROKER_KEYSTORE_PASSWORD);

        FileKeyStoreImpl fileKeyStore = new FileKeyStoreImpl(attributes, _broker);

        fileKeyStore.create();

        KeyManager[] keyManagers = fileKeyStore.getKeyManagers();
        assertNotNull(keyManagers);
        assertEquals("Unexpected number of key managers", 1, keyManagers.length);
        assertNotNull("Key manager unexpected null", keyManagers[0]);
    }

    public void testCreateKeyStoreWithAliasFromDataUrl_Success() throws Exception
    {
        String trustStoreAsDataUrl = createDataUrlForFile(TestSSLConstants.BROKER_KEYSTORE);

        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileKeyStore.NAME, "myFileKeyStore");
        attributes.put(FileKeyStore.PATH, trustStoreAsDataUrl);
        attributes.put(FileKeyStore.PASSWORD, TestSSLConstants.BROKER_KEYSTORE_PASSWORD);
        attributes.put(FileKeyStore.CERTIFICATE_ALIAS, TestSSLConstants.BROKER_KEYSTORE_ALIAS);

        FileKeyStoreImpl fileKeyStore = new FileKeyStoreImpl(attributes, _broker);

        fileKeyStore.create();

        KeyManager[] keyManagers = fileKeyStore.getKeyManagers();
        assertNotNull(keyManagers);
        assertEquals("Unexpected number of key managers", 1, keyManagers.length);
        assertNotNull("Key manager unexpected null", keyManagers[0]);
    }

    public void testCreateKeyStoreFromDataUrl_WrongPassword() throws Exception
    {
        String keyStoreAsDataUrl = createDataUrlForFile(TestSSLConstants.BROKER_KEYSTORE);

        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileKeyStore.NAME, "myFileKeyStore");
        attributes.put(FileKeyStore.PASSWORD, "wrong");
        attributes.put(FileKeyStore.PATH, keyStoreAsDataUrl);

        FileKeyStoreImpl fileKeyStore = new FileKeyStoreImpl(attributes, _broker);

        try
        {

            fileKeyStore.create();
            fail("Exception not thrown");
        }
        catch (IllegalConfigurationException ice)
        {
            String message = ice.getMessage();
            assertTrue("Exception text not as unexpected:" + message, message.contains("Check key store password"));
        }
    }

    public void testCreateKeyStoreFromDataUrl_BadKeystoreBytes() throws Exception
    {
        String keyStoreAsDataUrl = DataUrlUtils.getDataUrlForBytes("notatruststore".getBytes());

        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileKeyStore.NAME, "myFileKeyStore");
        attributes.put(FileKeyStore.PASSWORD, TestSSLConstants.BROKER_KEYSTORE_PASSWORD);
        attributes.put(FileKeyStore.PATH, keyStoreAsDataUrl);

        FileKeyStoreImpl fileKeyStore = new FileKeyStoreImpl(attributes, _broker);

        try
        {
            fileKeyStore.create();
            fail("Exception not thrown");
        }
        catch (IllegalConfigurationException ice)
        {
            String message = ice.getMessage();
            assertTrue("Exception text not as unexpected:" + message, message.contains("Cannot instantiate key store"));

        }
    }

    public void testCreateKeyStoreFromDataUrl_UnknownAlias() throws Exception
    {
        String keyStoreAsDataUrl = createDataUrlForFile(TestSSLConstants.BROKER_KEYSTORE);

        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileKeyStore.NAME, "myFileKeyStore");
        attributes.put(FileKeyStore.PASSWORD, TestSSLConstants.BROKER_KEYSTORE_PASSWORD);
        attributes.put(FileKeyStore.PATH, keyStoreAsDataUrl);
        attributes.put(FileKeyStore.CERTIFICATE_ALIAS, "notknown");

        FileKeyStoreImpl fileKeyStore = new FileKeyStoreImpl(attributes, _broker);

        try
        {
            fileKeyStore.create();
            fail("Exception not thrown");
        }
        catch (IllegalConfigurationException ice)
        {
            String message = ice.getMessage();
            assertTrue("Exception text not as unexpected:" + message, message.contains("Cannot find a certificate with alias 'notknown' in key store"));
        }
    }

    public void testUpdateKeyStore_Success() throws Exception
    {

        when(_securityManager.authoriseConfiguringBroker(any(String.class), (Class<? extends ConfiguredObject>)any(), any(Operation.class))).thenReturn(true);

        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileKeyStore.NAME, "myFileKeyStore");
        attributes.put(FileKeyStore.PATH, TestSSLConstants.BROKER_KEYSTORE);
        attributes.put(FileKeyStore.PASSWORD, TestSSLConstants.BROKER_KEYSTORE_PASSWORD);

        FileKeyStoreImpl fileKeyStore = new FileKeyStoreImpl(attributes, _broker);

        fileKeyStore.create();

        assertNull("Unexpected alias value before change", fileKeyStore.getCertificateAlias());

        try
        {
            Map<String,Object> unacceptableAttributes = new HashMap<>();
            unacceptableAttributes.put(FileKeyStore.CERTIFICATE_ALIAS, "notknown");

            fileKeyStore.setAttributes(unacceptableAttributes);
            fail("Exception not thrown");
        }
        catch (IllegalConfigurationException ice)
        {
            String message = ice.getMessage();
            assertTrue("Exception text not as unexpected:" + message, message.contains("Cannot find a certificate with alias 'notknown' in key store"));
        }

        assertNull("Unexpected alias value after failed change", fileKeyStore.getCertificateAlias());

        Map<String,Object> changedAttributes = new HashMap<>();
        changedAttributes.put(FileKeyStore.CERTIFICATE_ALIAS, TestSSLConstants.BROKER_KEYSTORE_ALIAS);

        fileKeyStore.setAttributes(changedAttributes);

        assertEquals("Unexpected alias value after change that is expected to be successful",
                     TestSSLConstants.BROKER_KEYSTORE_ALIAS,
                     fileKeyStore.getCertificateAlias());

    }

    public void testDeleteKeyStore_Success() throws Exception
    {

        when(_securityManager.authoriseConfiguringBroker(any(String.class), (Class<? extends ConfiguredObject>)any(), any(Operation.class))).thenReturn(true);

        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileKeyStore.NAME, "myFileKeyStore");
        attributes.put(FileKeyStore.PATH, TestSSLConstants.BROKER_KEYSTORE);
        attributes.put(FileKeyStore.PASSWORD, TestSSLConstants.BROKER_KEYSTORE_PASSWORD);

        FileKeyStoreImpl fileKeyStore = new FileKeyStoreImpl(attributes, _broker);

        fileKeyStore.create();
        fileKeyStore.delete();
    }

    public void testDeleteKeyStore_KeyManagerInUseByPort() throws Exception
    {
        when(_securityManager.authoriseConfiguringBroker(any(String.class),
                                                         any(Class.class),
                                                         any(Operation.class))).thenReturn(true);

        Map<String,Object> attributes = new HashMap<>();
        attributes.put(FileKeyStore.NAME, "myFileKeyStore");
        attributes.put(FileKeyStore.PATH, TestSSLConstants.BROKER_KEYSTORE);
        attributes.put(FileKeyStore.PASSWORD, TestSSLConstants.BROKER_KEYSTORE_PASSWORD);

        FileKeyStoreImpl fileKeyStore = new FileKeyStoreImpl(attributes, _broker);

        fileKeyStore.create();

        Port<?> port = mock(Port.class);
        when(port.getKeyStore()).thenReturn(fileKeyStore);

        when(_broker.getPorts()).thenReturn(Collections.<Port<?>>singletonList(port));

        try
        {
            fileKeyStore.delete();
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