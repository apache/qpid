package org.apache.qpid.server.management.plugin.servlet.rest;

import java.net.URLDecoder;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.model.Port;

public class PortRestTest extends QpidRestTestCase
{
    public void testGet() throws Exception
    {
        List<Map<String, Object>> ports = getJsonAsList("/rest/port/");
        assertNotNull("Port data cannot be null", ports);
        assertEquals("Unexpected number of ports", 2, ports.size());
        int[] expectedPorts = { getPort(), getHttpPort() };
        for (int port : expectedPorts)
        {
            String portName = "0.0.0.0:" + port;
            Map<String, Object> portData = find(Port.NAME, portName, ports);
            assertNotNull("Port " + portName + " is not found", portData);
            Asserts.assertPortAttributes(portData);
        }
    }

    public void testGetPort() throws Exception
    {
        List<Map<String, Object>> ports = getJsonAsList("/rest/port/");
        assertNotNull("Ports data cannot be null", ports);
        assertEquals("Unexpected number of ports", 2, ports.size());
        for (Map<String, Object> portMap : ports)
        {
            String portName = (String) portMap.get(Port.NAME);
            assertNotNull("Port name attribute is not found", portName);
            Map<String, Object> portData = getJsonAsSingletonList("/rest/port/" + URLDecoder.decode(portName, "UTF-8"));
            assertNotNull("Port " + portName + " is not found", portData);
            Asserts.assertPortAttributes(portData);
        }
    }

}
