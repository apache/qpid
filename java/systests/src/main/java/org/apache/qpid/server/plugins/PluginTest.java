package org.apache.qpid.server.plugins;

import java.util.Map;

import org.apache.qpid.server.exchange.ExchangeType;

import junit.framework.TestCase;

public class PluginTest extends TestCase
{

    private static final String TEST_EXCHANGE_CLASS = "org.apache.qpid.extras.exchanges.example.TestExchangeType";
    private static final String PLUGIN_DIRECTORY = System.getProperty("example.plugin.target");

    public void testLoadExchanges() throws Exception
    {
        PluginManager manager = new PluginManager(PLUGIN_DIRECTORY);
        Map<String, ExchangeType<?>> exchanges = manager.getExchanges();
        assertNotNull("No exchanges found in "+PLUGIN_DIRECTORY, exchanges);
        assertEquals("Wrong number of exchanges found in "+PLUGIN_DIRECTORY, 
                     2, exchanges.size());
        assertNotNull("Wrong exchange found in "+PLUGIN_DIRECTORY,
                      exchanges.get(TEST_EXCHANGE_CLASS));
    } 
    
    public void testNoExchanges() throws Exception
    {
        PluginManager manager = new PluginManager("/path/to/nowhere");
        Map<String, ExchangeType<?>> exchanges = manager.getExchanges();
        assertNull("Exchanges found", exchanges);
    } 
    
}
