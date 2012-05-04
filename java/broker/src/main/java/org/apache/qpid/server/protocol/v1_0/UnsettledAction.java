package org.apache.qpid.server.protocol.v1_0;

import org.apache.qpid.amqp_1_0.type.DeliveryState;

public interface UnsettledAction
{
    boolean process(DeliveryState state, Boolean settled);
}
