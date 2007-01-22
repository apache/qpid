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

import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;

import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQMethodListener;
import org.apache.qpid.protocol.AMQProtocolWriter;

public class RequestManager
{
    private static final Logger logger = Logger.getLogger(RequestManager.class);

    private int channel;
    private AMQProtocolWriter protocolWriter;
    
    /**
     * Used for logging and debugging only - allows the context of this instance
     * to be known.
     */
    private boolean serverFlag;

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

    private ConcurrentHashMap<Long, AMQMethodListener> requestSentMap;

    public RequestManager(int channel, AMQProtocolWriter protocolWriter, boolean serverFlag)
    {
        this.channel = channel;
        this.protocolWriter = protocolWriter;
        this.serverFlag = serverFlag;
        requestIdCount = 1L;
        lastProcessedResponseId = 0L;
        requestSentMap = new ConcurrentHashMap<Long, AMQMethodListener>();
    }

    // *** Functions to originate a request ***

    public long sendRequest(AMQMethodBody requestMethodBody,
        AMQMethodListener methodListener)
    {
        long requestId = getNextRequestId(); // Get new request ID
        AMQFrame requestFrame = AMQRequestBody.createAMQFrame(channel, requestId,
            lastProcessedResponseId, requestMethodBody);
        requestSentMap.put(requestId, methodListener);
        protocolWriter.writeFrame(requestFrame);
        if (logger.isDebugEnabled())
        {
            logger.debug((serverFlag ? "SRV" : "CLI") + " TX REQ: ch=" + channel +
                " Req[" + requestId + " " + lastProcessedResponseId + "]; " + requestMethodBody);
        }
        return requestId;
    }

    public void responseReceived(AMQResponseBody responseBody)
        throws Exception
    {
        long requestIdStart = responseBody.getRequestId();
        long requestIdStop = requestIdStart + responseBody.getBatchOffset();
        if (logger.isDebugEnabled())
        {
            logger.debug((serverFlag ? "SRV" : "CLI") + " RX RES: ch=" + channel +
                " " + responseBody + "; " + responseBody.getMethodPayload());
        }
        for (long requestId = requestIdStart; requestId <= requestIdStop; requestId++)
        {
            AMQMethodListener methodListener = requestSentMap.get(requestId);
            if (methodListener == null)
                throw new RequestResponseMappingException(requestId,
                    "Failed to locate requestId " + requestId + " in requestSentMap.");
            AMQMethodEvent methodEvent = new AMQMethodEvent(channel, responseBody.getMethodPayload(),
                requestId);
            methodListener.methodReceived(methodEvent);
            requestSentMap.remove(requestId);
        }
        lastProcessedResponseId = responseBody.getResponseId();
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
