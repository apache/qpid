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
package org.apache.qpid.server.configuration.startup;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.KeyManagerFactory;
import javax.security.auth.Subject;

import junit.framework.TestCase;

import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.ConfiguredObjectFactoryImpl;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.security.FileKeyStore;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.test.utils.TestSSLConstants;

public class FileKeyStoreCreationTest extends TestCase
{

    private ConfiguredObjectFactory _factory;

    public void setUp() throws Exception
    {
        super.setUp();
        _factory = new ConfiguredObjectFactoryImpl(BrokerModel.getInstance());
    }

    public void testCreateWithAllAttributesProvided()
    {
        Map<String, Object> attributes = getKeyStoreAttributes();
        Map<String, Object> attributesCopy = new HashMap<String, Object>(attributes);

        Broker broker = mock(Broker.class);
        TaskExecutor executor = CurrentThreadTaskExecutor.newStartedInstance();
        when(broker.getObjectFactory()).thenReturn(_factory);
        when(broker.getModel()).thenReturn(_factory.getModel());
        when(broker.getTaskExecutor()).thenReturn(executor);

        final FileKeyStore keyStore =
                createKeyStore(attributes, broker);


        assertNotNull("Key store configured object is not created", keyStore);
        assertEquals(attributes.get(ConfiguredObject.ID), keyStore.getId());

        //verify we can retrieve the actual password using the method
        Subject.doAs(SecurityManager.getSubjectWithAddedSystemRights(), new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
                assertNotNull(keyStore.getPassword());
                assertEquals(TestSSLConstants.BROKER_TRUSTSTORE_PASSWORD, keyStore.getPassword());
                //verify that we haven't configured the key store with the actual dummy password value
                assertFalse(AbstractConfiguredObject.SECURED_STRING_VALUE.equals(keyStore.getPassword()));
                return null;
            }
        });


        // Verify the remaining attributes, including that the password value returned
        // via getAttribute is actually the dummy value and not the real password
        attributesCopy.put(FileKeyStore.PASSWORD, AbstractConfiguredObject.SECURED_STRING_VALUE);
        for (Map.Entry<String, Object> attribute : attributesCopy.entrySet())
        {
            Object attributeValue = keyStore.getAttribute(attribute.getKey());
            assertEquals("Unexpected value of attribute '" + attribute.getKey() + "'", attribute.getValue(), attributeValue);
        }
    }

    protected FileKeyStore createKeyStore(final Map<String, Object> attributes, final Broker broker)
    {
        return (FileKeyStore) _factory.create(KeyStore.class,attributes, broker);
    }

    public void testCreateWithMissedRequiredAttributes()
    {
        Map<String, Object> attributes = getKeyStoreAttributes();

        Broker broker = mock(Broker.class);
        when(broker.getObjectFactory()).thenReturn(_factory);
        when(broker.getModel()).thenReturn(_factory.getModel());
        when(broker.getTaskExecutor()).thenReturn(CurrentThreadTaskExecutor.newStartedInstance());
        String[] mandatoryProperties = {KeyStore.NAME, FileKeyStore.PATH, FileKeyStore.PASSWORD};
        for (int i = 0; i < mandatoryProperties.length; i++)
        {
            Map<String, Object> properties =  new HashMap<String, Object>(attributes);
            properties.remove(mandatoryProperties[i]);
            try
            {
                createKeyStore(properties, broker);
                fail("Cannot create key store without a " + mandatoryProperties[i]);
            }
            catch(IllegalArgumentException e)
            {
                // pass
            }
        }
    }

    private Map<String, Object> getKeyStoreAttributes()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(KeyStore.ID, UUID.randomUUID());
        attributes.put(KeyStore.NAME, getName());
        attributes.put(FileKeyStore.PATH, TestSSLConstants.BROKER_KEYSTORE);
        attributes.put(FileKeyStore.PASSWORD, TestSSLConstants.BROKER_KEYSTORE_PASSWORD);
        attributes.put(FileKeyStore.KEY_STORE_TYPE, "jks");
        attributes.put(FileKeyStore.KEY_MANAGER_FACTORY_ALGORITHM, KeyManagerFactory.getDefaultAlgorithm());
        attributes.put(FileKeyStore.CERTIFICATE_ALIAS, "java-broker");
        return attributes;
    }


}
