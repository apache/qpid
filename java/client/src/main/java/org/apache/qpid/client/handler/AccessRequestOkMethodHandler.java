package org.apache.qpid.client.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.framing.*;
import org.apache.qpid.client.state.StateAwareMethodListener;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.AMQException;

public class AccessRequestOkMethodHandler implements StateAwareMethodListener<AccessRequestOkBody>
{
    private static final Logger _logger = LoggerFactory.getLogger(AccessRequestOkMethodHandler.class);

    private static AccessRequestOkMethodHandler _handler = new AccessRequestOkMethodHandler();

    public static AccessRequestOkMethodHandler getInstance()
    {
        return _handler;
    }

    public void methodReceived(AMQProtocolSession session, AccessRequestOkBody method, int channelId)
        throws AMQException
    {
        _logger.debug("AccessRequestOk method received");
        session.setTicket(method.getTicket(), channelId);

    }
}
