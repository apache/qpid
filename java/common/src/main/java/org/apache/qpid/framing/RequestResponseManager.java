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

import java.util.Iterator;
import java.util.TreeMap;

import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQProtocolWriter;

public class RequestResponseManager
{
	private int channel;
    AMQProtocolWriter protocolSession;

	/**
     * Determines the batch behaviour of the manager.
     *
     * Responses are sent to the RequestResponseManager through sendResponse().
     * These may be internally stored/accumulated for batching purposes, depending
     * on the batching strategy/mode of the RequestResponseManager.
     *
     * The following modes are possibe:
     *
     * NONE: Each request results in an immediate single response, no batching
     *     takes place.
     * DELAY_FIXED: Waits until a fixed period has passed to batch
     *     accumulated responses. An optional fixed threshold may be set, which
     *     if reached or exceeded within the delay period will trigger the batch. (TODO)
     * MANUAL: No response is sent until it is explicitly released by calling
     *     function xxxx(). (TODO)
     */
	public enum batchResponseModeEnum { NONE }
    private batchResponseModeEnum batchResponseMode;
    
    /**
     * Request and response frames must have a requestID and responseID which
     * indepenedently increment from 0 on a per-channel basis. These are the
     * counters, and contain the value of the next (not yet used) frame.
     */
    private long requestIdCount;
    private long responseIdCount;
    
    /**
     * These keep track of the last requestId and responseId to be received.
     */
    private long lastReceivedRequestId;
    private long lastReceivedResponseId;
    
    /**
     * Last requestID sent in a response (for batching)
     */
    private long lastSentRequestId;
    
    private class ResponseStatus implements Comparable<ResponseStatus>
    {
     	public long requestId;
        public AMQMethodBody responseMethodBody;
         
        public ResponseStatus(long requestId)
        {
	       	this.requestId = requestId;
        	responseMethodBody = null;
        }
         
        public int compareTo(ResponseStatus o)
        {
        	return (int)(requestId - o.requestId);
        }
    }
    
    private TreeMap<Long, AMQResponseCallback> requestSentMap;
    private TreeMap<Long, ResponseStatus> responseMap;
    
	public RequestResponseManager(int channel, AMQProtocolWriter protocolSession)
    {
    	this.channel = channel;
        this.protocolSession = protocolSession;
    	requestIdCount = 1L;
        responseIdCount = 1L;
        lastReceivedRequestId = 0L;
        lastReceivedResponseId = 0L;
        requestSentMap = new TreeMap<Long, AMQResponseCallback>();
        responseMap = new TreeMap<Long, ResponseStatus>();
    }
    
    // *** Functions to originate a request ***
    
    public long sendRequest(AMQMethodBody requestMethodBody, AMQResponseCallback responseCallback)
    {
    	long requestId = getRequestId(); // Get new request ID
    	AMQFrame requestFrame = AMQRequestBody.createAMQFrame(channel, requestId,
        	lastReceivedResponseId, requestMethodBody);
        protocolSession.writeFrame(requestFrame);
        requestSentMap.put(requestId, responseCallback);
        return requestId;
    }
    
    public void responseReceived(AMQResponseBody responseBody) throws AMQException
    {
    	lastReceivedResponseId = responseBody.getResponseId();
        long requestIdStart = responseBody.getRequestId();
        long requestIdStop = requestIdStart + responseBody.getBatchOffset();
        for (long requestId = requestIdStart; requestId <= requestIdStop; requestId++)
        {
        	AMQResponseCallback responseCallback = requestSentMap.get(requestId);
            if (responseCallback == null)
            	throw new AMQException("Failed to locate requestId " + requestId +
                	" in requestSentMap.");
            responseCallback.responseFrameReceived(responseBody);
            requestSentMap.remove(requestId);
        }
    }

	// *** Functions to handle an incoming request ***
    
    public void requestReceived(AMQRequestBody requestBody)
    {
    	long requestId = requestBody.getRequestId();
        // TODO: responseMark is used in HA, but until then, ignore...
        long responseMark = requestBody.getResponseMark();
	    lastReceivedRequestId = requestId;
        responseMap.put(requestId, new ResponseStatus(requestId));
        
        // TODO: Initiate some action based on the MethodBody - like send to handlers,
        // but how to do this in a way that will work for both client and server?
    }
    
    public void sendResponse(long requestId, AMQMethodBody responseMethodBody) throws AMQException
    {
    	ResponseStatus responseStatus = responseMap.get(requestId);
        if (responseStatus == null)
        	throw new AMQException("Failed to locate requestId " + requestId +
            	" in responseMap.");
        if (responseStatus.responseMethodBody != null)
        	throw new AMQException("RequestId " + requestId + " already has a response.");
        responseStatus.responseMethodBody = responseMethodBody;
        doBatches();
    }
    
    // *** Management functions ***

	public batchResponseModeEnum getBatchResponseMode()
    {
    	return batchResponseMode;
    }
    
    public void setBatchResponseMode(batchResponseModeEnum batchResponseMode)
    {
    	if (this.batchResponseMode != batchResponseMode)
        {
    		this.batchResponseMode = batchResponseMode;
        	doBatches();
        }
    }
    
    // *** Private helper functions ***
    
    private long getRequestId()
    {
    	return requestIdCount++;
    }
    
    private long getResponseId()
    {
    	return responseIdCount++;
    }
    
    private void doBatches()
    {
    	switch (batchResponseMode)
        {
        	case NONE:
            	Iterator<Long> lItr = responseMap.keySet().iterator();
            	while (lItr.hasNext())
                {
                	long requestId = lItr.next();
                	ResponseStatus responseStatus = responseMap.get(requestId);
                	if (responseStatus.responseMethodBody != null)
                    {
                    	sendResponseBatch(requestId, 0, responseStatus.responseMethodBody);
                        lItr.remove();
                    }
                }
            	break;
                
            // TODO: Add additional batch mode handlers here...
			// case DELAY_FIXED:
			// case MANUAL:
        }
    }
    
    private void sendResponseBatch(long firstRequestId, int numAdditionalRequests,
    	AMQMethodBody responseMethodBody)
    {
    	long responseId = getResponseId(); // Get new request ID
    	AMQFrame responseFrame = AMQResponseBody.createAMQFrame(channel, responseId,
        	firstRequestId, numAdditionalRequests, responseMethodBody);
        protocolSession.writeFrame(responseFrame);
    }
} 
