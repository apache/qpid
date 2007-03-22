package org.apache.qpid.client.handler.amqp_0_9;

import org.apache.qpid.framing.ChannelCloseOkBody;
import org.apache.qpid.framing.amqp_0_9.ChannelCloseOkBodyImpl;

import org.apache.log4j.Logger;

public class ChannelCloseMethodHandler extends org.apache.qpid.client.handler.amqp_8_0.ChannelCloseMethodHandler
{
    private static final Logger _logger = Logger.getLogger(ChannelCloseMethodHandler.class);

    private static ChannelCloseMethodHandler _handler = new ChannelCloseMethodHandler();

    public static ChannelCloseMethodHandler getInstance()
    {
        return _handler;
    }


    protected ChannelCloseOkBody createChannelCloseOkBody()
    {
        return new ChannelCloseOkBodyImpl();
    }
}
