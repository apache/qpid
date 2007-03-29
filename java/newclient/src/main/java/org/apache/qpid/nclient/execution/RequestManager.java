/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.nclient.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQRequestBody;
import org.apache.qpid.framing.AMQResponseBody;
import org.apache.qpid.framing.RequestResponseMappingException;
import org.apache.qpid.nclient.amqp.event.AMQPMethodEvent;

public class RequestManager
{
    private static final Logger logger = Logger.getLogger(RequestManager.class);

    private int channel;
    
    /**
     * Used for logging and debugging only - allows the context of this instance
     * to be known.
     */
    private boolean serverFlag;
    private long connectionId;

    /**
     * Request and response frames must have a requestID and responseID which
     * indepenedently increment from 0 on a per-channel basis. These are the
     * counters, and contain the value of the next (not yet used) frame.
     */
    private long requestIdCount;

    /**
     * These keep track of the last requestId and responseId to be received.
     */
    private long lastProcessedResponseId;

    private ConcurrentHashMap<Long, CorrelationID> requestSentMap;

    public RequestManager(long connectionId, int channel, boolean serverFlag)
    {
        this.channel = channel;
        this.serverFlag = serverFlag;
        this.connectionId = connectionId;
        requestIdCount = 1L;
        lastProcessedResponseId = 0L;
        requestSentMap = new ConcurrentHashMap<Long, CorrelationID>();
    }

    // *** Functions to originate a request ***

    public AMQFrame sendRequest(AMQPMethodEvent evt)
    {
        long requestId = getNextRequestId(); // Get new request ID
        AMQFrame requestFrame = AMQRequestBody.createAMQFrame(channel, requestId,
            lastProcessedResponseId, evt.getMethod());
        if (logger.isDebugEnabled())
        {
            logger.debug((serverFlag ? "SRV[" : "CLI[") + connectionId + "," + channel +
                "] TX REQ: Req[" + requestId + " " + lastProcessedResponseId + "]; " + evt.getMethod());
        }
        requestSentMap.put(requestId, new CorrelationID(evt.getCorrelationId(), evt.getLocalCorrelationId()));
        return requestFrame;
    }

    public List<AMQPMethodEvent> responseReceived(AMQResponseBody responseBody)
        throws Exception
    {
        long requestIdStart = responseBody.getRequestId();
        long requestIdStop = requestIdStart + responseBody.getBatchOffset();
        if (logger.isDebugEnabled())
        {
            logger.debug((serverFlag ? "SRV[" : "CLI[") + connectionId + "," + channel + "] RX RES: " +
                responseBody + "; " + responseBody.getMethodPayload());
        }
        
        List<AMQPMethodEvent> events = new ArrayList<AMQPMethodEvent>();
        for (long requestId = requestIdStart; requestId <= requestIdStop; requestId++)
        {   
            if (requestSentMap.get(requestId) == null)
            {
                throw new RequestResponseMappingException(requestId,
                    "Failed to locate requestId " + requestId + " in requestSentMap.");
            }
            CorrelationID correlationID = requestSentMap.get(requestId);
            AMQPMethodEvent methodEvent = new AMQPMethodEvent(channel, responseBody.getMethodPayload(),
        	    correlationID.getSystemCorrelationID(),correlationID.getLocalCorrelationID());
            events.add(methodEvent);
            requestSentMap.remove(requestId);
        }
        lastProcessedResponseId = responseBody.getResponseId();
        return events;
    }

    // *** Management functions ***

    public int requestsMapSize()
    {
        return requestSentMap.size();
    }

    // *** Private helper functions ***

    private long getNextRequestId()
    {
        return requestIdCount++;
    }
    
    private class CorrelationID
    {
	// Use for the request/response stuff
	private long _systemCorrelationID;
	// used internally to track callbacks
	private long _localCorrelationID;
	
	CorrelationID(long systemCorrelationID,long localCorrelationID)
	{
	    _localCorrelationID = localCorrelationID;
	    _systemCorrelationID = systemCorrelationID;
	}

	public long getLocalCorrelationID()
	{
	    return _localCorrelationID;
	}

	public void setLocalCorrelationID(long correlationID)
	{
	    _localCorrelationID = correlationID;
	}

	public long getSystemCorrelationID()
	{
	    return _systemCorrelationID;
	}

	public void setSystemCorrelationID(long correlationID)
	{
	    _systemCorrelationID = correlationID;
	}
	
	
    }
} 
