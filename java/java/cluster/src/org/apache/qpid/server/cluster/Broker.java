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
package org.apache.qpid.server.cluster;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.cluster.util.LogMessage;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * An implementation of the Member interface (through which data is sent to other
 * peers in the cluster). This class provides a base from which subclasses can
 * inherit some common behaviour for broadcasting GroupRequests and sending methods
 * that may expect a response. It also extends the Member abstraction to support
 * a richer set of operations that are useful within the package but should not be
 * exposed outside of it.
 *
 */
abstract class Broker extends SimpleMemberHandle implements Member
{
    private static final Logger _logger = Logger.getLogger(Broker.class);
    private static final int DEFAULT_CHANNEL = 1;
    private static final int START_CHANNEL = 2;
    private static final int END_CHANNEL = 10000;


    private MemberFailureListener _listener;
    //a wrap-around counter to allocate _requests a unique channel:
    private int _nextChannel = START_CHANNEL;
    //outstanding _requests:
    private final Map<Integer, ResponseHandler> _requests = new HashMap<Integer, ResponseHandler>();

    Broker(String host, int port)
    {
        super(host, port);
    }

    /**
     * Allows a listener to be registered that will receive callbacks when communication
     * to the peer this broker instance represents fails.
     * @param listener the callback to be notified of failures
     */
    public void addFailureListener(MemberFailureListener listener)
    {
        _listener = listener;
    }

    /**
     * Allows subclasses to signal comunication failures
     */
    protected void failed()
    {
        if (_listener != null)
        {
            _listener.failed(this);
        }
    }

    /**
     * Subclasses should call this on receiving message responses from the remote
     * peer.  They are matched to any outstanding request they might be response
     * to, with the completion and callback of that request being managed if
     * required.
     *
     * @param channel the channel on which the method was received
     * @param response the response received
     * @return  true if the response matched an outstanding request
     */
    protected synchronized boolean handleResponse(int channel, AMQMethodBody response)
    {
        ResponseHandler request = _requests.get(channel);
        if (request == null)
        {
            if(!_requests.isEmpty())
            {
                _logger.warn(new LogMessage("[next channel={3, integer}]: Response {0} on channel {1, integer} failed to match outstanding requests: {2}", response, channel, _requests, _nextChannel));
            }
            return false;
        }
        else
        {
            request.responded(response);
            return true;
        }
    }

    /**
     * Called when this broker is excluded from the group. Any requests made on
     * it are informed this member has left the group.
     */
    synchronized void remove()
    {
        for (ResponseHandler r : _requests.values())
        {
            r.removed();
        }
    }

    /**
     * Engages this broker in the specified group request
     *
     * @param request the request being made to a group of brokers
     * @throws AMQException if there is any failure
     */
    synchronized void invoke(GroupRequest request) throws AMQException
    {
        int channel = nextChannel();
        _requests.put(channel, new GroupRequestAdapter(request, channel));
        request.send(channel, this);
    }

    /**
     * Sends a message to the remote peer and undertakes to notify the specified
     * handler of the response.
     *
     * @param msg the message to send
     * @param handler the callback to notify of responses (or the removal of this broker
     * from the group)
     * @throws AMQException
     */
    synchronized void send(Sendable msg, ResponseHandler handler) throws AMQException
    {
        int channel;
        if (handler != null)
        {
            channel = nextChannel();
            _requests.put(channel, new RemovingWrapper(handler, channel));
        }
        else
        {
            channel = DEFAULT_CHANNEL;
        }

        msg.send(channel, this);
    }

    private int nextChannel()
    {
        int channel = _nextChannel++;
        if(_nextChannel >= END_CHANNEL)
        {
            _nextChannel = START_CHANNEL;
        }
        return channel;
    }

    /**
     * extablish connection without handling redirect
     */
    abstract boolean connect() throws IOException, InterruptedException;

    /**
     * Start connection process, including replay
     */
    abstract void connectAsynch(Iterable<AMQMethodBody> msgs);

    /**
     * Replay messages to the remote peer this instance represents. These messages
     * must be sent before any others whose transmission is requested through send() etc.
     *
     * @param msgs
     */
    abstract void replay(Iterable<AMQMethodBody> msgs);

    /**
     * establish connection, handling redirect if required...
     */
    abstract Broker connectToCluster() throws IOException, InterruptedException;

    private class GroupRequestAdapter implements ResponseHandler
    {
        private final GroupRequest request;
        private final int channel;

        GroupRequestAdapter(GroupRequest request, int channel)
        {
            this.request = request;
            this.channel = channel;
        }

        public void responded(AMQMethodBody response)
        {
            request.responseReceived(Broker.this, response);
            _requests.remove(channel);
        }

        public void removed()
        {
            request.removed(Broker.this);
        }

        public String toString()
        {
            return "GroupRequestAdapter{" + channel + ", " + request + "}";
        }
    }

    private class RemovingWrapper implements ResponseHandler
    {
        private final ResponseHandler handler;
        private final int channel;

        RemovingWrapper(ResponseHandler handler, int channel)
        {
            this.handler = handler;
            this.channel = channel;
        }

        public void responded(AMQMethodBody response)
        {
            handler.responded(response);
            _requests.remove(channel);
        }

        public void removed()
        {
            handler.removed();
        }

        public String toString()
        {
            return "RemovingWrapper{" + channel + ", " + handler + "}";
        }
    }
}
