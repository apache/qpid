/*
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
 */
package org.apache.qpid.disttest.message;

import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_BROWSING_SUBSCRIPTION;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_DURABLE_SUBSCRIPTION;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_NO_LOCAL;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_SELECTOR;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_SYNCHRONOUS_CONSUMER;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_TOPIC;

import java.util.Collection;

public class ConsumerParticipantResult extends ParticipantResult
{
    private boolean _topic;
    private boolean _durableSubscription;
    private boolean _browsingSubscription;
    private boolean _selector;
    private boolean _noLocal;
    private boolean _synchronousConsumer;

    private Collection<Long> _messageLatencies;
    private long _minLatency;
    private long _maxLatency;
    private double _averageLatency;
    private double _latencyStandardDeviation;

    public ConsumerParticipantResult()
    {
        super(CommandType.CONSUMER_PARTICIPANT_RESULT);
    }

    public ConsumerParticipantResult(String participantName)
    {
        this();
        setParticipantName(participantName);
    }

    @OutputAttribute(attribute=IS_DURABLE_SUBSCRIPTION)
    public boolean isDurableSubscription()
    {
        return _durableSubscription;
    }

    public void setDurableSubscription(boolean durable)
    {
        _durableSubscription = durable;
    }


    @OutputAttribute(attribute=IS_BROWSING_SUBSCRIPTION)
    public boolean isBrowsingSubscription()
    {
        return _browsingSubscription;
    }

    public void setBrowsingSubscription(boolean browsingSubscription)
    {
        _browsingSubscription = browsingSubscription;
    }


    @OutputAttribute(attribute=IS_SELECTOR)
    public boolean isSelector()
    {
        return _selector;
    }

    public void setSelector(boolean selector)
    {
        _selector = selector;
    }


    @OutputAttribute(attribute=IS_NO_LOCAL)
    public boolean isNoLocal()
    {
        return _noLocal;

    }

    public void setNoLocal(boolean noLocal)
    {
        _noLocal = noLocal;
    }

    @OutputAttribute(attribute=IS_SYNCHRONOUS_CONSUMER)
    public boolean isSynchronousConsumer()
    {
        return _synchronousConsumer;
    }

    public void setSynchronousConsumer(boolean synchronousConsumer)
    {
        _synchronousConsumer = synchronousConsumer;
    }


    public void setTopic(boolean isTopic)
    {
        _topic = isTopic;
    }

    @OutputAttribute(attribute=IS_TOPIC)
    public boolean isTopic()
    {
        return _topic;
    }

    public Collection<Long> getMessageLatencies()
    {
        return _messageLatencies;
    }

    public void setMessageLatencies(Collection<Long> messageLatencies)
    {
        _messageLatencies = messageLatencies;
    }

    @Override
    @OutputAttribute(attribute=ParticipantAttribute.MIN_LATENCY)
    public long getMinLatency()
    {
        return _minLatency;
    }

    public void setMinLatency(long minLatency)
    {
        _minLatency = minLatency;
    }

    @Override
    @OutputAttribute(attribute=ParticipantAttribute.MAX_LATENCY)
    public long getMaxLatency()
    {
        return _maxLatency;
    }

    public void setMaxLatency(long maxLatency)
    {
        _maxLatency = maxLatency;
    }

    @Override
    @OutputAttribute(attribute=ParticipantAttribute.AVERAGE_LATENCY)
    public double getAverageLatency()
    {
        return _averageLatency;
    }

    public void setAverageLatency(double averageLatency)
    {
        _averageLatency = averageLatency;
    }

    @Override
    @OutputAttribute(attribute=ParticipantAttribute.LATENCY_STANDARD_DEVIATION)
    public double getLatencyStandardDeviation()
    {
        return _latencyStandardDeviation;
    }

    public void setLatencyStandardDeviation(double latencyStandardDeviation)
    {
        _latencyStandardDeviation = latencyStandardDeviation;
    }

}
