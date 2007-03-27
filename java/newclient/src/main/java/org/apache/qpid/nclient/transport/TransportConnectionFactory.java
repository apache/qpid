package org.apache.qpid.nclient.transport;

import java.net.URISyntaxException;

public class TransportConnectionFactory
{
    public enum ConnectionType 
    {	
	TCP,VM
    }
    
    public static TransportConnection createTransportConnection(String url,ConnectionType type) throws URISyntaxException
    {
	return createTransportConnection(new AMQPConnectionURL(url),type);
	
    }
    
    public static TransportConnection createTransportConnection(ConnectionURL url,ConnectionType type)
    {
	switch (type)
	{
	    case TCP : default:
	    {
		return createTCPConnection(url);
	    }
	    
	    case VM :
	    {
		return createVMConnection(url);
	    }
	}
	
    }

    private static TransportConnection createTCPConnection(ConnectionURL url)
    {
	return new TCPConnection(url);
    }
    
    private static TransportConnection createVMConnection(ConnectionURL url)
    {
	return null;
    }
}
