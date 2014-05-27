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

import org.apache.qpid.server.logging.subjects.MessageStoreLogSubject;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.berkeleydb.BDBMessageStore;
import org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacade;
import org.apache.qpid.server.virtualhost.AbstractVirtualHost;

import com.sleepycat.je.Durability.SyncPolicy;

@ManagedObject( category = false, type = "BDB_HA" )
public class BDBHAVirtualHostImpl extends AbstractVirtualHost<BDBHAVirtualHostImpl> implements BDBHAVirtualHost<BDBHAVirtualHostImpl>
{
    public static final String TYPE = "BDB_HA";

    private final BDBMessageStore _messageStore;
    private MessageStoreLogSubject _messageStoreLogSubject;

    @ManagedAttributeField(afterSet="setLocalTransactionSyncronizationPolicyOnEnvironment")
    private String _localTransactionSyncronizationPolicy;

    @ManagedAttributeField(afterSet="setRemoteTransactionSyncronizationPolicyOnEnvironment")
    private String _remoteTransactionSyncronizationPolicy;

    @ManagedObjectFactoryConstructor
    public BDBHAVirtualHostImpl(final Map<String, Object> attributes, VirtualHostNode<?> virtualHostNode)
    {
        super(attributes, virtualHostNode);

        _messageStore = (BDBMessageStore) virtualHostNode.getConfigurationStore();
        _messageStoreLogSubject = new MessageStoreLogSubject(getName(), _messageStore.getClass().getSimpleName());
    }

    @Override
    protected void initialiseStorage()
    {
    }

    @Override
    public DurableConfigurationStore getDurableConfigurationStore()
    {
        return _messageStore;
    }

    @Override
    public MessageStore getMessageStore()
    {
        return _messageStore;
    }

    @Override
    protected MessageStoreLogSubject getMessageStoreLogSubject()
    {
        return _messageStoreLogSubject;
    }

    @Override
    public String getLocalTransactionSyncronizationPolicy()
    {
        return _localTransactionSyncronizationPolicy;
    }

    @Override
    public String getRemoteTransactionSyncronizationPolicy()
    {
        return _remoteTransactionSyncronizationPolicy;
    }

    @Override
    public void onOpen()
    {
        super.onOpen();
        setRemoteTransactionSyncronizationPolicyOnEnvironment();
        setLocalTransactionSyncronizationPolicyOnEnvironment();
    }

    @Override
    protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
    {
        super.validateChange(proxyForValidation, changedAttributes);

        if(changedAttributes.contains(LOCAL_TRANSACTION_SYNCRONIZATION_POLICY))
        {
            String policy = ((BDBHAVirtualHost<?>)proxyForValidation).getLocalTransactionSyncronizationPolicy();
            validateTransactionSynchronizationPolicy(policy);
        }

        if(changedAttributes.contains(REMOTE_TRANSACTION_SYNCRONIZATION_POLICY))
        {
            String policy = ((BDBHAVirtualHost<?>)proxyForValidation).getRemoteTransactionSyncronizationPolicy();
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
            throw new IllegalArgumentException("Invalid transaction syncronization policy '" + policy + "'. " + e.getMessage());
        }
    }

    protected void setLocalTransactionSyncronizationPolicyOnEnvironment()
    {
        ReplicatedEnvironmentFacade facade = getReplicatedEnvironmentFacade();
        if (facade != null)
        {
            facade.setLocalTransactionSyncronizationPolicy(SyncPolicy.valueOf(getLocalTransactionSyncronizationPolicy()));
        }
    }

    protected void setRemoteTransactionSyncronizationPolicyOnEnvironment()
    {
        ReplicatedEnvironmentFacade facade = getReplicatedEnvironmentFacade();
        if (facade != null)
        {
            facade.setRemoteTransactionSyncronizationPolicy(SyncPolicy.valueOf(getRemoteTransactionSyncronizationPolicy()));
        }
    }

    private ReplicatedEnvironmentFacade getReplicatedEnvironmentFacade()
    {
        return (ReplicatedEnvironmentFacade)_messageStore.getEnvironmentFacade();
    }
}
