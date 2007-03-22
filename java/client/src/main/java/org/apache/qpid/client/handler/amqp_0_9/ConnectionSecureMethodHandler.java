package org.apache.qpid.client.handler.amqp_0_9;

import org.apache.qpid.client.state.StateAwareMethodListener;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.ConnectionSecureBody;
import org.apache.qpid.framing.ConnectionSecureOkBody;
import org.apache.qpid.framing.amqp_8_0.ConnectionSecureOkBodyImpl;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

public class ConnectionSecureMethodHandler extends org.apache.qpid.client.handler.amqp_8_0.ConnectionSecureMethodHandler
{
    private static final ConnectionSecureMethodHandler _instance = new ConnectionSecureMethodHandler();

    public static ConnectionSecureMethodHandler getInstance()
    {
        return _instance;
    }

    protected ConnectionSecureOkBody createConnectionSecureOkBody(byte[] response)
    {
        return new ConnectionSecureOkBodyImpl(response);
    }
}
