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

import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.Subject;

import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.security.FileTrustStore;
import org.apache.qpid.server.security.FileTrustStoreImpl;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestSSLConstants;

public class FileTrustStoreCreationTest extends QpidTestCase
{
    public void testCreateWithAllAttributesProvided()
    {
        UUID id = UUID.randomUUID();
        Map<String, Object> attributes = getTrustStoreAttributes(id);
        Map<String, Object> attributesCopy = new HashMap<String, Object>(attributes);

        Broker broker = mock(Broker.class);

        final FileTrustStore trustStore = new FileTrustStoreImpl(attributes, broker);
        trustStore.open();
        assertNotNull("Trust store configured object is not created", trustStore);
        assertEquals(id, trustStore.getId());

        Subject.doAs(SecurityManager.getSubjectWithAddedSystemRights(), new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
                //verify we can retrieve the actual password using the method
                assertEquals(TestSSLConstants.BROKER_TRUSTSTORE_PASSWORD, trustStore.getPassword());
                assertNotNull(trustStore.getPassword());
                //verify that we haven't configured the trust store with the actual dummy password value
                assertFalse(AbstractConfiguredObject.SECURED_STRING_VALUE.equals(trustStore.getPassword()));
                return null;
            }
        });


        // Verify the remaining attributes, including that the password value returned
        // via getAttribute is actually the dummy value and not the real password
        attributesCopy.put(FileTrustStore.PASSWORD, AbstractConfiguredObject.SECURED_STRING_VALUE);
        for (Map.Entry<String, Object> attribute : attributesCopy.entrySet())
        {
            Object attributeValue = trustStore.getAttribute(attribute.getKey());
            assertEquals("Unexpected value of attribute '" + attribute.getKey() + "'", attribute.getValue(), attributeValue);
        }
    }

    public void testCreateWithMissedRequiredAttributes()
    {
        UUID id = UUID.randomUUID();
        Map<String, Object> attributes = getTrustStoreAttributes(id);

        Broker broker = mock(Broker.class);

        String[] mandatoryProperties = {TrustStore.NAME, FileTrustStore.PATH, FileTrustStore.PASSWORD};
        for (int i = 0; i < mandatoryProperties.length; i++)
        {
            Map<String, Object> properties =  new HashMap<String, Object>(attributes);
            properties.remove(mandatoryProperties[i]);
            try
            {
                TrustStore trustStore = new FileTrustStoreImpl(properties, broker);
                trustStore.open();
                fail("Cannot create key store without a " + mandatoryProperties[i]);
            }
            catch(IllegalArgumentException e)
            {
                // pass
            }
        }
    }

    private Map<String, Object> getTrustStoreAttributes(UUID id)
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(TrustStore.NAME, getName());
        attributes.put(TrustStore.ID, id);
        attributes.put(FileTrustStore.PATH, TestSSLConstants.BROKER_TRUSTSTORE);
        attributes.put(FileTrustStore.PASSWORD, TestSSLConstants.BROKER_TRUSTSTORE_PASSWORD);
        attributes.put(FileTrustStore.TRUST_STORE_TYPE, "jks");
        attributes.put(FileTrustStore.TRUST_MANAGER_FACTORY_ALGORITHM, TrustManagerFactory.getDefaultAlgorithm());
        attributes.put(FileTrustStore.PEERS_ONLY, Boolean.TRUE);
        return attributes;
    }


}
