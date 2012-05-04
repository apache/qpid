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

import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_BROWSIING_SUBSCRIPTION;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_DURABLE_SUBSCRIPTION;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_NO_LOCAL;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_SELECTOR;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_SYNCHRONOUS_CONSUMER;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_TOPIC;

public class ConsumerParticipantResult extends ParticipantResult
{
    private boolean _topic;
    private boolean _durableSubscription;
    private boolean _browsingSubscription;
    private boolean _selector;
    private boolean _noLocal;
    private boolean _synchronousConsumer;

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


    @OutputAttribute(attribute=IS_BROWSIING_SUBSCRIPTION)
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
}
