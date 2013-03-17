package org.apache.qpid.amqp_1_0.client;

import org.apache.qpid.amqp_1_0.type.transport.Error;

public class ConnectionErrorException extends ConnectionException
{
    protected final Error _remoteError;

    public ConnectionErrorException(Error remoteError)
    {
        super();
        _remoteError = remoteError;
    }

    public org.apache.qpid.amqp_1_0.type.transport.Error getRemoteError()
    {
        return _remoteError;
    }
}
