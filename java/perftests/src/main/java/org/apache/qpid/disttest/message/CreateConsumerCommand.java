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

public class CreateConsumerCommand extends CreateParticpantCommand
{
    private boolean _isTopic;
    private boolean _isDurableSubscription;
    private boolean _isBrowsingSubscription;
    private String _selector;
    private boolean _noLocal;
    private boolean _synchronous;
    private long _receiveTimeout = 5000;


    public CreateConsumerCommand()
    {
        super(CommandType.CREATE_CONSUMER);
    }

    public boolean isDurableSubscription()
    {
        return _isDurableSubscription;
    }

    public void setDurableSubscription(final boolean isDurableSubscription)
    {
        this._isDurableSubscription = isDurableSubscription;
    }

    public boolean isBrowsingSubscription()
    {
        return _isBrowsingSubscription;
    }

    public void setBrowsingSubscription(final boolean isBrowsingSubscription)
    {
        _isBrowsingSubscription = isBrowsingSubscription;
    }

    public String getSelector()
    {
        return _selector;
    }

    public void setSelector(final String selector)
    {
        this._selector = selector;
    }

    public boolean isNoLocal()
    {
        return _noLocal;
    }

    public void setNoLocal(final boolean noLocal)
    {
        this._noLocal = noLocal;
    }

    public boolean isTopic()
    {
        return _isTopic;
    }

    public void setTopic(boolean isTopic)
    {
        this._isTopic = isTopic;
    }

    public boolean isSynchronous()
    {
        return _synchronous;
    }

    public void setSynchronous(boolean synchronous)
    {
        _synchronous = synchronous;
    }

    public void setReceiveTimeout(long receiveTimeout)
    {
        _receiveTimeout  = receiveTimeout;

    }

    public long getReceiveTimeout()
    {
        return _receiveTimeout;
    }
}
