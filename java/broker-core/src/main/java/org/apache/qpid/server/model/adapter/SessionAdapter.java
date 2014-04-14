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
package org.apache.qpid.server.model.adapter;

import java.security.AccessControlException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.*;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.ConsumerListener;

final class SessionAdapter extends AbstractConfiguredObject<SessionAdapter> implements Session<SessionAdapter>
{
    // Attributes


    private AMQSessionModel _session;

    @ManagedAttributeField
    private int _channelId;

    public SessionAdapter(final ConnectionAdapter connectionAdapter,
                          final AMQSessionModel session,
                          TaskExecutor taskExecutor)
    {
        super(parentsMap(connectionAdapter), createAttributes(session), taskExecutor);
        _session = session;
        _session.addConsumerListener(new ConsumerListener()
        {
            @Override
            public void consumerAdded(final Consumer<?> consumer)
            {
                childAdded(consumer);
            }

            @Override
            public void consumerRemoved(final Consumer<?> consumer)
            {
                childRemoved(consumer);
            }
        });
        open();
    }

    private static Map<String, Object> createAttributes(final AMQSessionModel session)
    {
        Map<String,Object> attributes = new HashMap<String, Object>();
        attributes.put(ID, UUID.randomUUID());
        attributes.put(NAME, String.valueOf(session.getChannelId()));
        attributes.put(CHANNEL_ID, session.getChannelId());
        attributes.put(DURABLE, false);
        return attributes;
    }

    @Override
    public int getChannelId()
    {
        return _channelId;
    }

    @Override
    public boolean isProducerFlowBlocked()
    {
        return _session.getBlocking();
    }

    public Collection<org.apache.qpid.server.model.Consumer> getConsumers()
    {
        return (Collection<Consumer>) _session.getConsumers();
    }

    public Collection<Publisher> getPublishers()
    {
        return Collections.emptySet();  //TODO
    }

    public State getState()
    {
        return null;  //TODO
    }

    public LifetimePolicy getLifetimePolicy()
    {
        return null;  //TODO
    }


    @Override
    public Collection<String> getAttributeNames()
    {
        return getAttributeNames(Session.class);
    }

    @Override
    public Object getAttribute(String name)
    {
        if(name.equals(PRODUCER_FLOW_BLOCKED))
        {
            return _session.getBlocking();
        }
        return super.getAttribute(name);    //TODO - Implement
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        if(clazz == org.apache.qpid.server.model.Consumer.class)
        {
            return (Collection<C>) getConsumers();
        }
        else if(clazz == Publisher.class)
        {
            return (Collection<C>) getPublishers();
        }
        else
        {
            return Collections.emptySet();
        }
    }

    @Override
    public long getConsumerCount()
    {
        return _session.getConsumerCount();
    }

    @Override
    public long getLocalTransactionBegins()
    {
        return _session.getTxnStart();
    }

    @Override
    public int getLocalTransactionOpen()
    {
        long open = _session.getTxnStart() - (_session.getTxnCommits() + _session.getTxnRejects());
        return (open > 0l) ? 1 : 0;
    }

    @Override
    public long getLocalTransactionRollbacks()
    {
        return _session.getTxnRejects();
    }

    @Override
    public long getUnacknowledgedMessages()
    {
        return _session.getUnacknowledgedMessageCount();
    }


    @Override
    protected boolean setState(State currentState, State desiredState)
    {
        // TODO : add state management
        return false;
    }

    @Override
    public Object setAttribute(final String name, final Object expected, final Object desired) throws IllegalStateException,
            AccessControlException, IllegalArgumentException
    {
        throw new UnsupportedOperationException("Changing attributes on session is not supported.");
    }

    @Override
    public void setAttributes(final Map<String, Object> attributes) throws IllegalStateException, AccessControlException,
            IllegalArgumentException
    {
        throw new UnsupportedOperationException("Changing attributes on session is not supported.");
    }
}
