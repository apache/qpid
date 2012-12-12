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

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.test.utils.QpidTestCase;

public class TrustStoreRecovererTest extends QpidTestCase
{
    public void testCreateWithAllAttributesProvided()
    {
        Map<String, Object> attributes = getTrustStoreAttributes();

        UUID id = UUID.randomUUID();
        Broker broker = mock(Broker.class);
        ConfigurationEntry entry = mock(ConfigurationEntry.class);
        when(entry.getAttributes()).thenReturn(attributes);
        when(entry.getId()).thenReturn(id);

        TrustStoreRecoverer recovever = new TrustStoreRecoverer();

        TrustStore trustStore = recovever.create(null, entry, broker);
        assertNotNull("Trust store configured object is not created", trustStore);
        assertEquals(id, trustStore.getId());
        assertEquals("my-secret-password", trustStore.getPassword());

        assertNull("Password was unexpectedly returned from configured object", trustStore.getAttribute(TrustStore.PASSWORD));

        // password attribute should not be exposed by a trust store configured object
        // so, we should set password value to null in the map being used to create the trust store configured object
        attributes.put(TrustStore.PASSWORD, null);
        for (Map.Entry<String, Object> attribute : attributes.entrySet())
        {
            Object attributeValue = trustStore.getAttribute(attribute.getKey());
            assertEquals("Unexpected value of attribute '" + attribute.getKey() + "'", attribute.getValue(), attributeValue);
        }
    }

    private Map<String, Object> getTrustStoreAttributes()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(TrustStore.NAME, getName());
        attributes.put(TrustStore.PATH, "/path/to/truststore");
        attributes.put(TrustStore.PASSWORD, "my-secret-password");
        attributes.put(TrustStore.TYPE, "NON-JKS");
        attributes.put(TrustStore.KEY_MANAGER_FACTORY_ALGORITHM, "NON-STANDARD");
        attributes.put(TrustStore.DESCRIPTION, "Description");
        return attributes;
    }

    public void testCreateWithMissedRequiredAttributes()
    {
        Map<String, Object> attributes = getTrustStoreAttributes();

        UUID id = UUID.randomUUID();
        Broker broker = mock(Broker.class);
        ConfigurationEntry entry = mock(ConfigurationEntry.class);
        when(entry.getAttributes()).thenReturn(attributes);
        when(entry.getId()).thenReturn(id);

        TrustStoreRecoverer recovever = new TrustStoreRecoverer();

        String[] mandatoryProperties = {TrustStore.NAME, TrustStore.PATH, TrustStore.PASSWORD};
        for (int i = 0; i < mandatoryProperties.length; i++)
        {
            Map<String, Object> properties =  new HashMap<String, Object>(attributes);
            properties.remove(mandatoryProperties[i]);
            when(entry.getAttributes()).thenReturn(properties);
            try
            {
                recovever.create(null, entry, broker);
                fail("Cannot create key store without a " + mandatoryProperties[i]);
            }
            catch(IllegalArgumentException e)
            {
                // pass
            }
        }
    }

}
