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
package org.apache.qpid.server.virtualhost;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.logging.messages.ConfigStoreMessages;
import org.apache.qpid.server.logging.messages.MessageStoreMessages;
import org.apache.qpid.server.logging.subjects.MessageStoreLogSubject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.plugin.MessageStoreFactory;
import org.apache.qpid.server.store.ConfiguredObjectRecordRecoveverAndUpgrader;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.DurableConfigurationStoreCreator;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;

@ManagedObject( category = false, type = "STANDARD")
public class StandardVirtualHost extends AbstractVirtualHost<StandardVirtualHost>
{
    public static final String TYPE = "STANDARD";
    private MessageStore _messageStore;

    private DurableConfigurationStore _durableConfigurationStore;

    private MessageStoreLogSubject _messageStoreLogSubject;

    private MessageStoreLogSubject _configurationStoreLogSubject;

    public StandardVirtualHost(final Map<String, Object> attributes, Broker<?> broker)
    {
        super(attributes, broker);
    }

    @Override
    public void validate()
    {
        super.validate();
        Map<String,Object> attributes = getActualAttributes();
        Map<String, Object> messageStoreSettings = getMessageStoreSettings();
        if (messageStoreSettings == null)
        {
            throw new IllegalArgumentException("Attribute '"+ org.apache.qpid.server.model.VirtualHost.MESSAGE_STORE_SETTINGS + "' is required.");
        }

        Object storeType = messageStoreSettings.get(MessageStore.STORE_TYPE);

        // need store type and path
        Collection<String> knownTypes = MessageStoreFactory.FACTORY_LOADER.getSupportedTypes();

        if (storeType == null)
        {
            throw new IllegalArgumentException("Setting '"+ MessageStore.STORE_TYPE
                                               +"' is required in attribute " + org.apache.qpid.server.model.VirtualHost.MESSAGE_STORE_SETTINGS + ". Known types are : " + knownTypes);
        }
        else if (!(storeType instanceof String))
        {
            throw new IllegalArgumentException("Setting '"+ MessageStore.STORE_TYPE
                                               +"' is required and must be of type String. "
                                               +"Known types are : " + knownTypes);
        }

        MessageStoreFactory factory = MessageStoreFactory.FACTORY_LOADER.get((String)storeType);
        if(factory == null)
        {
            throw new IllegalArgumentException("Setting '"+ MessageStore.STORE_TYPE
                                               +"' has value '" + storeType + "' which is not one of the valid values: "
                                               + "Known types are : " + knownTypes);
        }

        factory.validateAttributes(attributes);



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
        if (combinedStores)
        {
            configurationStoreSettings = new HashMap<String,Object>(messageStoreSettings);
            configurationStoreSettings.put(DurableConfigurationStore.IS_MESSAGE_STORE_TOO, true);
        }

        if (!combinedStores)
        {
            _configurationStoreLogSubject = new MessageStoreLogSubject(getName(), _durableConfigurationStore.getClass().getSimpleName());
            getEventLogger().message(_configurationStoreLogSubject, ConfigStoreMessages.CREATED());
        }

        _durableConfigurationStore.openConfigurationStore(virtualHost, configurationStoreSettings);

        _messageStore.openMessageStore(virtualHost, virtualHost.getMessageStoreSettings());

        getEventLogger().message(_messageStoreLogSubject, MessageStoreMessages.STORE_LOCATION(_messageStore.getStoreLocation()));

        if (_configurationStoreLogSubject != null)
        {
            getEventLogger().message(_configurationStoreLogSubject, ConfigStoreMessages.STORE_LOCATION(configurationStoreSettings.toString()));
            getEventLogger().message(_configurationStoreLogSubject, ConfigStoreMessages.RECOVERY_START());
        }

        ConfiguredObjectRecordHandler upgraderRecoverer = new ConfiguredObjectRecordRecoveverAndUpgrader(this, getDurableConfigurationRecoverers());

        _durableConfigurationStore.visitConfiguredObjectRecords(upgraderRecoverer);

        if (_configurationStoreLogSubject != null)
        {
            getEventLogger().message(_configurationStoreLogSubject, ConfigStoreMessages.RECOVERY_COMPLETE());
        }

        // If store does not have entries for standard exchanges (amq.*), the following will create them.
        initialiseModel();

        new MessageStoreRecoverer(this, getMessageStoreLogSubject()).recover();

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
