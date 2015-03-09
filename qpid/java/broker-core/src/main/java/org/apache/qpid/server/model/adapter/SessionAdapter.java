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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Consumer;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Publisher;
import org.apache.qpid.server.model.Session;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.ConsumerListener;
import org.apache.qpid.server.util.Action;

final class SessionAdapter extends AbstractConfiguredObject<SessionAdapter> implements Session<SessionAdapter>
{
    // Attributes
    private final AMQSessionModel _session;

    public SessionAdapter(final ConnectionAdapter connectionAdapter,
                          final AMQSessionModel session)
    {
        super(parentsMap(connectionAdapter), createAttributes(session));
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
        session.setModelObject(this);
        session.addDeleteTask(new Action()
        {
            @Override
            public void performAction(final Object object)
            {
                session.removeDeleteTask(this);
                deleted();
            }
        });
        setState(State.ACTIVE);
    }

    private static Map<String, Object> createAttributes(final AMQSessionModel session)
    {
        Map<String,Object> attributes = new HashMap<String, Object>();
        attributes.put(ID, UUID.randomUUID());
        attributes.put(NAME, String.valueOf(session.getChannelId()));
        attributes.put(DURABLE, false);
        attributes.put(LIFETIME_POLICY, LifetimePolicy.DELETE_ON_SESSION_END);
        return attributes;
    }

    @Override
    public int getChannelId()
    {
        return _session.getChannelId();
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
    public long getTransactionStartTime()
    {
        return _session.getTransactionStartTime();
    }

    @Override
    public long getTransactionUpdateTime()
    {
        return _session.getTransactionUpdateTime();
    }

    @StateTransition(currentState = State.ACTIVE, desiredState = State.DELETED)
    private ListenableFuture<Void> doDelete()
    {
        deleted();
        setState(State.DELETED);
        return Futures.immediateFuture(null);
    }

}
