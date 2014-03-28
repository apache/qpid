package org.apache.qpid.server.virtualhost;/*
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

import java.util.Map;

import org.apache.qpid.server.logging.messages.ConfigStoreMessages;
import org.apache.qpid.server.logging.messages.MessageStoreMessages;
import org.apache.qpid.server.logging.subjects.MessageStoreLogSubject;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.plugin.MessageStoreFactory;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.DurableConfigurationRecoverer;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.DurableConfigurationStoreCreator;
import org.apache.qpid.server.store.MessageStore;

public class StandardVirtualHost extends AbstractVirtualHost
{
    private MessageStore _messageStore;

    private DurableConfigurationStore _durableConfigurationStore;

    private MessageStoreLogSubject _messageStoreLogSubject;

    private MessageStoreLogSubject _configurationStoreLogSubject;

    StandardVirtualHost(VirtualHostRegistry virtualHostRegistry,
                        StatisticsGatherer brokerStatisticsGatherer,
                        org.apache.qpid.server.security.SecurityManager parentSecurityManager,
                        VirtualHost virtualHost)
    {
        super(virtualHostRegistry, brokerStatisticsGatherer, parentSecurityManager, virtualHost);
    }

    private DurableConfigurationStore initialiseConfigurationStore(String storeType)
    {
        DurableConfigurationStore configurationStore;

        if(storeType != null)
        {
            configurationStore = new DurableConfigurationStoreCreator().createMessageStore(storeType);
        }
        else if(getMessageStore() instanceof DurableConfigurationStore)
        {
            configurationStore = (DurableConfigurationStore) getMessageStore();
        }
        else
        {
            throw new ClassCastException(getMessageStore().getClass().getSimpleName() +
                                         " is not an instance of DurableConfigurationStore");
        }
        return configurationStore;
    }

    @Override
    protected void initialiseStorage(VirtualHost virtualHost)
    {
        Map<String, Object> messageStoreSettings = virtualHost.getMessageStoreSettings();
        String storeType = (String) messageStoreSettings.get(MessageStore.STORE_TYPE);
        _messageStore = MessageStoreFactory.FACTORY_LOADER.get(storeType).createMessageStore();
        _messageStoreLogSubject = new MessageStoreLogSubject(getName(), _messageStore.getClass().getSimpleName());
        getEventLogger().message(_messageStoreLogSubject, MessageStoreMessages.CREATED());

        Map<String, Object> configurationStoreSettings = virtualHost.getConfigurationStoreSettings();
        String configurationStoreType = configurationStoreSettings == null ? null : (String) configurationStoreSettings.get(DurableConfigurationStore.STORE_TYPE);
        _durableConfigurationStore = initialiseConfigurationStore(configurationStoreType);
        boolean combinedStores = _durableConfigurationStore == _messageStore;
        if (!combinedStores)
        {
            _configurationStoreLogSubject = new MessageStoreLogSubject(getName(), _durableConfigurationStore.getClass().getSimpleName());
            getEventLogger().message(_configurationStoreLogSubject, ConfigStoreMessages.CREATED());
        }

        DurableConfigurationRecoverer configRecoverer =
                new DurableConfigurationRecoverer(getName(), getDurableConfigurationRecoverers(),
                                                  new DefaultUpgraderProvider(this, getExchangeRegistry()), getEventLogger());
        _durableConfigurationStore.openConfigurationStore(virtualHost.getName(), combinedStores ? messageStoreSettings: configurationStoreSettings);

        _messageStore.openMessageStore(virtualHost.getName(), virtualHost.getMessageStoreSettings());

        getEventLogger().message(_messageStoreLogSubject, MessageStoreMessages.STORE_LOCATION(_messageStore.getStoreLocation()));

        if (_configurationStoreLogSubject != null)
        {
            getEventLogger().message(_configurationStoreLogSubject, ConfigStoreMessages.STORE_LOCATION(configurationStoreSettings.toString()));
        }

        _durableConfigurationStore.recoverConfigurationStore(getModel(), configRecoverer);

        // If store does not have entries for standard exchanges (amq.*), the following will create them.
        initialiseModel();

        VirtualHostConfigRecoveryHandler recoveryHandler = new VirtualHostConfigRecoveryHandler(this, getMessageStoreLogSubject());
        _messageStore.recoverMessageStore(getModel(), recoveryHandler, recoveryHandler);

        attainActivation();
    }

    @Override
    public MessageStore getMessageStore()
    {
        return _messageStore;
    }

    @Override
    public DurableConfigurationStore getDurableConfigurationStore()
    {
        return _durableConfigurationStore;
    }

    @Override
    protected MessageStoreLogSubject getMessageStoreLogSubject()
    {
        return _messageStoreLogSubject;
    }

    @Override
    protected MessageStoreLogSubject getConfigurationStoreLogSubject()
    {
        return _configurationStoreLogSubject;
    }
}