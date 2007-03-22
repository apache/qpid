package org.apache.qpid.client.handler.amqp_0_9;

import org.apache.qpid.framing.ConnectionCloseOkBody;
import org.apache.qpid.framing.amqp_0_9.ConnectionCloseOkBodyImpl;

import org.apache.log4j.Logger;

public class ConnectionCloseMethodHandler extends org.apache.qpid.client.handler.amqp_8_0.ConnectionCloseMethodHandler
{
    private static final Logger _logger = Logger.getLogger(ConnectionCloseMethodHandler.class);

    private static ConnectionCloseMethodHandler _handler = new ConnectionCloseMethodHandler();

    public static ConnectionCloseMethodHandler getInstance()
    {
        return _handler;
    }

    protected ConnectionCloseMethodHandler()
    {
    }


    protected ConnectionCloseOkBody createConnectionCloseOkBody()
    {
        return new ConnectionCloseOkBodyImpl();
    }

}
