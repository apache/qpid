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
package org.apache.qpid.server.virtualhost.berkeleydb;

import java.util.Map;
import java.util.Set;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.berkeleydb.BDBConfigurationStore;
import org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacade;
import org.apache.qpid.server.virtualhost.AbstractVirtualHost;

import com.sleepycat.je.Durability.SyncPolicy;

@ManagedObject( category = false, type = "BDB_HA" )
public class BDBHAVirtualHostImpl extends AbstractVirtualHost<BDBHAVirtualHostImpl> implements BDBHAVirtualHost<BDBHAVirtualHostImpl>
{
    public static final String TYPE = "BDB_HA";

    private final BDBConfigurationStore _configurationStore;

    @ManagedAttributeField(afterSet="setLocalTransactionSynchronizationPolicyOnEnvironment")
    private String _localTransactionSynchronizationPolicy;

    @ManagedAttributeField(afterSet="setRemoteTransactionSynchronizationPolicyOnEnvironment")
    private String _remoteTransactionSynchronizationPolicy;

    @ManagedObjectFactoryConstructor
    public BDBHAVirtualHostImpl(final Map<String, Object> attributes, VirtualHostNode<?> virtualHostNode)
    {
        super(attributes, virtualHostNode);

        _configurationStore = (BDBConfigurationStore) virtualHostNode.getConfigurationStore();
    }

    @Override
    protected MessageStore createMessageStore()
    {
        return _configurationStore.getMessageStore();
    }

    @Override
    public String getLocalTransactionSynchronizationPolicy()
    {
        return _localTransactionSynchronizationPolicy;
    }

    @Override
    public String getRemoteTransactionSynchronizationPolicy()
    {
        return _remoteTransactionSynchronizationPolicy;
    }


    @Override
    public String getReplicaAcknowledgmentPolicy()
    {
        ReplicatedEnvironmentFacade facade = getReplicatedEnvironmentFacade();
        if (facade != null)
        {
            return facade.getReplicaAcknowledgmentPolicy().name();
        }
        return null;
    }

    @Override
    public boolean isCoalescingSync()
    {
        ReplicatedEnvironmentFacade facade = getReplicatedEnvironmentFacade();
        if (facade != null)
        {
            return facade.isCoalescingSync();
        }
        return false;
    }

    @Override
    public void onOpen()
    {
        super.onOpen();
        setRemoteTransactionSynchronizationPolicyOnEnvironment();
        setLocalTransactionSynchronizationPolicyOnEnvironment();
    }

    @Override
    protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
    {
        super.validateChange(proxyForValidation, changedAttributes);

        if(changedAttributes.contains(LOCAL_TRANSACTION_SYNCHRONIZATION_POLICY))
        {
            String policy = ((BDBHAVirtualHost<?>)proxyForValidation).getLocalTransactionSynchronizationPolicy();
            validateTransactionSynchronizationPolicy(policy);
        }

        if(changedAttributes.contains(REMOTE_TRANSACTION_SYNCHRONIZATION_POLICY))
        {
            String policy = ((BDBHAVirtualHost<?>)proxyForValidation).getRemoteTransactionSynchronizationPolicy();
            validateTransactionSynchronizationPolicy(policy);
        }
    }

    private void validateTransactionSynchronizationPolicy(String policy)
    {
        try
        {
            SyncPolicy.valueOf(policy);
        }
        catch(Exception e)
        {
            throw new IllegalArgumentException("Invalid transaction synchronization policy '" + policy + "'. " + e.getMessage());
        }
    }

    protected void setLocalTransactionSynchronizationPolicyOnEnvironment()
    {
        ReplicatedEnvironmentFacade facade = getReplicatedEnvironmentFacade();
        if (facade != null)
        {
            facade.setMessageStoreLocalTransactionSynchronizationPolicy(SyncPolicy.valueOf(getLocalTransactionSynchronizationPolicy()));
        }
    }

    protected void setRemoteTransactionSynchronizationPolicyOnEnvironment()
    {
        ReplicatedEnvironmentFacade facade = getReplicatedEnvironmentFacade();
        if (facade != null)
        {
            facade.setMessageStoreRemoteTransactionSyncrhonizationPolicy(SyncPolicy.valueOf(getRemoteTransactionSynchronizationPolicy()));
        }
    }

    private ReplicatedEnvironmentFacade getReplicatedEnvironmentFacade()
    {
        return (ReplicatedEnvironmentFacade) _configurationStore.getEnvironmentFacade();
    }

}
