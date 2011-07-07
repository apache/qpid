package org.apache.qpid.transport;

import org.apache.mina.common.IoConnector;

public interface SocketConnectorFactory
{
    IoConnector newConnector();
}