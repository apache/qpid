package org.apache.qpid.server.protocol;

import org.apache.qpid.protocol.ProtocolEngine;
import org.apache.qpid.protocol.ProtocolEngineFactory;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.qpid.transport.NetworkDriver;

public class AMQProtocolEngineFactory implements ProtocolEngineFactory
{
    private VirtualHostRegistry _vhosts;

    public AMQProtocolEngineFactory()
    {
        this(1);
    }
    
    public AMQProtocolEngineFactory(Integer port)
    {
        _vhosts = ApplicationRegistry.getInstance(port).getVirtualHostRegistry();
    }
   
    
    public ProtocolEngine newProtocolEngine(NetworkDriver networkDriver)
    {
        return new AMQProtocolEngine(_vhosts, networkDriver);
    }

}
