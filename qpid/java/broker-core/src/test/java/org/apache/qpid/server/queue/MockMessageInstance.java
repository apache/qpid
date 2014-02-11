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
package org.apache.qpid.server.queue;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.filter.Filterable;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.consumer.Consumer;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.StateChangeListener;

public class MockMessageInstance implements MessageInstance<MockMessageInstance,Consumer>
{

    private ServerMessage _message;

    public boolean acquire()
    {
        return false;
    }

    @Override
    public int getMaximumDeliveryCount()
    {
        return 0;
    }

    @Override
    public int routeToAlternate(final Action<? super MessageInstance<?, ? extends Consumer>> action,
                                final ServerTransaction txn)
    {
        return 0;
    }

    public boolean acquiredByConsumer()
    {
        return false;
    }

    @Override
    public boolean isAcquiredBy(final Consumer consumer)
    {
        return false;
    }

    public void delete()
    {

    }

    public boolean expired() throws AMQException
    {
        return false;
    }

    @Override
    public boolean acquire(final Consumer sub)
    {
        return false;
    }

    public boolean isAvailable()
    {
        return false;
    }

    public Consumer getDeliveredConsumer()
    {
        return null;
    }

    public boolean getDeliveredToConsumer()
    {
        return false;
    }

    public ServerMessage getMessage()
    {
        return _message;
    }

    public long getSize()
    {
        return 0;
    }

    public boolean isAcquired()
    {
        return false;
    }

    public void reject()
    {
    }

    @Override
    public boolean isRejectedBy(final Consumer consumer)
    {
        return false;
    }


    public void release()
    {
    }

    @Override
    public boolean resend() throws AMQException
    {
        return false;
    }


    public void setRedelivered()
    {
    }

    public AMQMessageHeader getMessageHeader()
    {
        return null;
    }

    public boolean isPersistent()
    {
        return false;
    }

    public boolean isRedelivered()
    {
        return false;
    }

    public void setMessage(ServerMessage msg)
    {
        _message = msg;
    }

    public boolean isDeleted()
    {
        return false;
    }
    @Override
    public int getDeliveryCount()
    {
        return 0;
    }

    @Override
    public void incrementDeliveryCount()
    {
    }

    @Override
    public void decrementDeliveryCount()
    {
    }

    @Override
    public void addStateChangeListener(final StateChangeListener<? super MockMessageInstance, State> listener)
    {

    }

    @Override
    public boolean removeStateChangeListener(final StateChangeListener<? super MockMessageInstance, State> listener)
    {
        return false;
    }

    @Override
    public Filterable asFilterable()
    {
        return Filterable.Factory.newInstance(_message, getInstanceProperties());
    }

    @Override
    public InstanceProperties getInstanceProperties()
    {
        return InstanceProperties.EMPTY;
    }

    @Override
    public TransactionLogResource getOwningResource()
    {
        return null;
    }
}
