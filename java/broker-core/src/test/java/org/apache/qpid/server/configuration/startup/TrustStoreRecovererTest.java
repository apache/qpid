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

import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.Subject;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.model.adapter.AbstractKeyStoreAdapter;
import org.apache.qpid.server.security.*;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestSSLConstants;

public class TrustStoreRecovererTest extends QpidTestCase
{
    public void testCreateWithAllAttributesProvided()
    {
        Map<String, Object> attributes = getTrustStoreAttributes();
        Map<String, Object> attributesCopy = new HashMap<String, Object>(attributes);

        UUID id = UUID.randomUUID();
        Broker broker = mock(Broker.class);
        ConfigurationEntry entry = mock(ConfigurationEntry.class);
        when(entry.getAttributes()).thenReturn(attributes);
        when(entry.getId()).thenReturn(id);

        TrustStoreRecoverer recoverer = new TrustStoreRecoverer();

        final TrustStore trustStore = recoverer.create(null, entry, broker);
        assertNotNull("Trust store configured object is not created", trustStore);
        assertEquals(id, trustStore.getId());

        Subject.doAs(SecurityManager.SYSTEM, new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
                //verify we can retrieve the actual password using the method
                assertEquals(TestSSLConstants.BROKER_TRUSTSTORE_PASSWORD, trustStore.getPassword());
                assertNotNull(trustStore.getPassword());
                //verify that we haven't configured the trust store with the actual dummy password value
                assertFalse(AbstractKeyStoreAdapter.DUMMY_PASSWORD_MASK.equals(trustStore.getPassword()));
                return null;
            }
        });


        // Verify the remaining attributes, including that the password value returned
        // via getAttribute is actually the dummy value and not the real password
        attributesCopy.put(TrustStore.PASSWORD, AbstractKeyStoreAdapter.DUMMY_PASSWORD_MASK);
        for (Map.Entry<String, Object> attribute : attributesCopy.entrySet())
        {
            Object attributeValue = trustStore.getAttribute(attribute.getKey());
            assertEquals("Unexpected value of attribute '" + attribute.getKey() + "'", attribute.getValue(), attributeValue);
        }
    }

    public void testCreateWithMissedRequiredAttributes()
    {
        Map<String, Object> attributes = getTrustStoreAttributes();

        UUID id = UUID.randomUUID();
        Broker broker = mock(Broker.class);
        ConfigurationEntry entry = mock(ConfigurationEntry.class);
        when(entry.getAttributes()).thenReturn(attributes);
        when(entry.getId()).thenReturn(id);

        TrustStoreRecoverer recoverer = new TrustStoreRecoverer();

        String[] mandatoryProperties = {TrustStore.NAME, TrustStore.PATH, TrustStore.PASSWORD};
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

    private Map<String, Object> getTrustStoreAttributes()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(TrustStore.NAME, getName());
        attributes.put(TrustStore.PATH, TestSSLConstants.BROKER_TRUSTSTORE);
        attributes.put(TrustStore.PASSWORD, TestSSLConstants.BROKER_TRUSTSTORE_PASSWORD);
        attributes.put(TrustStore.TRUST_STORE_TYPE, "jks");
        attributes.put(TrustStore.TRUST_MANAGER_FACTORY_ALGORITHM, TrustManagerFactory.getDefaultAlgorithm());
        attributes.put(TrustStore.PEERS_ONLY, Boolean.TRUE);
        return attributes;
    }

}
