package org.apache.qpid.server.protocol.converter.v0_10_v1_0;

import org.apache.qpid.server.plugin.MessageConverter;
import org.apache.qpid.server.protocol.v0_10.MessageTransferMessage;
import org.apache.qpid.server.protocol.v1_0.Message_1_0;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class MessageConverter_1_0_to_0_10 implements MessageConverter<Message_1_0, MessageTransferMessage>
{
    @Override
    public Class<Message_1_0> getInputClass()
    {
        return Message_1_0.class;
    }

    @Override
    public Class<MessageTransferMessage> getOutputClass()
    {
        return MessageTransferMessage.class;
    }

    @Override
    public MessageTransferMessage convert(Message_1_0 message, VirtualHost vhost)
    {
        return null;  //TODO - Implement
    }
}
