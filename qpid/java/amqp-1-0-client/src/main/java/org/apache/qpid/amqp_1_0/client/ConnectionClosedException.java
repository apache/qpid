package org.apache.qpid.amqp_1_0.client;

public class ConnectionClosedException extends ConnectionErrorException
{

    public ConnectionClosedException(org.apache.qpid.amqp_1_0.type.transport.Error remoteError)
    {
        super(remoteError);
    }

}
