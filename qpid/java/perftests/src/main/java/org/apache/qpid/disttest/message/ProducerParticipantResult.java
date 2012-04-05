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

import static org.apache.qpid.disttest.message.ParticipantAttribute.DELIVERY_MODE;
import static org.apache.qpid.disttest.message.ParticipantAttribute.PRIORITY;
import static org.apache.qpid.disttest.message.ParticipantAttribute.PRODUCER_INTERVAL;
import static org.apache.qpid.disttest.message.ParticipantAttribute.PRODUCER_START_DELAY;
import static org.apache.qpid.disttest.message.ParticipantAttribute.TIME_TO_LIVE;

public class ProducerParticipantResult extends ParticipantResult
{
    private int _priority;
    private long _timeToLive;
    private long _startDelay;
    private long _interval;
    private int _deliveryMode;
    public ProducerParticipantResult()
    {
        super(CommandType.PRODUCER_PARTICIPANT_RESULT);
    }

    public ProducerParticipantResult(String participantName)
    {
        this();
        setParticipantName(participantName);
    }

    @OutputAttribute(attribute=PRIORITY)
    public int getPriority()
    {
        return _priority;
    }

    public void setPriority(int priority)
    {
        _priority = priority;
    }

    @OutputAttribute(attribute=TIME_TO_LIVE)
    public long getTimeToLive()
    {
        return _timeToLive;
    }

    public void setTimeToLive(long timeToLive)
    {
        _timeToLive = timeToLive;
    }

    @OutputAttribute(attribute=PRODUCER_START_DELAY)
    public long getStartDelay()
    {
        return _startDelay;
    }

    public void setStartDelay(long startDelay)
    {
        _startDelay = startDelay;
    }

    @OutputAttribute(attribute=PRODUCER_INTERVAL)
    public long getInterval()
    {
        return _interval;
    }

    public void setInterval(long producerInterval)
    {
        _interval = producerInterval;
    }

    @OutputAttribute(attribute=DELIVERY_MODE)
    public int getDeliveryMode()
    {
        return _deliveryMode;
    }

    public void setDeliveryMode(int deliveryMode)
    {
        this._deliveryMode = deliveryMode;
    }

}
