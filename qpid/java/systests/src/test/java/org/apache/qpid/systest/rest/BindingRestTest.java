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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.model.Binding;

public class BindingRestTest extends QpidRestTestCase
{

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        getRestTestHelper().createTestQueues();
    }

    public void testGetAllBindings() throws Exception
    {
        List<Map<String, Object>> bindings = getRestTestHelper().getJsonAsList("binding/test");
        assertNotNull("Bindings cannot be null", bindings);
        assertEquals("Unexpected number of bindings", RestTestHelper.EXPECTED_QUEUES.length, bindings.size());
        for (Map<String, Object> binding : bindings)
        {
            Asserts.assertBinding((String) binding.get(Binding.NAME), (String) binding.get(Binding.EXCHANGE), binding);
        }
    }

    public void testGetVirtualHostExchangeBindings() throws Exception
    {
        List<Map<String, Object>> bindings = getRestTestHelper().getJsonAsList("binding/test/test/amq.direct");
        assertNotNull("Bindings cannot be null", bindings);
        assertEquals("Unexpected number of bindings", RestTestHelper.EXPECTED_QUEUES.length, bindings.size());
        for (String queueName : RestTestHelper.EXPECTED_QUEUES)
        {
            Map<String, Object> binding = getRestTestHelper().find(Binding.NAME, queueName, bindings);
            Asserts.assertBinding(queueName, "amq.direct", binding);
        }
    }

    public void testGetVirtualHostExchangeQueueBindings() throws Exception
    {
        List<Map<String, Object>> bindings = getRestTestHelper().getJsonAsList("binding/test/test/amq.direct/queue");
        assertNotNull("Bindings cannot be null", bindings);
        assertEquals("Unexpected number of bindings", 1, bindings.size());
        Asserts.assertBinding("queue", "amq.direct", bindings.get(0));
    }


    public void testDeleteBinding() throws Exception
    {
        String bindingUrl = "binding/test/test/amq.direct/queue/queue";
        List<Map<String, Object>> bindings = getRestTestHelper().getJsonAsList(bindingUrl);
        assertEquals("Unexpected number of bindings", 1, bindings.size());
        Asserts.assertBinding("queue", "amq.direct", bindings.get(0));

        int responseCode = getRestTestHelper().submitRequest(bindingUrl, "DELETE");
        assertEquals("Unexpected response code", 200, responseCode);

        bindings = getRestTestHelper().getJsonAsList(bindingUrl);
        assertEquals("Binding should be deleted", 0, bindings.size());
    }

    public void testDeleteBindingById() throws Exception
    {
        Map<String, Object> binding = getRestTestHelper().getJsonAsSingletonList("binding/test/test/amq.direct/queue");
        int responseCode = getRestTestHelper().submitRequest("binding/test/test/amq.direct?id=" + binding.get(Binding.ID), "DELETE");
        assertEquals("Unexpected response code", 200, responseCode);
        List<Map<String, Object>> bindings = getRestTestHelper().getJsonAsList("binding/test/test/amq.direct/queue");
        assertEquals("Binding should be deleted", 0, bindings.size());
    }

    public void testCreateBinding() throws Exception
    {
        String bindingName =  getTestName();
        Map<String, Object> bindingData = new HashMap<String, Object>();
        bindingData.put(Binding.NAME, bindingName);
        bindingData.put(Binding.QUEUE, "queue");
        bindingData.put(Binding.EXCHANGE, "amq.direct");

        String bindingUrl = "binding/test/test/amq.direct/queue/" + bindingName;

        int responseCode = getRestTestHelper().submitRequest(bindingUrl, "PUT", bindingData);
        assertEquals("Unexpected response code", 201, responseCode);

        Map<String, Object> binding = getRestTestHelper().getJsonAsSingletonList(bindingUrl);
        Asserts.assertBinding(bindingName, "queue", "amq.direct", binding);
    }

    public void testSetBindingAttributesUnsupported() throws Exception
    {
        String bindingName =  getTestName();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Binding.NAME, bindingName);
        attributes.put(Binding.QUEUE, "queue");
        attributes.put(Binding.EXCHANGE, "amq.direct");

        String bindingUrl = "binding/test/test/amq.direct/queue/" + bindingName;
        int responseCode = getRestTestHelper().submitRequest(bindingUrl, "PUT", attributes);
        assertEquals("Unexpected response code", 201, responseCode);

        Map<String, Object> binding = getRestTestHelper().getJsonAsSingletonList(bindingUrl);
        Asserts.assertBinding(bindingName, "queue", "amq.direct", binding);

        attributes.put(Binding.ARGUMENTS, "blah");

        responseCode = getRestTestHelper().submitRequest(bindingUrl, "PUT", attributes);
        assertEquals("Update should be unsupported", 409, responseCode);
    }
}
