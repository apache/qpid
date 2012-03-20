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
import java.util.HashSet;

import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Publisher;
import org.apache.qpid.server.model.Session;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.Consumer;
import org.apache.qpid.server.protocol.AMQSessionModel;

final class SessionAdapter extends AbstractAdapter implements Session
{
    // Attributes


    private AMQSessionModel _session;
    private SessionStatistics _statistics;

    public SessionAdapter(final AMQSessionModel session)
    {
        _session = session;
        _statistics = new SessionStatistics();
    }

    public Collection<Consumer> getSubscriptions()
    {
        return null;  //TODO
    }

    public Collection<Publisher> getPublishers()
    {
        return null;  //TODO
    }

    public String getName()
    {
        return String.valueOf(_session.getChannelId());
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
        Collection<String> names = new HashSet<String>(super.getAttributeNames());
        names.addAll(AVAILABLE_ATTRIBUTES);

        return Collections.unmodifiableCollection(names);
    }

    @Override
    public Object getAttribute(String name)
    {
        if(name.equals(CHANNEL_ID))
        {
            return _session.getChannelId();
        }
        return super.getAttribute(name);    //TODO - Implement
    }

    @Override
    public Object setAttribute(String name, Object expected, Object desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        return super.setAttribute(name, expected, desired);    //TODO - Implement
    }

    public Statistics getStatistics()
    {
        return _statistics;
    }

    private class SessionStatistics implements Statistics
    {

        public SessionStatistics()
        {
        }

        public Collection<String> getStatisticNames()
        {
            return AVAILABLE_STATISTICS;
        }

        public Object getStatistic(String name)
        {
            if(name.equals(BYTES_IN))
            {
            }
            else if(name.equals(BYTES_OUT))
            {
            }
            else if(name.equals(CONSUMER_COUNT))
            {
                return getSubscriptions().size();
            }
            else if(name.equals(LOCAL_TRANSACTION_BEGINS))
            {
                return _session.getTxnCount();
            }
            else if(name.equals(LOCAL_TRANSACTION_OPEN))
            {
                long open = _session.getTxnCount() - (_session.getTxnCommits() + _session.getTxnRejects());
                return (Boolean) (open > 0l);
            }
            else if(name.equals(LOCAL_TRANSACTION_ROLLBACKS))
            {
                return _session.getTxnCommits();
            }
            else if(name.equals(STATE_CHANGED))
            {
            }
            else if(name.equals(UNACKNOWLEDGED_BYTES))
            {
            }
            else if(name.equals(UNACKNOWLEDGED_MESSAGES))
            {
                return _session.getUnacknowledgedMessageCount();
            }
            else if(name.equals(XA_TRANSACTION_BRANCH_ENDS))
            {
            }
            else if(name.equals(XA_TRANSACTION_BRANCH_STARTS))
            {
            }
            else if(name.equals(XA_TRANSACTION_BRANCH_SUSPENDS))
            {

            }

            return null;  // TODO - Implement
        }
    }
}
