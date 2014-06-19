package org.apache.qpid.server.plugin;

import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.test.utils.QpidTestCase;
import org.mockito.Mockito;

public class ProtocolEngineCreatorComparatorTest extends QpidTestCase
{
    public void testProtocolEngineCreatorComparator() throws Exception
    {
        final ProtocolEngineCreatorComparator comparator = new ProtocolEngineCreatorComparator();

        final ProtocolEngineCreator amqp_0_8 = createAMQPProtocolEngineCreatorMock(Protocol.AMQP_0_8);
        final ProtocolEngineCreator amqp_0_9 = createAMQPProtocolEngineCreatorMock(Protocol.AMQP_0_9);
        final ProtocolEngineCreator amqp_0_9_1 = createAMQPProtocolEngineCreatorMock(Protocol.AMQP_0_9_1);
        final ProtocolEngineCreator amqp_0_10 = createAMQPProtocolEngineCreatorMock(Protocol.AMQP_0_10);
        final ProtocolEngineCreator amqp_1_0 = createAMQPProtocolEngineCreatorMock(Protocol.AMQP_1_0);

        assertTrue(comparator.compare(amqp_0_8,amqp_0_9) < 0);
        assertTrue(comparator.compare(amqp_0_9,amqp_0_9_1) < 0);
        assertTrue(comparator.compare(amqp_0_9_1,amqp_0_10) < 0);
        assertTrue(comparator.compare(amqp_0_10,amqp_1_0) < 0);

        assertTrue(comparator.compare(amqp_0_9,amqp_0_8) > 0);
        assertTrue(comparator.compare(amqp_0_9_1,amqp_0_9) > 0);
        assertTrue(comparator.compare(amqp_0_10,amqp_0_9_1) > 0);
        assertTrue(comparator.compare(amqp_1_0,amqp_0_10) > 0);

        assertTrue(comparator.compare(amqp_0_8,amqp_0_8) == 0);
        assertTrue(comparator.compare(amqp_0_9,amqp_0_9) == 0);
        assertTrue(comparator.compare(amqp_0_9_1,amqp_0_9_1) == 0);
        assertTrue(comparator.compare(amqp_0_10,amqp_0_10) == 0);
        assertTrue(comparator.compare(amqp_1_0,amqp_1_0) == 0);
    }

    private ProtocolEngineCreator createAMQPProtocolEngineCreatorMock(Protocol protocol)
    {
        final ProtocolEngineCreator protocolMock = Mockito.mock(ProtocolEngineCreator.class);
        Mockito.when(protocolMock.getVersion()).thenReturn(protocol);
        return protocolMock;
    }
}
