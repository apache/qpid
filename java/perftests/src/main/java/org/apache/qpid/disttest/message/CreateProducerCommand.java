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
 *
 */
package org.apache.qpid.disttest.message;

public class CreateProducerCommand extends CreateParticpantCommand
{
    private int _deliveryMode;
    private int _messageSize;
    private int _priority;
    private long _timeToLive;
    private long _interval;
    private long _startDelay;
    private String _messageProviderName;

    public CreateProducerCommand()
    {
        super(CommandType.CREATE_PRODUCER);
    }

    public int getMessageSize()
    {
        return _messageSize;
    }

    public void setMessageSize(final int messageSize)
    {
        this._messageSize = messageSize;
    }

    public int getPriority()
    {
        return _priority;
    }

    public void setPriority(final int priority)
    {
        this._priority = priority;
    }

    public int getDeliveryMode()
    {
        return _deliveryMode;
    }

    public void setDeliveryMode(final int deliveryMode)
    {
        this._deliveryMode = deliveryMode;
    }

    public long getTimeToLive()
    {
        return _timeToLive;
    }

    public void setTimeToLive(final long timeToLive)
    {
        this._timeToLive = timeToLive;
    }

    public long getInterval()
    {
        return _interval;
    }

    public void setInterval(long interval)
    {
        this._interval = interval;
    }

    public long getStartDelay()
    {
        return _startDelay;
    }

    public void setStartDelay(long startDelay)
    {
        this._startDelay = startDelay;
    }

    public String getMessageProviderName()
    {
        return _messageProviderName;
    }

    public void setMessageProviderName(String messageProviderName)
    {
        this._messageProviderName = messageProviderName;
    }
}
