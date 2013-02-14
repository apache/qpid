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

import java.util.Map;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Consumer;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.subscription.Subscription;

import java.security.AccessControlException;
import java.util.Collection;
import java.util.Collections;

public class ConsumerAdapter extends AbstractAdapter implements Consumer
{
    private final Subscription _subscription;
    private final QueueAdapter _queue;
    private final ConsumerStatistics _statistics;

    public ConsumerAdapter(final QueueAdapter queueAdapter, final Subscription subscription)
    {
        super(UUIDGenerator.generateConsumerUUID(queueAdapter.getVirtualHost().getName(),
                                               queueAdapter.getName(),
                                               subscription.getSessionModel().getConnectionModel().getRemoteAddressString(),
                                               String.valueOf(subscription.getSessionModel().getChannelId()),
                                               subscription.getConsumerName()), queueAdapter.getTaskExecutor());
        _subscription = subscription;
        _queue = queueAdapter;
        _statistics = new ConsumerStatistics();
        //TODO
    }

    public String getName()
    {
        return _subscription.getConsumerName();
    }

    public String setName(final String currentName, final String desiredName)
            throws IllegalStateException, AccessControlException
    {
        return null;  //TODO
    }

    public State getActualState()
    {
        return null;  //TODO
    }

    public boolean isDurable()
    {
        return false;  //TODO
    }

    public void setDurable(final boolean durable)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        //TODO
    }

    public LifetimePolicy getLifetimePolicy()
    {
        return null;  //TODO
    }

    public LifetimePolicy setLifetimePolicy(final LifetimePolicy expected, final LifetimePolicy desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        return null;  //TODO
    }

    public long getTimeToLive()
    {
        return 0;  //TODO
    }

    public long setTimeToLive(final long expected, final long desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        return 0;  //TODO
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return Consumer.AVAILABLE_ATTRIBUTES;
    }

    @Override
    public Object getAttribute(final String name)
    {
        if(ID.equals(name))
        {
            return getId();
        }
        else if(NAME.equals(name))
        {
            return getName();
        }
        else if(STATE.equals(name))
        {

        }
        else if(DURABLE.equals(name))
        {
            return false;
        }
        else if(LIFETIME_POLICY.equals(name))
        {
            return LifetimePolicy.AUTO_DELETE;
        }
        else if(TIME_TO_LIVE.equals(name))
        {

        }
        else if(CREATED.equals(name))
        {

        }
        else if(UPDATED.equals(name))
        {

        }
        else if(DISTRIBUTION_MODE.equals(name))
        {
            return _subscription.acquires() ? "MOVE" : "COPY";
        }
        else if(SETTLEMENT_MODE.equals(name))
        {

        }
        else if(EXCLUSIVE.equals(name))
        {

        }
        else if(NO_LOCAL.equals(name))
        {

        }
        else if(SELECTOR.equals(name))
        {

        }
        return super.getAttribute(name);    //TODO
    }

    public Statistics getStatistics()
    {
        return _statistics;
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        return Collections.emptySet();
    }

    @Override
    public <C extends ConfiguredObject> C createChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        throw new IllegalArgumentException();
    }

    private class ConsumerStatistics implements Statistics
    {

        public Collection<String> getStatisticNames()
        {
            return AVAILABLE_STATISTICS;
        }

        public Object getStatistic(String name)
        {
            if(name.equals(BYTES_OUT))
            {
                return _subscription.getBytesOut();
            }
            else if(name.equals(MESSAGES_OUT))
            {
                return _subscription.getMessagesOut();
            }
            else if(name.equals(STATE_CHANGED))
            {

            }
            else if(name.equals(UNACKNOWLEDGED_BYTES))
            {
                return _subscription.getUnacknowledgedBytes();
            }
            else if(name.equals(UNACKNOWLEDGED_MESSAGES))
            {
                return _subscription.getUnacknowledgedMessages();
            }
            return null;  // TODO - Implement
        }
    }

    @Override
    protected boolean setState(State currentState, State desiredState)
    {
        // TODO : Add state management
        return false;
    }
}
