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
import java.util.Hashtable;

import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQMethodListener;
import org.apache.qpid.protocol.AMQProtocolWriter;

public class ResponseManager
{
    private int channel;
    AMQMethodListener methodListener;
    AMQProtocolWriter protocolWriter;

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
    }

    private Hashtable<Long, ResponseStatus> responseMap;

    public ResponseManager(int channel, AMQMethodListener methodListener,
        AMQProtocolWriter protocolWriter)
    {
        this.channel = channel;
        this.methodListener = methodListener;
        this.protocolWriter = protocolWriter;
        responseIdCount = 1L;
        lastReceivedRequestId = 0L;
        responseMap = new Hashtable<Long, ResponseStatus>();
    }

    // *** Functions to handle an incoming request ***

    public void requestReceived(AMQRequestBody requestBody) throws Exception
    {
        long requestId = requestBody.getRequestId();
        // TODO: responseMark is used in HA, but until then, ignore...
        long responseMark = requestBody.getResponseMark();
        lastReceivedRequestId = requestId;
        responseMap.put(requestId, new ResponseStatus(requestId));
        // TODO: Update MethodEvent to use the RequestBody instead of MethodBody
        AMQMethodEvent methodEvent = new AMQMethodEvent(channel, requestBody.getMethodPayload());
        methodListener.methodReceived(methodEvent);
    }

    public void sendResponse(long requestId, AMQMethodBody responseMethodBody)
        throws RequestResponseMappingException
    {
        ResponseStatus responseStatus = responseMap.get(requestId);
        if (responseStatus == null)
            throw new RequestResponseMappingException(requestId,
                "Failed to locate requestId " + requestId + " in responseMap.");
        if (responseStatus.responseMethodBody != null)
            throw new RequestResponseMappingException(requestId, "RequestId " +
                requestId + " already has a response in responseMap.");
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
        long responseId = getNextResponseId(); // Get new request ID
        AMQFrame responseFrame = AMQResponseBody.createAMQFrame(channel, responseId,
            firstRequestId, numAdditionalRequests, responseMethodBody);
        protocolWriter.writeFrame(responseFrame);
    }
} 
