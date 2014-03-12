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

import org.apache.qpid.server.logging.subjects.MessageStoreLogSubject;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.DurableConfigurationRecoverer;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.DurableConfigurationStoreCreator;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreCreator;
import org.apache.qpid.server.store.OperationalLoggingListener;

public class StandardVirtualHost extends AbstractVirtualHost
{
    private MessageStore _messageStore;

    private DurableConfigurationStore _durableConfigurationStore;

    StandardVirtualHost(VirtualHostRegistry virtualHostRegistry,
                        StatisticsGatherer brokerStatisticsGatherer,
                        org.apache.qpid.server.security.SecurityManager parentSecurityManager,
                        VirtualHost virtualHost)
    {
        super(virtualHostRegistry, brokerStatisticsGatherer, parentSecurityManager, virtualHost);
    }



    private MessageStore initialiseMessageStore(VirtualHost virtualHost)
    {
        final Object storeTypeAttr = virtualHost.getAttribute(VirtualHost.STORE_TYPE);
        String storeType = storeTypeAttr == null ? null : String.valueOf(storeTypeAttr);
        MessageStore messageStore = new MessageStoreCreator().createMessageStore(storeType);

        MessageStoreLogSubject
                storeLogSubject = new MessageStoreLogSubject(getName(), messageStore.getClass().getSimpleName());
        OperationalLoggingListener.listen(messageStore, storeLogSubject, getEventLogger());

        return messageStore;
    }

    private DurableConfigurationStore initialiseConfigurationStore(VirtualHost virtualHost)
    {
        DurableConfigurationStore configurationStore;
        final Object storeTypeAttr = virtualHost.getAttribute(VirtualHost.CONFIG_STORE_TYPE);
        String storeType = storeTypeAttr == null ? null : String.valueOf(storeTypeAttr);

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


    protected void initialiseStorage(VirtualHost virtualHost)
    {
        _messageStore = initialiseMessageStore(virtualHost);

        _durableConfigurationStore = initialiseConfigurationStore(virtualHost);

        DurableConfigurationRecoverer configRecoverer =
                new DurableConfigurationRecoverer(getName(), getDurableConfigurationRecoverers(),
                                                  new DefaultUpgraderProvider(this, getExchangeRegistry()), getEventLogger());
        _durableConfigurationStore.configureConfigStore(virtualHost, configRecoverer);

        VirtualHostConfigRecoveryHandler recoveryHandler = new VirtualHostConfigRecoveryHandler(this);
        _messageStore.configureMessageStore(virtualHost, recoveryHandler, recoveryHandler);

        initialiseModel();

        _messageStore.activate();

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

}
