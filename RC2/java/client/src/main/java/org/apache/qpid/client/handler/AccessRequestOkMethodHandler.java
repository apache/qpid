package org.apache.qpid.client.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.framing.*;
import org.apache.qpid.client.state.StateAwareMethodListener;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.client.AMQNoConsumersException;
import org.apache.qpid.client.AMQNoRouteException;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQInvalidRoutingKeyException;
import org.apache.qpid.AMQChannelClosedException;
import org.apache.qpid.protocol.AMQConstant;

public class AccessRequestOkMethodHandler implements StateAwareMethodListener<AccessRequestOkBody>
{
    private static final Logger _logger = LoggerFactory.getLogger(AccessRequestOkMethodHandler.class);

    private static AccessRequestOkMethodHandler _handler = new AccessRequestOkMethodHandler();

    public static AccessRequestOkMethodHandler getInstance()
    {
        return _handler;
    }

    public void methodReceived(AMQStateManager stateManager, AccessRequestOkBody method, int channelId)
        throws AMQException
    {
        _logger.debug("AccessRequestOk method received");
        final AMQProtocolSession session = stateManager.getProtocolSession();
        session.setTicket(method.getTicket(), channelId);

    }
}
