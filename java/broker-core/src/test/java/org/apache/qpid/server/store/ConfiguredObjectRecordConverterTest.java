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
package org.apache.qpid.server.store;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.test.utils.QpidTestCase;

public class ConfiguredObjectRecordConverterTest extends QpidTestCase
{

    public void testSecondParentReferencedByName() throws Exception
    {

        String jsonData = "{\n"
                          + "  \"name\" : \"test\",\n"
                          + "  \"exchanges\" : [ {\n"
                          + "    \"name\" : \"amq.direct\",\n"
                          + "    \"type\" : \"direct\"\n"
                          + "  } ],\n"
                          + "  \"queues\" : [ {\n"
                          + "    \"name\" : \"foo\",\n"
                          + "    \"bindings\" : [ {\n"
                          + "      \"exchange\" : \"amq.direct\",\n"
                          + "      \"name\" : \"foo\"\n"
                          + "    } ]\n"
                          + "  } ]\n"
                          + "} ";

        ConfiguredObjectRecordConverter converter = new ConfiguredObjectRecordConverter(BrokerModel.getInstance());
        ConfiguredObject parent = mock(ConfiguredObject.class);
        when(parent.getId()).thenReturn(UUID.randomUUID());
        when(parent.getCategoryClass()).thenReturn(VirtualHostNode.class);
        Collection<ConfiguredObjectRecord> records =
                converter.readFromJson(VirtualHost.class, parent, new StringReader(jsonData));

        UUID exchangeId = null;
        for (ConfiguredObjectRecord record : records)
        {
            if (record.getType().equals(Exchange.class.getSimpleName()))
            {
                assertNull("Only one exchange record expected", exchangeId);
                exchangeId = record.getId();
            }
        }
        assertNotNull("No exchange record found", exchangeId);

        UUID queueId = null;
        for (ConfiguredObjectRecord record : records)
        {
            if (record.getType().equals(Queue.class.getSimpleName()))
            {
                assertNull("Only one queue record expected", queueId);
                queueId = record.getId();
            }
        }
        assertNotNull("No queueId record found", queueId);

        boolean bindingFound = false;
        for (ConfiguredObjectRecord record : records)
        {
            if (record.getType().equals(Binding.class.getSimpleName()))
            {
                assertFalse("Expecting only one binding", bindingFound);
                bindingFound = true;
                Map<String,UUID> parents = record.getParents();
                assertEquals("Two parents expected", 2, parents.size());
                assertEquals("Queue parent id not as expected", queueId, parents.get(Queue.class.getSimpleName()));
                assertEquals("Exchange parent id not as expected", exchangeId, parents.get(Exchange.class.getSimpleName()));

            }
        }
        assertTrue("No binding found", bindingFound);
    }

    public void testUnresolvedSecondParentFailsToCovert() throws Exception
    {
        {

            String jsonData = "{\n"
                              + "  \"name\" : \"test\",\n"
                              + "  \"exchanges\" : [ {\n"
                              + "    \"name\" : \"amq.direct\",\n"
                              + "    \"type\" : \"direct\"\n"
                              + "  } ],\n"
                              + "  \"queues\" : [ {\n"
                              + "    \"name\" : \"foo\",\n"
                              + "    \"bindings\" : [ {\n"
                              + "      \"exchange\" : \"amq.topic\",\n"
                              + "      \"name\" : \"foo\"\n"
                              + "    } ]\n"
                              + "  } ]\n"
                              + "} ";

            ConfiguredObjectRecordConverter converter = new ConfiguredObjectRecordConverter(BrokerModel.getInstance());
            ConfiguredObject parent = mock(ConfiguredObject.class);
            when(parent.getId()).thenReturn(UUID.randomUUID());
            when(parent.getCategoryClass()).thenReturn(VirtualHostNode.class);
            try
            {
                converter.readFromJson(VirtualHost.class, parent, new StringReader(jsonData));
                fail("The records should not be converted as there is an unresolved reference");
            }
            catch (IllegalArgumentException e)
            {
                // pass
            }

        }
    }
}
