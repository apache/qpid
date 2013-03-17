package org.apache.qpid.amqp_1_0.client;

public class ConnectionException extends Exception
{
    public ConnectionException(Throwable cause)
    {
        super(cause);
    }

    ConnectionException()
    {

    }
}
