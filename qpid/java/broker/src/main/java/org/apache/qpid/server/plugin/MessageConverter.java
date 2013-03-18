package org.apache.qpid.server.plugin;

import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.virtualhost.VirtualHost;

public interface MessageConverter<M extends ServerMessage, N extends ServerMessage>
{
    Class<M> getInputClass();
    Class<N> getOutputClass();

    N convert(M message, VirtualHost vhost);
}
