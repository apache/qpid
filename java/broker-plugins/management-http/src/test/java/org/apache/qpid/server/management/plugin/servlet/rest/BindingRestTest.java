package org.apache.qpid.server.management.plugin.servlet.rest;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.model.Binding;

public class BindingRestTest extends QpidRestTestCase
{

    public void testGetAllBindings() throws Exception
    {
        List<Map<String, Object>> bindings = getJsonAsList("/rest/binding");
        assertNotNull("Bindings cannot be null", bindings);
        assertTrue("Unexpected number of bindings", bindings.size() >= EXPECTED_HOSTS.length * EXPECTED_QUEUES.length);
        for (Map<String, Object> binding : bindings)
        {
            Asserts.assertBinding((String) binding.get(Binding.NAME), (String) binding.get(Binding.EXCHANGE), binding);
        }
    }

    public void testGetVirtualHostBindings() throws Exception
    {
        List<Map<String, Object>> bindings = getJsonAsList("/rest/binding/test");
        assertNotNull("Bindings cannot be null", bindings);
        assertEquals("Unexpected number of bindings", EXPECTED_QUEUES.length * 2, bindings.size());
        for (String queueName : EXPECTED_QUEUES)
        {
            Map<String, Object> searchAttributes = new HashMap<String, Object>();
            searchAttributes.put(Binding.NAME, queueName);
            searchAttributes.put(Binding.EXCHANGE, "amq.direct");

            Map<String, Object> binding = find(searchAttributes, bindings);
            Asserts.assertBinding(queueName, "amq.direct", binding);

            searchAttributes.put(Binding.EXCHANGE, "<<default>>");

            binding = find(searchAttributes, bindings);
            Asserts.assertBinding(queueName, "<<default>>", binding);
        }
    }

    public void testGetVirtualHostExchangeBindings() throws Exception
    {
        List<Map<String, Object>> bindings = getJsonAsList("/rest/binding/test/amq.direct");
        assertNotNull("Bindings cannot be null", bindings);
        assertEquals("Unexpected number of bindings", EXPECTED_QUEUES.length, bindings.size());
        for (String queueName : EXPECTED_QUEUES)
        {
            Map<String, Object> binding = find(Binding.NAME, queueName, bindings);
            Asserts.assertBinding(queueName, "amq.direct", binding);
        }
    }

    public void testGetVirtualHostExchangeQueueBindings() throws Exception
    {
        List<Map<String, Object>> bindings = getJsonAsList("/rest/binding/test/amq.direct/queue");
        assertNotNull("Bindings cannot be null", bindings);
        assertEquals("Unexpected number of bindings", 1, bindings.size());
        Asserts.assertBinding("queue", "amq.direct", bindings.get(0));
    }


    public void testDeleteBinding() throws Exception
    {
        List<Map<String, Object>> bindings = getJsonAsList("/rest/binding/test/amq.direct/queue/queue");
        assertEquals("Unexpected number of bindings", 1, bindings.size());
        Asserts.assertBinding("queue", "amq.direct", bindings.get(0));

        HttpURLConnection connection = openManagementConection("/rest/binding/test/amq.direct/queue/queue", "DELETE");
        connection.connect();
        assertEquals("Unexpected response code", 200, connection.getResponseCode());

        bindings = getJsonAsList("/rest/binding/test/amq.direct/queue/queue");
        assertEquals("Binding should be deleted", 0, bindings.size());
    }

    public void testDeleteBindingById() throws Exception
    {
        Map<String, Object> binding = getJsonAsSingletonList("/rest/binding/test/amq.direct/queue");
        HttpURLConnection connection = openManagementConection("/rest/binding/test/amq.direct?id=" + binding.get(Binding.ID), "DELETE");
        connection.connect();
        assertEquals("Unexpected response code", 200, connection.getResponseCode());
        List<Map<String, Object>> bindings = getJsonAsList("/rest/binding/test/amq.direct/queue");
        assertEquals("Binding should be deleted", 0, bindings.size());
    }

    public void testCreateBinding() throws Exception
    {
        String bindingName =  getTestName();
        Map<String, Object> bindingData = new HashMap<String, Object>();
        bindingData.put(Binding.NAME, bindingName);
        bindingData.put(Binding.QUEUE, "queue");
        bindingData.put(Binding.EXCHANGE, "amq.direct");

        HttpURLConnection connection = openManagementConection("/rest/binding/test/amq.direct/queue/" + bindingName, "PUT");
        connection.connect();
        writeJsonRequest(connection, bindingData);
        int responseCode = connection.getResponseCode();
        connection.disconnect();
        assertEquals("Unexpected response code", 201, responseCode);
        Map<String, Object> binding = getJsonAsSingletonList("/rest/binding/test/amq.direct/queue/" + bindingName);

        Asserts.assertBinding(bindingName, "queue", "amq.direct", binding);
    }

}
