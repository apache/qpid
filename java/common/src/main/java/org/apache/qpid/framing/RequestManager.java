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
package org.apache.qpid.framing;

import java.util.Hashtable;

import org.apache.qpid.protocol.AMQProtocolWriter;

public class RequestManager
{
	private int channel;
    AMQProtocolWriter protocolSession;
    
    /**
     * Request and response frames must have a requestID and responseID which
     * indepenedently increment from 0 on a per-channel basis. These are the
     * counters, and contain the value of the next (not yet used) frame.
     */
    private long requestIdCount;
    
    /**
     * These keep track of the last requestId and responseId to be received.
     */
    private long lastReceivedResponseId;
            
    private Hashtable<Long, AMQResponseCallback> requestSentMap;
    
	public RequestManager(int channel, AMQProtocolWriter protocolSession)
    {
    	this.channel = channel;
        this.protocolSession = protocolSession;
    	requestIdCount = 1L;
        lastReceivedResponseId = 0L;
        requestSentMap = new Hashtable<Long, AMQResponseCallback>();
    }
    
    // *** Functions to originate a request ***
    
    public long sendRequest(AMQMethodBody requestMethodBody,
    	AMQResponseCallback responseCallback)
    {
    	long requestId = getNextRequestId(); // Get new request ID
    	AMQFrame requestFrame = AMQRequestBody.createAMQFrame(channel, requestId,
        	lastReceivedResponseId, requestMethodBody);
        protocolSession.writeFrame(requestFrame);
        requestSentMap.put(requestId, responseCallback);
        return requestId;
    }
    
    public void responseReceived(AMQResponseBody responseBody)
    	throws RequestResponseMappingException
    {
    	lastReceivedResponseId = responseBody.getResponseId();
        long requestIdStart = responseBody.getRequestId();
        long requestIdStop = requestIdStart + responseBody.getBatchOffset();
        for (long requestId = requestIdStart; requestId <= requestIdStop; requestId++)
        {
        	AMQResponseCallback responseCallback = requestSentMap.get(requestId);
            if (responseCallback == null)
            	throw new RequestResponseMappingException(requestId,
                	"Failed to locate requestId " + requestId + " in requestSentMap.");
            responseCallback.responseFrameReceived(responseBody);
            requestSentMap.remove(requestId);
        }
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
} 
