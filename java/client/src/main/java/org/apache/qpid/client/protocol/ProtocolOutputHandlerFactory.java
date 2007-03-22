package org.apache.qpid.client.protocol;

import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.client.protocol.amqp_8_0.ProtocolOutputHandler_8_0;

import java.util.Map;
import java.util.HashMap;

public abstract class ProtocolOutputHandlerFactory
{
    private static final Map<ProtocolVersion, ProtocolOutputHandlerFactory> _handlers =
            new HashMap<ProtocolVersion, ProtocolOutputHandlerFactory>();

    public ProtocolOutputHandlerFactory(ProtocolVersion pv)
    {
        _handlers.put(pv,this);
    }

    public abstract ProtocolOutputHandler newInstance(AMQProtocolSession amqProtocolSession);

    public static ProtocolOutputHandler createOutputHandler(ProtocolVersion version, AMQProtocolSession amqProtocolSession)
    {
        return _handlers.get(version).newInstance(amqProtocolSession);
    }

    private static final ProtocolOutputHandlerFactory VERSION_8_0 =
            new ProtocolOutputHandlerFactory(new ProtocolVersion((byte)8,(byte)0))
            {

                public ProtocolOutputHandler newInstance(AMQProtocolSession amqProtocolSession)
                {
                    return new ProtocolOutputHandler_8_0(amqProtocolSession);
                }
            };

    // TODO - HACK

    private static final ProtocolOutputHandlerFactory VERSION_0_9 =
                new ProtocolOutputHandlerFactory(new ProtocolVersion((byte)0,(byte)9))
                {

                    public ProtocolOutputHandler newInstance(AMQProtocolSession amqProtocolSession)
                    {
                        return new ProtocolOutputHandler_8_0(amqProtocolSession);
                    }
                };



}
