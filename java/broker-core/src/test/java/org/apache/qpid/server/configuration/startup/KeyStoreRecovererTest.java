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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.KeyManagerFactory;

import junit.framework.TestCase;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.adapter.AbstractKeyStoreAdapter;
import org.apache.qpid.test.utils.TestSSLConstants;

public class KeyStoreRecovererTest extends TestCase
{

    public void testCreateWithAllAttributesProvided()
    {
        Map<String, Object> attributes = getKeyStoreAttributes();
        Map<String, Object> attributesCopy = new HashMap<String, Object>(attributes);

        UUID id = UUID.randomUUID();
        Broker broker = mock(Broker.class);
        ConfigurationEntry entry = mock(ConfigurationEntry.class);
        when(entry.getAttributes()).thenReturn(attributes);
        when(entry.getId()).thenReturn(id);

        KeyStoreRecoverer recoverer = new KeyStoreRecoverer();

        KeyStore keyStore = recoverer.create(null, entry, broker);
        assertNotNull("Key store configured object is not created", keyStore);
        assertEquals(id, keyStore.getId());

        //verify we can retrieve the actual password using the method
        assertEquals(TestSSLConstants.BROKER_TRUSTSTORE_PASSWORD, keyStore.getPassword());
        assertNotNull(keyStore.getPassword());

        //verify that we haven't configured the key store with the actual dummy password value
        assertFalse(AbstractKeyStoreAdapter.DUMMY_PASSWORD_MASK.equals(keyStore.getPassword()));

        // Verify the remaining attributes, including that the password value returned
        // via getAttribute is actually the dummy value and not the real password
        attributesCopy.put(KeyStore.PASSWORD, AbstractKeyStoreAdapter.DUMMY_PASSWORD_MASK);
        for (Map.Entry<String, Object> attribute : attributesCopy.entrySet())
        {
            Object attributeValue = keyStore.getAttribute(attribute.getKey());
            assertEquals("Unexpected value of attribute '" + attribute.getKey() + "'", attribute.getValue(), attributeValue);
        }
    }

    public void testCreateWithMissedRequiredAttributes()
    {
        Map<String, Object> attributes = getKeyStoreAttributes();

        UUID id = UUID.randomUUID();
        Broker broker = mock(Broker.class);
        ConfigurationEntry entry = mock(ConfigurationEntry.class);
        when(entry.getId()).thenReturn(id);

        KeyStoreRecoverer recoverer = new KeyStoreRecoverer();

        String[] mandatoryProperties = {KeyStore.NAME, KeyStore.PATH, KeyStore.PASSWORD};
        for (int i = 0; i < mandatoryProperties.length; i++)
        {
            Map<String, Object> properties =  new HashMap<String, Object>(attributes);
            properties.remove(mandatoryProperties[i]);
            when(entry.getAttributes()).thenReturn(properties);
            try
            {
                recoverer.create(null, entry, broker);
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
        attributes.put(KeyStore.NAME, getName());
        attributes.put(KeyStore.PATH, TestSSLConstants.BROKER_KEYSTORE);
        attributes.put(KeyStore.PASSWORD, TestSSLConstants.BROKER_KEYSTORE_PASSWORD);
        attributes.put(KeyStore.KEY_STORE_TYPE, "jks");
        attributes.put(KeyStore.KEY_MANAGER_FACTORY_ALGORITHM, KeyManagerFactory.getDefaultAlgorithm());
        attributes.put(KeyStore.CERTIFICATE_ALIAS, "java-broker");
        return attributes;
    }

}
