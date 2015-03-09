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
package org.apache.qpid.server.management.amqp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.consumer.ConsumerTarget;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.message.internal.InternalMessage;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.util.StateChangeListener;

class ManagementNodeConsumer implements ConsumerImpl
{
    private final long _id = ConsumerImpl.CONSUMER_NUMBER_GENERATOR.getAndIncrement();
    private final ManagementNode _managementNode;
    private final List<ManagementResponse> _queue = Collections.synchronizedList(new ArrayList<ManagementResponse>());
    private final ConsumerTarget _target;
    private final String _name;
    private final StateChangeListener<ConsumerTarget, ConsumerTarget.State> _targetChangeListener = new TargetChangeListener();


    public ManagementNodeConsumer(final String consumerName, final ManagementNode managementNode, ConsumerTarget target)
    {
        _name = consumerName;
        _managementNode = managementNode;
        _target = target;
        target.addStateListener(_targetChangeListener);
    }

    @Override
    public void externalStateChange()
    {

    }

    @Override
    public long getBytesOut()
    {
        return 0;
    }

    @Override
    public long getMessagesOut()
    {
        return 0;
    }

    @Override
    public long getUnacknowledgedBytes()
    {
        return 0;
    }

    @Override
    public long getUnacknowledgedMessages()
    {
        return 0;
    }

    @Override
    public AMQSessionModel getSessionModel()
    {
        return _target.getSessionModel();
    }

    @Override
    public MessageSource getMessageSource()
    {
        return _managementNode;
    }

    @Override
    public long getConsumerNumber()
    {
        return _id;
    }

    @Override
    public boolean isSuspended()
    {
        return false;
    }

    @Override
    public boolean isClosed()
    {
        return false;
    }

    @Override
    public boolean acquires()
    {
        return true;
    }

    @Override
    public boolean seesRequeues()
    {
        return false;
    }

    @Override
    public void close()
    {
    }

    @Override
    public boolean trySendLock()
    {
        return _target.trySendLock();
    }

    @Override
    public void getSendLock()
    {
        _target.getSendLock();
    }

    @Override
    public void releaseSendLock()
    {
        _target.releaseSendLock();
    }


    @Override
    public boolean isActive()
    {
        return false;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public void flush()
    {

    }

    @Override
    public ConsumerTarget getTarget()
    {
        return _target;
    }

    ManagementNode getManagementNode()
    {
        return _managementNode;
    }

    void send(final InternalMessage response)
    {
        _target.getSendLock();
        try
        {
            final ManagementResponse responseEntry = new ManagementResponse(this, response);
            if(_queue.isEmpty() && _target.allocateCredit(response))
            {
                _target.send(this, responseEntry, false);
            }
            else
            {
                _queue.add(responseEntry);
            }
        }
        finally
        {
            _target.releaseSendLock();
        }
    }

    private class TargetChangeListener implements StateChangeListener<ConsumerTarget, ConsumerTarget.State>
    {
        @Override
        public void stateChanged(final ConsumerTarget object,
                                 final ConsumerTarget.State oldState,
                                 final ConsumerTarget.State newState)
        {
            if(newState == ConsumerTarget.State.ACTIVE)
            {
                deliverMessages();
            }
        }
    }

    private void deliverMessages()
    {
        _target.getSendLock();
        try
        {
            while(!_queue.isEmpty())
            {

                final ManagementResponse managementResponse = _queue.get(0);
                if(!_target.isSuspended() && _target.allocateCredit(managementResponse.getMessage()))
                {
                    _queue.remove(0);
                    _target.send(this, managementResponse, false);
                }
                else
                {
                    break;
                }
            }
        }
        finally
        {
            _target.releaseSendLock();
        }
    }
}
