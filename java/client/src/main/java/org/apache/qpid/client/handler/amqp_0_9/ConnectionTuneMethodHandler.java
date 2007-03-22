package org.apache.qpid.client.handler.amqp_0_9;

import org.apache.qpid.client.ConnectionTuneParameters;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ConnectionOpenBody;
import org.apache.qpid.framing.ConnectionTuneOkBody;
import org.apache.qpid.framing.amqp_0_9.ConnectionOpenBodyImpl;
import org.apache.qpid.framing.amqp_0_9.ConnectionTuneOkBodyImpl;

import org.apache.log4j.Logger;

public class ConnectionTuneMethodHandler extends org.apache.qpid.client.handler.amqp_8_0.ConnectionTuneMethodHandler
{
    private static final Logger _logger = Logger.getLogger(ConnectionTuneMethodHandler.class);

    private static final ConnectionTuneMethodHandler _instance = new ConnectionTuneMethodHandler();

    public static ConnectionTuneMethodHandler getInstance()
    {
        return _instance;
    }


    protected ConnectionOpenBody createConnectionOpenBody(AMQShortString path, AMQShortString capabilities, boolean insist)
    {

        return new ConnectionOpenBodyImpl(path,// virtualHost
            capabilities,	// capabilities
            insist);	// insist

    }

    protected ConnectionTuneOkBody createTuneOkBody(ConnectionTuneParameters params)
    {
        // Be aware of possible changes to parameter order as versions change.
        return new ConnectionTuneOkBodyImpl(
            params.getChannelMax(),	// channelMax
            params.getFrameMax(),	// frameMax
            params.getHeartbeat());	// heartbeat
    }
}
