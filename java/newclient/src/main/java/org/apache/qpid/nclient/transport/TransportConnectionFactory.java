package org.apache.qpid.nclient.transport;

import java.net.URISyntaxException;

import org.apache.qpid.nclient.core.PhaseContext;

public class TransportConnectionFactory
{
    public enum ConnectionType 
    {	
	TCP,VM
    }
    
    public static TransportConnection createTransportConnection(String url,ConnectionType type, PhaseContext ctx) throws URISyntaxException
    {
	return createTransportConnection(new AMQPConnectionURL(url),type,ctx);
	
    }
    
    public static TransportConnection createTransportConnection(ConnectionURL url,ConnectionType type, PhaseContext ctx)
    {
	switch (type)
	{
	    case TCP : default:
	    {
		return new TCPConnection(url,ctx);
	    }
	    
	    case VM :
	    {
		return new VMConnection(url,ctx);
	    }
	}
	
    }
}
