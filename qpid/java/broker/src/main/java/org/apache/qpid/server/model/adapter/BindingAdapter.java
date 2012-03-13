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
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;

final class BindingAdapter extends AbstractAdapter implements Binding
{
    private final org.apache.qpid.server.binding.Binding _binding;
    private Statistics _statistics = NoStatistics.getInstance();
    private final ExchangeAdapter _exchange;
    private QueueAdapter _queue;

    public BindingAdapter(final org.apache.qpid.server.binding.Binding binding, 
                          ExchangeAdapter exchangeAdapter,
                          QueueAdapter queueAdapter)
    {
        _binding = binding;
        _exchange = exchangeAdapter;
        _queue = queueAdapter;
        addParent(Queue.class, queueAdapter);
        addParent(Exchange.class, exchangeAdapter);
    }


    public ExchangeAdapter getExchange()
    {
        return _exchange;
    }

    public QueueAdapter getQueue()
    {
        return _queue;
    }

    public String getName()
    {
        return _binding.getBindingKey();
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
        return _binding.getQueue().isDurable() && _binding.getExchange().isDurable();
    }

    public void setDurable(final boolean durable)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        //TODO
    }

    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
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

    public Statistics getStatistics()
    {
        return _statistics;
    }

    public Map<String, Object> getArguments()
    {
        return new HashMap<String, Object> (_binding.getArguments());
    }
}
