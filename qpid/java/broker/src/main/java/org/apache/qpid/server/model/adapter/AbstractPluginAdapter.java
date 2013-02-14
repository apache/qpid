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
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;

public abstract class AbstractPluginAdapter extends AbstractAdapter implements Plugin
{

    protected AbstractPluginAdapter(UUID id, Map<String, Object> defaults, Map<String, Object> attributes, TaskExecutor taskExecutor)
    {
        super(id, defaults, attributes, taskExecutor);
    }

    @Override
    public String setName(String currentName, String desiredName) throws IllegalStateException, AccessControlException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public State getActualState()
    {
        return null;
    }

    @Override
    public boolean isDurable()
    {
        return true;
    }

    @Override
    public void setDurable(boolean durable) throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
    }

    @Override
    public LifetimePolicy setLifetimePolicy(LifetimePolicy expected, LifetimePolicy desired) throws IllegalStateException,
            AccessControlException, IllegalArgumentException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getTimeToLive()
    {
        return 0;
    }

    @Override
    public long setTimeToLive(long expected, long desired) throws IllegalStateException, AccessControlException,
            IllegalArgumentException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Statistics getStatistics()
    {
        return null;
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        return Collections.emptyList();
    }

    @Override
    public <C extends ConfiguredObject> C createChild(Class<C> childClass, Map<String, Object> attributes,
            ConfiguredObject... otherParents)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return AVAILABLE_ATTRIBUTES;
    }

    @Override
    public Object getAttribute(String name)
    {
        if (ID.equals(name))
        {
            return getId();
        }
        else if (STATE.equals(name))
        {
            return getActualState();
        }
        else if (DURABLE.equals(name))
        {
            return isDurable();
        }
        else if (LIFETIME_POLICY.equals(name))
        {
            return getLifetimePolicy();
        }
        else if (TIME_TO_LIVE.equals(name))
        {
            return getTimeToLive();
        }
        else if (CREATED.equals(name))
        {

        }
        else if (UPDATED.equals(name))
        {

        }
        return super.getAttribute(name);
    }
}
