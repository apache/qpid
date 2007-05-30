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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.AMQRequestBody;
import org.apache.qpid.framing.AMQResponseBody;
import org.apache.qpid.framing.RequestResponseMappingException;
import org.apache.qpid.nclient.amqp.event.AMQPMethodEvent;
import org.apache.qpid.nclient.config.ClientConfiguration;
import org.apache.qpid.nclient.core.AMQPConstants;

public class ResponseManager
{
    private static final Logger logger = Logger.getLogger(ResponseManager.class);

    private int channel;
    
    /**
     * Used for logging and debugging only - allows the context of this instance
     * to be known.
     */
    private boolean serverFlag;
    private long connectionId;

    private int maxAccumulatedResponses = 20; // Default

    /**
     * Request and response frames must have a requestID and responseID which
     * indepenedently increment from 0 on a per-channel basis. These are the
     * counters, and contain the value of the next (not yet used) frame.
     */
    private long responseIdCount;

    /**
     * These keep track of the last requestId and responseId to be received.
     */
    private long lastReceivedRequestId;

    /**
     * Last requestID sent in a response (for batching)
     */
    private long lastSentRequestId;

    private class ResponseStatus implements Comparable<ResponseStatus>
    {
        private long requestId;
        private AMQMethodBody responseMethodBody;

        public ResponseStatus(long requestId)
        {
            this.requestId = requestId;
            responseMethodBody = null;
        }

        public int compareTo(ResponseStatus o)
        {
            return (int)(requestId - o.requestId);
        }
        
        public String toString()
        {
            // Need to define this
        	return "";
        }
    }

    private ConcurrentHashMap<Long, ResponseStatus> responseMap;

    public ResponseManager(long connectionId, int channel, boolean serverFlag)
    {
        this.channel = channel;
        this.serverFlag = serverFlag;
        this.connectionId = connectionId;
        responseIdCount = 1L;
        lastReceivedRequestId = 0L;
        maxAccumulatedResponses = ClientConfiguration.get().getInt(AMQPConstants.MAX_ACCUMILATED_RESPONSES);
        responseMap = new ConcurrentHashMap<Long, ResponseStatus>();
    }

    // *** Functions to handle an incoming request ***

    public AMQPMethodEvent requestReceived(AMQRequestBody requestBody) throws Exception
    {
        long requestId = requestBody.getRequestId();
        if (logger.isDebugEnabled())
        {
            logger.debug((serverFlag ? "SRV[" : "CLI[") + connectionId + "," + channel + "] RX REQ: " + 
                requestBody + "; " + requestBody.getMethodPayload());
        }
        long responseMark = requestBody.getResponseMark();
        lastReceivedRequestId = requestId;
        responseMap.put(requestId, new ResponseStatus(requestId));
        
        AMQPMethodEvent methodEvent = new AMQPMethodEvent(channel,
            requestBody.getMethodPayload(), requestId);
        
        return methodEvent;
    }

    public List<AMQFrame> sendResponse(AMQPMethodEvent evt)
        throws RequestResponseMappingException
    {
    	long requestId = evt.getCorrelationId();
    	AMQMethodBody responseMethodBody = evt.getMethod();
    	
        if (logger.isDebugEnabled())
        {
            logger.debug((serverFlag ? "SRV[" : "CLI[") + connectionId + "," + channel +
                "] TX RES: Res[# " + requestId + "]; " + responseMethodBody);
        }
        
        ResponseStatus responseStatus = responseMap.get(requestId);
        if (responseStatus == null)
        {
            throw new RequestResponseMappingException(requestId,
                "Failed to locate requestId " + requestId + " in responseMap." + responseMap);
        }
        if (responseStatus.responseMethodBody != null)
        {
            throw new RequestResponseMappingException(requestId, "RequestId " +
                requestId + " already has a response in responseMap.");
        }       
        responseStatus.responseMethodBody = responseMethodBody;
        return doBatches();
    }

    // *** Management functions ***

    /**
     * Sends batched responses - i.e. all those members of responseMap that have
     * received a response.
     */
    public synchronized List<AMQFrame> doBatches()
    {
        long startRequestId = 0;
        int numAdditionalRequestIds = 0;
        Class responseMethodBodyClass = null;
        List<AMQFrame> frames = new ArrayList<AMQFrame>();
        Iterator<Long> lItr = responseMap.keySet().iterator();
        while (lItr.hasNext())
        {
            long requestId = lItr.next();
            ResponseStatus responseStatus = responseMap.get(requestId);
            if (responseStatus.responseMethodBody != null)
            {
               frames.add(sendResponseBatchFrame(requestId, 0, responseStatus.responseMethodBody));
               lItr.remove();
            }
        }
        
        return frames;
    }

    /**
     * Total number of entries in the responseMap - including both those that
     * are outstanding (i.e. no response has been received) and those that are
     * batched (those for which responses have been received but have not yet
     * been collected together and sent).
     */
    public int responsesMapSize()
    {
        return responseMap.size();
    }

    /**
     * As the responseMap may contain both outstanding responses (those with
     * ResponseStatus.responseMethodBody still null) and responses waiting to
     * be batched (those with ResponseStatus.responseMethodBody not null), we
     * need to count only those in the map with responseMethodBody null.
     */
    public int outstandingResponses()
    {
        int cnt = 0;
        for (Long requestId : responseMap.keySet())
        {
            if (responseMap.get(requestId).responseMethodBody == null)
                cnt++;
        }
        return cnt;
    }

    /**
     * As the responseMap may contain both outstanding responses (those with
     * ResponseStatus.responseMethodBody still null) and responses waiting to
     * be batched (those with ResponseStatus.responseMethodBody not null), we
     * need to count only those in the map with responseMethodBody not null.
     */
    public int batchedResponses()
    {
        int cnt = 0;
        for (Long requestId : responseMap.keySet())
        {
            if (responseMap.get(requestId).responseMethodBody != null)
                cnt++;
        }
        return cnt;
    }

    // *** Private helper functions ***

    private long getNextResponseId()
    {
        return responseIdCount++;
    }

    private AMQFrame sendResponseBatchFrame(long firstRequestId, int numAdditionalRequests,
        AMQMethodBody responseMethodBody)
    {
        long responseId = getNextResponseId(); // Get new response ID
        AMQFrame responseFrame = AMQResponseBody.createAMQFrame(channel, responseId,
            firstRequestId, numAdditionalRequests, responseMethodBody);
        return responseFrame;
    }
} 
