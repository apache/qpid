package org.apache.qpid.nclient.model;

import org.apache.qpid.framing.AMQMethodBody;

/**
 * This class is exactly the same as the AMQMethod event.
 * Except I renamed requestId to corelationId, so I could use it both ways.
 * 
 * I didn't want to modify anything in common so that there is no
 * impact on the existing code.
 *
 */
public class AMQPMethodEvent <M extends AMQMethodBody> {
	
	private final M _method;
    private final int _channelId;
    private final long _correlationId;
    private long _localCorrletionId = 0;
    
    public AMQPMethodEvent(int channelId, M method, long correlationId,long localCorrletionId)
    {
        _channelId = channelId;
        _method = method;
        _correlationId = correlationId;   
        _localCorrletionId = localCorrletionId;
    }
    
    public AMQPMethodEvent(int channelId, M method, long correlationId)
    {
        _channelId = channelId;
        _method = method;
        _correlationId = correlationId;        
    }

    public M getMethod()
    {
        return _method;
    }

    public int getChannelId()
    {
        return _channelId;
    }

    public long getCorrelationId()
    {
        return _correlationId;
    }
    
    public long getLocalCorrelationId()
    {
        return _localCorrletionId;
    }

    public String toString()
    {
        StringBuilder buf = new StringBuilder("Method event: \n");
        buf.append("Channel id: \n").append(_channelId);
        buf.append("Method: \n").append(_method);
        buf.append("Request Id: ").append(_correlationId);
        buf.append("Local Correlation Id: ").append(_localCorrletionId);
        return buf.toString();
    }
}
