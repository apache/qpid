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
import org.apache.qpid.framing.AMQMethodBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a method sent to a group of Member instances. Manages the responses,
 * completion and callback.
 *
 */
class GroupRequest
{
    private final Map<Member, AMQMethodBody> _responses = new HashMap<Member, AMQMethodBody>();
    private final List<Member> _brokers = new ArrayList<Member>();
    private boolean _sent;

    private final Sendable _request;
    private final BroadcastPolicy _policy;
    private final GroupResponseHandler _callback;

    GroupRequest(Sendable request, BroadcastPolicy policy, GroupResponseHandler callback)
    {
        _request = request;
        _policy = policy;
        _callback = callback;
    }

    void send(int channel, Member session) throws AMQException
    {
        _brokers.add(session);
        _request.send(channel, session);
    }

    boolean finishedSend()
    {
        _sent = true;
        return checkCompletion();
    }

    public boolean responseReceived(Member broker, AMQMethodBody response)
    {
        _responses.put(broker, response);
        return checkCompletion();
    }

    public boolean removed(Member broker)
    {
        _brokers.remove(broker);
        return checkCompletion();
    }

    private synchronized boolean checkCompletion()
    {
        return isComplete() && callback();
    }

    boolean isComplete()
    {
        return _sent && _policy != null && _policy.isComplete(_responses.size(), _brokers.size());
    }

    boolean callback()
    {
        _callback.response(getResults(), _brokers);
        return true;
    }

    List<AMQMethodBody> getResults()
    {
        List<AMQMethodBody> results = new ArrayList<AMQMethodBody>(_brokers.size());
        for (Member b : _brokers)
        {
            results.add(_responses.get(b));
        }
        return results;
    }

    public String toString()
    {
        return "GroupRequest{request=" + _request +", brokers=" + _brokers + ", responses=" + _responses + "}";
    }
}
