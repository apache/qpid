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

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.model.Binding;

public class BindingRestTest extends QpidRestTestCase
{

    public void testGetAllBindings() throws Exception
    {
        List<Map<String, Object>> bindings = getRestTestHelper().getJsonAsList("/rest/binding");
        assertNotNull("Bindings cannot be null", bindings);
        assertTrue("Unexpected number of bindings: " + bindings.size(),
                bindings.size() >= EXPECTED_VIRTUALHOSTS.length * EXPECTED_QUEUES.length);
        for (Map<String, Object> binding : bindings)
        {
            Asserts.assertBinding((String) binding.get(Binding.NAME), (String) binding.get(Binding.EXCHANGE), binding);
        }
    }

    public void testGetVirtualHostBindings() throws Exception
    {
        List<Map<String, Object>> bindings = getRestTestHelper().getJsonAsList("/rest/binding/test");
        assertNotNull("Bindings cannot be null", bindings);
        assertEquals("Unexpected number of bindings", EXPECTED_QUEUES.length * 2, bindings.size());
        for (String queueName : EXPECTED_QUEUES)
        {
            Map<String, Object> searchAttributes = new HashMap<String, Object>();
            searchAttributes.put(Binding.NAME, queueName);
            searchAttributes.put(Binding.EXCHANGE, "amq.direct");

            Map<String, Object> binding = getRestTestHelper().find(searchAttributes, bindings);
            Asserts.assertBinding(queueName, "amq.direct", binding);

            searchAttributes.put(Binding.EXCHANGE, "<<default>>");

            binding = getRestTestHelper().find(searchAttributes, bindings);
            Asserts.assertBinding(queueName, "<<default>>", binding);
        }
    }

    public void testGetVirtualHostExchangeBindings() throws Exception
    {
        List<Map<String, Object>> bindings = getRestTestHelper().getJsonAsList("/rest/binding/test/amq.direct");
        assertNotNull("Bindings cannot be null", bindings);
        assertEquals("Unexpected number of bindings", EXPECTED_QUEUES.length, bindings.size());
        for (String queueName : EXPECTED_QUEUES)
        {
            Map<String, Object> binding = getRestTestHelper().find(Binding.NAME, queueName, bindings);
            Asserts.assertBinding(queueName, "amq.direct", binding);
        }
    }

    public void testGetVirtualHostExchangeQueueBindings() throws Exception
    {
        List<Map<String, Object>> bindings = getRestTestHelper().getJsonAsList("/rest/binding/test/amq.direct/queue");
        assertNotNull("Bindings cannot be null", bindings);
        assertEquals("Unexpected number of bindings", 1, bindings.size());
        Asserts.assertBinding("queue", "amq.direct", bindings.get(0));
    }


    public void testDeleteBinding() throws Exception
    {
        List<Map<String, Object>> bindings = getRestTestHelper().getJsonAsList("/rest/binding/test/amq.direct/queue/queue");
        assertEquals("Unexpected number of bindings", 1, bindings.size());
        Asserts.assertBinding("queue", "amq.direct", bindings.get(0));

        HttpURLConnection connection = getRestTestHelper().openManagementConnection("/rest/binding/test/amq.direct/queue/queue", "DELETE");
        connection.connect();
        assertEquals("Unexpected response code", 200, connection.getResponseCode());

        bindings = getRestTestHelper().getJsonAsList("/rest/binding/test/amq.direct/queue/queue");
        assertEquals("Binding should be deleted", 0, bindings.size());
    }

    public void testDeleteBindingById() throws Exception
    {
        Map<String, Object> binding = getRestTestHelper().getJsonAsSingletonList("/rest/binding/test/amq.direct/queue");
        HttpURLConnection connection = getRestTestHelper().openManagementConnection("/rest/binding/test/amq.direct?id=" + binding.get(Binding.ID), "DELETE");
        connection.connect();
        assertEquals("Unexpected response code", 200, connection.getResponseCode());
        List<Map<String, Object>> bindings = getRestTestHelper().getJsonAsList("/rest/binding/test/amq.direct/queue");
        assertEquals("Binding should be deleted", 0, bindings.size());
    }

    public void testCreateBinding() throws Exception
    {
        String bindingName =  getTestName();
        Map<String, Object> bindingData = new HashMap<String, Object>();
        bindingData.put(Binding.NAME, bindingName);
        bindingData.put(Binding.QUEUE, "queue");
        bindingData.put(Binding.EXCHANGE, "amq.direct");

        HttpURLConnection connection = getRestTestHelper().openManagementConnection("/rest/binding/test/amq.direct/queue/" + bindingName, "PUT");
        connection.connect();
        getRestTestHelper().writeJsonRequest(connection, bindingData);
        int responseCode = connection.getResponseCode();
        connection.disconnect();
        assertEquals("Unexpected response code", 201, responseCode);
        Map<String, Object> binding = getRestTestHelper().getJsonAsSingletonList("/rest/binding/test/amq.direct/queue/" + bindingName);

        Asserts.assertBinding(bindingName, "queue", "amq.direct", binding);
    }

}
