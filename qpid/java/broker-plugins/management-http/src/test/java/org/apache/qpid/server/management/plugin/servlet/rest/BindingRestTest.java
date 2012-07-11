package org.apache.qpid.server.management.plugin.servlet.rest;

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

}
