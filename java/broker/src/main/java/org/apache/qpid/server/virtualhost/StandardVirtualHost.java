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

import org.apache.commons.configuration.Configuration;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.logging.subjects.MessageStoreLogSubject;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.Event;
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
                               VirtualHostConfiguration hostConfig) throws Exception
    {
        super(virtualHostRegistry, brokerStatisticsGatherer, parentSecurityManager, hostConfig);
    }



    private MessageStore initialiseMessageStore(VirtualHostConfiguration hostConfig) throws Exception
    {
        String storeType = hostConfig.getConfig().getString("store.type");
        MessageStore  messageStore = null;
        if (storeType == null)
        {
            final Class<?> clazz = Class.forName(hostConfig.getMessageStoreClass());
            final Object o = clazz.newInstance();

            if (!(o instanceof MessageStore))
            {
                throw new ClassCastException(clazz + " does not implement " + MessageStore.class);
            }

            messageStore = (MessageStore) o;
        }
        else
        {
            messageStore = new MessageStoreCreator().createMessageStore(storeType);
        }

        final
        MessageStoreLogSubject
                storeLogSubject = new MessageStoreLogSubject(this, messageStore.getClass().getSimpleName());
        OperationalLoggingListener.listen(messageStore, storeLogSubject);

        return messageStore;
    }

    private DurableConfigurationStore initialiseConfigurationStore(VirtualHostConfiguration hostConfig) throws Exception
    {
        DurableConfigurationStore configurationStore;
        if(getMessageStore() instanceof DurableConfigurationStore)
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


    protected void initialiseStorage(VirtualHostConfiguration hostConfig) throws Exception
    {
        _messageStore = initialiseMessageStore(hostConfig);

        _durableConfigurationStore = initialiseConfigurationStore(hostConfig);

        VirtualHostConfigRecoveryHandler recoveryHandler = new VirtualHostConfigRecoveryHandler(this);

        final Configuration storeConfiguration = hostConfig.getStoreConfiguration();

        _durableConfigurationStore.configureConfigStore(getName(), recoveryHandler, storeConfiguration);

        _messageStore.configureMessageStore(getName(), recoveryHandler, recoveryHandler, storeConfiguration);

        initialiseModel(hostConfig);

        _messageStore.activate();

        attainActivation();
    }


    protected void closeStorage()
    {
        //Close MessageStore
        if (_messageStore != null)
        {
            //Remove MessageStore Interface should not throw Exception
            try
            {
                getMessageStore().close();
            }
            catch (Exception e)
            {
                getLogger().error("Failed to close message store", e);
            }
        }
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
