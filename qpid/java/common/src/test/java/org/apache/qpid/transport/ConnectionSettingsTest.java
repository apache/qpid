package org.apache.qpid.transport;

import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.test.utils.QpidTestCase;

public class ConnectionSettingsTest extends QpidTestCase
{
    ConnectionSettings _conConnectionSettings;

    protected void setUp() throws Exception
    {
        super.setUp();
        _conConnectionSettings = new ConnectionSettings();
    }

    public void testDefaultTCP_NODELAY()
    {
        assertTrue("Default for isTcpNodelay() should be true", _conConnectionSettings.isTcpNodelay());
    }

    public void testSystemPropertyOverrideTrueForTCP_NODELAY()
    {
        systemPropertyOverrideForTCP_NODELAYImpl(ClientProperties.QPID_TCP_NODELAY_PROP_NAME, true);
    }
    
    public void testSystemPropertyOverrideFalseForTCP_NODELAY()
    {
        systemPropertyOverrideForTCP_NODELAYImpl(ClientProperties.QPID_TCP_NODELAY_PROP_NAME, false);
    }

    public void testLegacySystemPropertyOverrideTrueForTCP_NODELAY()
    {
        systemPropertyOverrideForTCP_NODELAYImpl(ClientProperties.AMQJ_TCP_NODELAY_PROP_NAME, true);
    }

    public void testLegacySystemPropertyOverrideFalseForTCP_NODELAY()
    {
        systemPropertyOverrideForTCP_NODELAYImpl(ClientProperties.AMQJ_TCP_NODELAY_PROP_NAME, false);
    }

    private void systemPropertyOverrideForTCP_NODELAYImpl(String propertyName, boolean value)
    {
        //set the default via system property
        setTestSystemProperty(propertyName, String.valueOf(value));

        _conConnectionSettings = new ConnectionSettings();
        assertEquals("Value for isTcpNodelay() is incorrect", value, _conConnectionSettings.isTcpNodelay());
    }
}
