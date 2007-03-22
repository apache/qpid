package org.apache.qpid.client.protocol;

import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.state.AMQStateManager;

/**
 * Created by IntelliJ IDEA.
 * User: U146758
 * Date: 07-Mar-2007
 * Time: 19:40:08
 * To change this template use File | Settings | File Templates.
 */
public interface AMQProtocolHandler
{
    void writeFrame(AMQDataBlock frame);

    void closeSession(AMQSession session) throws AMQException;

    void closeConnection() throws AMQException;

    AMQConnection getConnection();

    AMQStateManager getStateManager();

    AMQProtocolSession getProtocolSession();

    ProtocolOutputHandler getOutputHandler();

    ProtocolVersion getProtocolVersion();
}
