package org.apache.qpid.server.management.plugin.servlet.rest;

import java.net.URLDecoder;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Exchange;

public class ExchangeRestTest extends QpidRestTestCase
{
    public void testGet() throws Exception
    {
        List<Map<String, Object>> exchanges = getJsonAsList("/rest/exchange");
        assertNotNull("Exchanges cannot be null", exchanges);
        assertTrue("Unexpected number of exchanges", exchanges.size() >= EXPECTED_HOSTS.length * EXPECTED_EXCHANGES.length);
        for (Map<String, Object> exchange : exchanges)
        {
            Asserts.assertExchange((String) exchange.get(Exchange.NAME), (String) exchange.get(Exchange.TYPE), exchange);
        }
    }

    public void testGetHostExchanges() throws Exception
    {
        List<Map<String, Object>> exchanges = getJsonAsList("/rest/exchange/test");
        assertNotNull("Users cannot be null", exchanges);
        assertEquals("Unexpected number of exchanges", 6, EXPECTED_EXCHANGES.length);
        for (String exchangeName : EXPECTED_EXCHANGES)
        {
            Map<String, Object> exchange = find(Exchange.NAME, exchangeName, exchanges);
            assertExchange(exchangeName, exchange);
        }
    }

    public void testGetHostExchangeByName() throws Exception
    {
        for (String exchangeName : EXPECTED_EXCHANGES)
        {
            Map<String, Object> exchange = getJsonAsSingletonList("/rest/exchange/test/"
                    + URLDecoder.decode(exchangeName, "UTF-8"));
            assertExchange(exchangeName, exchange);
        }
    }

    private void assertExchange(String exchangeName, Map<String, Object> exchange)
    {
        assertNotNull("Exchange with name " + exchangeName + " is not found", exchange);
        String type = (String) exchange.get(Exchange.TYPE);
        Asserts.assertExchange(exchangeName, type, exchange);
        if ("direct".equals(type))
        {
            assertBindings(exchange);
        }
    }

    private void assertBindings(Map<String, Object> exchange)
    {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bindings = (List<Map<String, Object>>) exchange.get("bindings");
        for (String queueName : EXPECTED_QUEUES)
        {
            Map<String, Object> binding = find(Binding.NAME, queueName, bindings);
            Asserts.assertBinding(queueName, (String) exchange.get(Exchange.NAME), binding);
        }
    }

}
