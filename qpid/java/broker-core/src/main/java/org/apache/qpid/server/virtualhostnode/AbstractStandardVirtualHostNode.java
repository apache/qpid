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

import java.io.IOException;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.security.auth.Subject;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.logging.messages.ConfigStoreMessages;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.RemoteReplicationNode;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.ConfiguredObjectRecordImpl;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.VirtualHostStoreUpgraderAndRecoverer;

public abstract class AbstractStandardVirtualHostNode<X extends AbstractStandardVirtualHostNode<X>> extends AbstractVirtualHostNode<X>
                implements VirtualHostNode<X>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractStandardVirtualHostNode.class);

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
    protected ListenableFuture<Void> activate()
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Activating virtualhost node " + this);
        }

        try
        {
            ConfiguredObjectRecord[] initialRecords = getInitialRecords();
            getConfigurationStore().openConfigurationStore(this, false, initialRecords);
            if(initialRecords != null && initialRecords.length > 0)
            {
                setAttribute(VIRTUALHOST_INITIAL_CONFIGURATION, getVirtualHostInitialConfiguration(), "{}");
            }
        }
        catch (IOException e)
        {
            throw new IllegalConfigurationException("Could not process initial configuration", e);
        }

        getConfigurationStore().upgradeStoreStructure();

        getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.CREATED());

        writeLocationEventLog();

        getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.RECOVERY_START());

        VirtualHostStoreUpgraderAndRecoverer upgrader = new VirtualHostStoreUpgraderAndRecoverer(this);
        upgrader.perform(getConfigurationStore());

        getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.RECOVERY_COMPLETE());

        VirtualHost<?,?,?>  host = getVirtualHost();

        if (host != null)
        {
            final VirtualHost<?,?,?> recoveredHost = host;
            final ListenableFuture<Void> openFuture = Subject.doAs(SecurityManager.getSubjectWithAddedSystemRights(),
                                                                   new PrivilegedAction<ListenableFuture<Void>>()
                                                                   {
                                                                       @Override
                                                                       public ListenableFuture<Void> run()
                                                                       {
                                                                           return recoveredHost.openAsync();

                                                                       }
                                                                   });
            return openFuture;
        }
        else
        {
            return Futures.immediateFuture(null);
        }
    }


    @Override
    protected ConfiguredObjectRecord enrichInitialVirtualHostRootRecord(final ConfiguredObjectRecord vhostRecord)
    {
        ConfiguredObjectRecord replacementRecord;
        if (vhostRecord.getAttributes().get(ConfiguredObject.NAME) == null)
        {
            Map<String, Object> updatedAttributes = new LinkedHashMap<>(vhostRecord.getAttributes());
            updatedAttributes.put(ConfiguredObject.NAME, getName());
            if (!updatedAttributes.containsKey(VirtualHost.MODEL_VERSION))
            {
                updatedAttributes.put(VirtualHost.MODEL_VERSION, getBroker().getModelVersion());
            }
            replacementRecord = new ConfiguredObjectRecordImpl(vhostRecord.getId(),
                                                               vhostRecord.getType(),
                                                               updatedAttributes,
                                                               vhostRecord.getParents());
        }
        else if (vhostRecord.getAttributes().get(VirtualHost.MODEL_VERSION) == null)
        {
            Map<String, Object> updatedAttributes = new LinkedHashMap<>(vhostRecord.getAttributes());

            updatedAttributes.put(VirtualHost.MODEL_VERSION, getBroker().getModelVersion());

            replacementRecord = new ConfiguredObjectRecordImpl(vhostRecord.getId(),
                                                               vhostRecord.getType(),
                                                               updatedAttributes,
                                                               vhostRecord.getParents());
        }
        else
        {
            replacementRecord = vhostRecord;
        }

        return replacementRecord;
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

    @Override
    public void validateOnCreate()
    {
        super.validateOnCreate();
        DurableConfigurationStore store = createConfigurationStore();
        if (store != null)
        {
            try
            {
                store.openConfigurationStore(this, false);
            }
            catch (Exception e)
            {
                throw new IllegalConfigurationException("Cannot open node configuration store:" + e.getMessage(), e);
            }
            finally
            {
                try
                {
                    store.closeConfigurationStore();
                }
                catch(Exception e)
                {
                    LOGGER.warn("Failed to close database", e);
                }
            }
        }
    }
}
