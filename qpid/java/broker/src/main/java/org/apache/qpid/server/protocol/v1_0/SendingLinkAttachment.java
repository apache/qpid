package org.apache.qpid.server.protocol.v1_0;

import org.apache.qpid.amqp_1_0.transport.DeliveryStateHandler;
import org.apache.qpid.amqp_1_0.transport.SendingLinkEndpoint;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.DeliveryState;
import org.apache.qpid.amqp_1_0.type.Source;

public class SendingLinkAttachment
{
    private final Session_1_0         _session;
    private final SendingLinkEndpoint _endpoint;

    public SendingLinkAttachment(final Session_1_0 session, final SendingLinkEndpoint endpoint)
    {
        _session = session;
        _endpoint = endpoint;
    }

    public Session_1_0 getSession()
    {
        return _session;
    }

    public SendingLinkEndpoint getEndpoint()
    {
        return _endpoint;
    }

    public Source getSource()
    {
        return getEndpoint().getSource();
    }

    public void setDeliveryStateHandler(final DeliveryStateHandler handler)
    {
        getEndpoint().setDeliveryStateHandler(handler);
    }

    public void updateDisposition(final Binary deliveryTag, final DeliveryState state, final boolean settled)
    {
        getEndpoint().updateDisposition(deliveryTag, state, settled);
    }
}
