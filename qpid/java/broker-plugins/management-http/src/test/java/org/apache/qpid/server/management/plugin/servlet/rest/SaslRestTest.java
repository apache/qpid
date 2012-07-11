package org.apache.qpid.server.management.plugin.servlet.rest;

import java.util.List;
import java.util.Map;

public class SaslRestTest extends QpidRestTestCase
{
    public void testGet() throws Exception
    {
        Map<String, Object> saslData = getJsonAsMap("/rest/sasl");
        assertNotNull("mechanisms attribute is not found", saslData.get("mechanisms"));

        @SuppressWarnings("unchecked")
        List<String> mechanisms = (List<String>) saslData.get("mechanisms");
        String[] expectedMechanisms = { "AMQPLAIN", "PLAIN", "CRAM-MD5" };
        for (String mechanism : expectedMechanisms)
        {
            assertTrue("Mechanism " + mechanism + " is not found", mechanisms.contains(mechanism));
        }
    }

}
