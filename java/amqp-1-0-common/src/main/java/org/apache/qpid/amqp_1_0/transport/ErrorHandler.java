package org.apache.qpid.amqp_1_0.transport;

import org.apache.qpid.amqp_1_0.type.transport.*;


public interface ErrorHandler
{
    void handleError(org.apache.qpid.amqp_1_0.type.transport.Error error);
}
