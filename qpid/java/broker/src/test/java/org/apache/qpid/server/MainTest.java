package org.apache.qpid.server;

import java.util.EnumSet;

import org.apache.qpid.test.utils.QpidTestCase;

/**
 * Test to verify the command line parsing within the Main class, by
 * providing it a series of command line arguments and verifying the
 * BrokerOptions emerging for use in starting the Broker instance.
 */
public class MainTest extends QpidTestCase
{
    public void testNoOptionsSpecified()
    {
        BrokerOptions options = startDummyMain("");

        assertTrue(options.getPorts().isEmpty());
        assertTrue(options.getSSLPorts().isEmpty());
        assertEquals(null, options.getJmxPortRegistryServer());
        assertEquals(null, options.getConfigFile());
        assertEquals(null, options.getLogConfigFile());
        assertEquals(null, options.getBind());

        for(ProtocolExclusion pe : EnumSet.allOf(ProtocolExclusion.class))
        {
            assertEquals(0, options.getExcludedPorts(pe).size());
        }
    }

    public void testPortOverriddenSingle()
    {
        BrokerOptions options = startDummyMain("-p 1234");

        assertTrue(options.getPorts().contains(1234));
        assertEquals(1, options.getPorts().size());
        assertTrue(options.getSSLPorts().isEmpty());
    }

    public void testPortOverriddenMultiple()
    {
        BrokerOptions options = startDummyMain("-p 1234 -p 4321");

        assertTrue(options.getPorts().contains(1234));
        assertTrue(options.getPorts().contains(4321));
        assertEquals(2, options.getPorts().size());
        assertTrue(options.getSSLPorts().isEmpty());
    }

    public void testSSLPortOverriddenSingle()
    {
        BrokerOptions options = startDummyMain("-s 5678");

        assertTrue(options.getSSLPorts().contains(5678));
        assertEquals(1, options.getSSLPorts().size());
        assertTrue(options.getPorts().isEmpty());
    }

    public void testSSLPortOverriddenMultiple()
    {
        BrokerOptions options = startDummyMain("-s 5678 -s 8765");

        assertTrue(options.getSSLPorts().contains(5678));
        assertTrue(options.getSSLPorts().contains(8765));
        assertEquals(2, options.getSSLPorts().size());
        assertTrue(options.getPorts().isEmpty());
    }

    public void testNonSSLandSSLPortsOverridden()
    {
        BrokerOptions options = startDummyMain("-p 5678 -s 8765");

        assertTrue(options.getPorts().contains(5678));
        assertTrue(options.getSSLPorts().contains(8765));
        assertEquals(1, options.getPorts().size());
        assertEquals(1, options.getSSLPorts().size());
    }

    public void testJmxPortRegistryServerOverridden()
    {
        BrokerOptions options = startDummyMain("--jmxregistryport 3456");

        assertEquals(Integer.valueOf(3456), options.getJmxPortRegistryServer());

         options = startDummyMain("-m 3457");
         assertEquals(Integer.valueOf(3457), options.getJmxPortRegistryServer());
    }

    public void testJmxPortConnectorServerOverridden()
    {
        BrokerOptions options = startDummyMain("--jmxconnectorport 3456");

        assertEquals(Integer.valueOf(3456), options.getJmxPortConnectorServer());
    }

    public void testExclude0_10()
    {
        BrokerOptions options = startDummyMain("-p 3456 --exclude-0-10 3456");

        assertTrue(options.getPorts().contains(3456));
        assertEquals(1, options.getPorts().size());
        assertTrue(options.getExcludedPorts(ProtocolExclusion.v0_10).contains(3456));
        assertEquals(1, options.getExcludedPorts(ProtocolExclusion.v0_10).size());
        assertEquals(0, options.getExcludedPorts(ProtocolExclusion.v0_9_1).size());
    }

    public void testConfig()
    {
        BrokerOptions options = startDummyMain("-c abcd/config.xml");

        assertEquals("abcd/config.xml", options.getConfigFile());
    }

    public void testLogConfig()
    {
        BrokerOptions options = startDummyMain("-l wxyz/log4j.xml");

        assertEquals("wxyz/log4j.xml", options.getLogConfigFile());
    }

    public void testLogWatch()
    {
        BrokerOptions options = startDummyMain("-w 9");

        assertEquals(9, options.getLogWatchFrequency());
    }

    private BrokerOptions startDummyMain(String commandLine)
    {
        return (new TestMain(commandLine.split("\\s"))).getOptions();
    }

    private class TestMain extends Main
    {
        private BrokerOptions _options;

        public TestMain(String[] args)
        {
            super(args);
        }

        @Override
        protected void startBroker(BrokerOptions options)
        {
            _options = options;
        }

        public BrokerOptions getOptions()
        {
            return _options;
        }
    }
}
