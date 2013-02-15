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
package org.apache.qpid.server.store;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.plugin.QpidServiceLoader;

public class MessageStoreCreator
{
    private Map<String, MessageStoreFactory> _factories = new HashMap<String, MessageStoreFactory>();

    public MessageStoreCreator()
    {
        QpidServiceLoader<MessageStoreFactory> qpidServiceLoader = new QpidServiceLoader<MessageStoreFactory>();
        Iterable<MessageStoreFactory> factories = qpidServiceLoader.atLeastOneInstanceOf(MessageStoreFactory.class);
        for (MessageStoreFactory messageStoreFactory : factories)
        {
            String type = messageStoreFactory.getType();
            MessageStoreFactory factory = _factories.put(type.toLowerCase(), messageStoreFactory);
            if (factory != null)
            {
                throw new IllegalStateException("MessageStoreFactory with type name '" + type
                        + "' is already registered using class '" + factory.getClass().getName() + "', can not register class '"
                        + messageStoreFactory.getClass().getName() + "'");
            }
        }
    }

    public MessageStore createMessageStore(String storeType)
    {
        MessageStoreFactory factory = _factories.get(storeType.toLowerCase());
        if (factory == null)
        {
            throw new IllegalConfigurationException("Unknown store type: " + storeType);
        }
        return factory.createMessageStore();
    }

    public Collection<MessageStoreFactory> getFactories()
    {
        return Collections.unmodifiableCollection(_factories.values());
    }
}
