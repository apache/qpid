package org.apache.qpid.server.handler;

import org.apache.qpid.framing.*;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.AMQException;

/**
 * @author Apache Software Foundation
 *
 *
 */
public class AccessRequestHandler implements StateAwareMethodListener<AccessRequestBody>
{
    private static final AccessRequestHandler _instance = new AccessRequestHandler();


    public static AccessRequestHandler getInstance()
    {
        return _instance;
    }

    private AccessRequestHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, AccessRequestBody body, int channelId) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();

        MethodRegistry methodRegistry = session.getMethodRegistry();

        // We don't implement access control class, but to keep clients happy that expect it
        // always use the "0" ticket.
        AccessRequestOkBody response = methodRegistry.createAccessRequestOkBody(0);

        session.writeFrame(response.generateFrame(channelId));
    }
}
