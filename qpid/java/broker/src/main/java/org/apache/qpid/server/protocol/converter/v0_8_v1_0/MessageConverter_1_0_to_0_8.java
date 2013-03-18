package org.apache.qpid.server.protocol.converter.v0_8_v1_0;

import org.apache.qpid.server.plugin.MessageConverter;
import org.apache.qpid.server.protocol.v0_8.AMQMessage;
import org.apache.qpid.server.protocol.v1_0.Message_1_0;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class MessageConverter_1_0_to_0_8 implements MessageConverter<Message_1_0, AMQMessage>
{
    @Override
    public Class<Message_1_0> getInputClass()
    {
        return Message_1_0.class;
    }

    @Override
    public Class<AMQMessage> getOutputClass()
    {
        return AMQMessage.class;
    }

    @Override
    public AMQMessage convert(Message_1_0 message, VirtualHost vhost)
    {
        return null;  //TODO - Implement
    }
}
