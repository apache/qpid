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

import java.util.List;
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

@ManagedObject( category = false, type = BDBHAVirtualHostImpl.VIRTUAL_HOST_TYPE )
public class BDBHAVirtualHostImpl extends AbstractVirtualHost<BDBHAVirtualHostImpl> implements BDBHAVirtualHost<BDBHAVirtualHostImpl>
{
    public static final String VIRTUAL_HOST_TYPE = "BDB_HA";

    private final BDBConfigurationStore _configurationStore;

    @ManagedAttributeField
    private String _localTransactionSynchronizationPolicy;

    @ManagedAttributeField
    private String _remoteTransactionSynchronizationPolicy;

    @ManagedAttributeField
    private Long _storeUnderfullSize;

    @ManagedAttributeField
    private Long _storeOverfullSize;

    @ManagedAttributeField(afterSet = "applyPermittedNodes")
    private List<String> _permittedNodes;

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
    public String getDurability()
    {
        ReplicatedEnvironmentFacade facade = getReplicatedEnvironmentFacade();
        if (facade != null)
        {
            return String.valueOf(facade.getMessageStoreDurability());
        }
        return null;
    }

    @Override
    public boolean isCoalescingSync()
    {
        return SyncPolicy.SYNC.name().equals(_localTransactionSynchronizationPolicy);
    }

    @Override
    public void onOpen()
    {
        ReplicatedEnvironmentFacade facade = getReplicatedEnvironmentFacade();
        if (facade != null)
        {
            facade.setMessageStoreDurability(
                    SyncPolicy.valueOf(getLocalTransactionSynchronizationPolicy()),
                    SyncPolicy.valueOf(getRemoteTransactionSynchronizationPolicy()),
                    ReplicatedEnvironmentFacade.REPLICA_REPLICA_ACKNOWLEDGMENT_POLICY);
        }
        super.onOpen();
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

        if(changedAttributes.contains(PERMITTED_NODES))
        {

            List<String> permittedNodes = ((BDBHAVirtualHost<?>)proxyForValidation).getPermittedNodes();
            if (permittedNodes != null)
            {
                for (String permittedNode: permittedNodes)
                {
                    String[] tokens = permittedNode.split(":");
                    if (tokens.length != 2)
                    {
                        throw new IllegalArgumentException(String.format("Invalid permitted node specified '%s'. ", permittedNode));
                    }
                    try
                    {
                        Integer.parseInt(tokens[1]);
                    }
                    catch(Exception e)
                    {
                        throw new IllegalArgumentException(String.format("Invalid port is specified in permitted node '%s'. ", permittedNode));
                    }
                }
            }
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

    private ReplicatedEnvironmentFacade getReplicatedEnvironmentFacade()
    {
        return (ReplicatedEnvironmentFacade) _configurationStore.getEnvironmentFacade();
    }

    @Override
    public Long getStoreUnderfullSize()
    {
        return _storeUnderfullSize;
    }

    @Override
    public Long getStoreOverfullSize()
    {
        return _storeOverfullSize;
    }

    @Override
    public List<String> getPermittedNodes()
    {
        return _permittedNodes;
    }

    protected void applyPermittedNodes()
    {
        ReplicatedEnvironmentFacade facade = getReplicatedEnvironmentFacade();
        if (facade != null)
        {
            facade.setPermittedNodes(getPermittedNodes());
        }
    }
}
