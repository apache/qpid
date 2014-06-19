package org.apache.qpid.server.plugin;

import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.test.utils.QpidTestCase;

public class AMQPProtocolVersionWrapperTest extends QpidTestCase
{
    public void testAMQPProtocolVersionWrapper() throws Exception
    {
        assertEquals(new AMQPProtocolVersionWrapper(0,8,0), new AMQPProtocolVersionWrapper(Protocol.AMQP_0_8));
        assertEquals(new AMQPProtocolVersionWrapper(0,9,0), new AMQPProtocolVersionWrapper(Protocol.AMQP_0_9));
        assertEquals(new AMQPProtocolVersionWrapper(0,9,1), new AMQPProtocolVersionWrapper(Protocol.AMQP_0_9_1));
        assertEquals(new AMQPProtocolVersionWrapper(0,10,0),new AMQPProtocolVersionWrapper(Protocol.AMQP_0_10));
        assertEquals(new AMQPProtocolVersionWrapper(1,0,0), new AMQPProtocolVersionWrapper(Protocol.AMQP_1_0));

        assertNotSame(new AMQPProtocolVersionWrapper(0, 9, 1), new AMQPProtocolVersionWrapper(Protocol.AMQP_0_9));
        assertNotSame(new AMQPProtocolVersionWrapper(0, 10, 0), new AMQPProtocolVersionWrapper(Protocol.AMQP_1_0));
    }

    public void testAMQPProtocolVersionWrapperGetProtocol() throws Exception
    {
        for (final Protocol protocol : Protocol.values())
        {
            if (protocol.isAMQP())
            {
                assertEquals(protocol, new AMQPProtocolVersionWrapper(protocol).getProtocol());
            }
        }
    }

    public void testWrappingNonAMQPProtocol() throws Exception
    {
        try
        {
            new AMQPProtocolVersionWrapper(Protocol.HTTP);
            fail("IllegalArgumentException exception expected when Protocol is not AMQP based");
        }
        catch (IllegalArgumentException iae)
        {
            // pass
        }
    }
}
