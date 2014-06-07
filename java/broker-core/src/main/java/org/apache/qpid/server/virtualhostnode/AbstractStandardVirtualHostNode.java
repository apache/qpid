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

import java.io.File;
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
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreProvider;
import org.apache.qpid.server.store.VirtualHostStoreUpgraderAndRecoverer;
import org.apache.qpid.server.virtualhost.ProvidedStoreVirtualHost;

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

        Map<String, Object> attributes = buildAttributesForStore();

        getConfigurationStore().openConfigurationStore(this, attributes);
        getConfigurationStore().upgradeStoreStructure();

        getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.CREATED());

        if (this instanceof FileBasedVirtualHostNode)
        {
            @SuppressWarnings("rawtypes")
            FileBasedVirtualHostNode fileBasedVirtualHostNode = (FileBasedVirtualHostNode) this;
            getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.STORE_LOCATION(fileBasedVirtualHostNode.getStorePath()));
        }

        getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.RECOVERY_START());

        VirtualHostStoreUpgraderAndRecoverer upgrader = new VirtualHostStoreUpgraderAndRecoverer(this);
        upgrader.perform(getConfigurationStore());

        getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.RECOVERY_COMPLETE());

        VirtualHost<?,?,?>  host = getVirtualHost();

        if (host == null)
        {
            // TODO normal case - we should not create VH,
            // TODO out if box case - if blueprint vh context variable is set, use it to create a VH
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Creating new virtualhost with name : " + getName());
            }
            Map<String, Object> hostAttributes = new HashMap<String, Object>();
            hostAttributes.put(VirtualHost.MODEL_VERSION, BrokerModel.MODEL_VERSION);
            hostAttributes.put(VirtualHost.NAME, getName());
            if (getConfigurationStore() instanceof MessageStoreProvider)
            {
                hostAttributes.put(VirtualHost.TYPE, ProvidedStoreVirtualHost.VIRTUAL_HOST_TYPE);
            }
            else
            {
                hostAttributes.put(VirtualHost.TYPE, "DERBY");
                hostAttributes.put(VirtualHost.MESSAGE_STORE_SETTINGS, getDefaultMessageStoreSettings());
            }
            host = createChild(VirtualHost.class, hostAttributes);
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

    // protected for unit testing purposes
    protected Map<String, Object> getDefaultMessageStoreSettings()
    {
        // TODO perhaps look for the MS with the default annotation and associated default.
        Map<String, Object> settings = new HashMap<String, Object>();
        settings.put(MessageStore.STORE_TYPE, "DERBY");
        settings.put(MessageStore.STORE_PATH, "${qpid.work_dir}" + File.separator + "derbystore" + File.separator + getName());
        return settings;
    }


}
