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
package org.apache.qpid.server.virtualhostnode;

import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;

import org.apache.qpid.server.logging.messages.ConfigStoreMessages;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.RemoteReplicationNode;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.VirtualHostStoreUpgraderAndRecoverer;

public abstract class AbstractStandardVirtualHostNode<X extends AbstractStandardVirtualHostNode<X>> extends AbstractVirtualHostNode<X>
                implements VirtualHostNode<X>
{
    private static final Logger LOGGER = Logger.getLogger(AbstractStandardVirtualHostNode.class);

    public AbstractStandardVirtualHostNode(Map<String, Object> attributes,
                                           Broker<?> parent)
    {
        super(parent, attributes);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    protected <C extends ConfiguredObject> C addChild(Class<C> childClass, Map<String, Object> attributes,
            ConfiguredObject... otherParents)
    {
        if(childClass == VirtualHost.class)
        {
            return (C) getObjectFactory().create(VirtualHost.class, attributes, this);
        }
        return super.addChild(childClass, attributes, otherParents);
    }

    @Override
    protected void activate()
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Activating virtualhost node " + this);
        }

        getConfigurationStore().openConfigurationStore(this, false);
        getConfigurationStore().upgradeStoreStructure();

        getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.CREATED());

        writeLocationEventLog();

        getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.RECOVERY_START());

        VirtualHostStoreUpgraderAndRecoverer upgrader = new VirtualHostStoreUpgraderAndRecoverer(this);
        upgrader.perform(getConfigurationStore());

        getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.RECOVERY_COMPLETE());

        VirtualHost<?,?,?>  host = getVirtualHost();

        if (host == null)
        {

            boolean hasBlueprint = getContextKeys().contains(VIRTUALHOST_BLUEPRINT_CONTEXT_VAR);
            boolean blueprintUtilised = getContext().containsKey(VIRTUALHOST_BLUEPRINT_UTILISED_CONTEXT_VAR)
                    && Boolean.parseBoolean(String.valueOf(getContext().get(VIRTUALHOST_BLUEPRINT_UTILISED_CONTEXT_VAR)));

            if (hasBlueprint && !blueprintUtilised)
            {
                Map<String, Object> virtualhostBlueprint = getContextValue(Map.class, VIRTUALHOST_BLUEPRINT_CONTEXT_VAR);

                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Using virtualhost blueprint " + virtualhostBlueprint);
                }

                Map<String, Object> virtualhostAttributes = new HashMap<>();
                virtualhostAttributes.put(VirtualHost.MODEL_VERSION, BrokerModel.MODEL_VERSION);
                virtualhostAttributes.put(VirtualHost.NAME, getName());
                virtualhostAttributes.putAll(virtualhostBlueprint);

                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Creating new virtualhost named " + virtualhostAttributes.get(VirtualHost.NAME));
                }

                host = createChild(VirtualHost.class, virtualhostAttributes);

                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Created new virtualhost: " + host);
                }

                // Update the context with the utilised flag
                Map<String, String> actualContext = (Map<String, String>) getActualAttributes().get(CONTEXT);
                Map<String, String> context = new HashMap<>(actualContext);
                context.put(VIRTUALHOST_BLUEPRINT_UTILISED_CONTEXT_VAR, Boolean.TRUE.toString());
                setAttribute(CONTEXT, getContext(), context);
            }
        }
        else
        {
            final VirtualHost<?,?,?> recoveredHost = host;
            Subject.doAs(SecurityManager.getSubjectWithAddedSystemRights(), new PrivilegedAction<Object>()
            {
                @Override
                public Object run()
                {
                    recoveredHost.open();
                    return null;
                }
            });
        }
    }

    protected abstract void writeLocationEventLog();

    @Override
    public String toString()
    {
        return this.getClass().getSimpleName() +  "[id=" + getId() + ", name=" + getName() + ", state=" + getState() + "]";
    }

    @Override
    public Collection<RemoteReplicationNode<?>> getRemoteReplicationNodes()
    {
        return Collections.emptyList();
    }
}
