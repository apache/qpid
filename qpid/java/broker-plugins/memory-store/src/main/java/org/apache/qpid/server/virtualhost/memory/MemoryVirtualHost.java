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
package org.apache.qpid.server.virtualhost.memory;

import java.util.Map;

import org.apache.qpid.server.logging.subjects.MessageStoreLogSubject;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.store.MemoryMessageStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.virtualhost.AbstractVirtualHost;

@ManagedObject(category = false, type = MemoryVirtualHost.VIRTUAL_HOST_TYPE)
public class MemoryVirtualHost extends AbstractVirtualHost<MemoryVirtualHost>
{
    public static final String VIRTUAL_HOST_TYPE = "Memory";
    private MessageStore _messageStore;
    private MessageStoreLogSubject _messageStoreLogSubject;

    @ManagedObjectFactoryConstructor
    public MemoryVirtualHost(final Map<String, Object> attributes,
                             final VirtualHostNode<?> virtualHostNode)
    {
        super(attributes, virtualHostNode);
    }


    @Override
    protected void initialiseStorage()
    {
        _messageStore = new MemoryMessageStore();

        _messageStoreLogSubject = new MessageStoreLogSubject(getName(), _messageStore.getClass().getSimpleName());

    }

    @Override
    protected MessageStoreLogSubject getMessageStoreLogSubject()
    {
        return _messageStoreLogSubject;
    }

    @Override
    public MessageStore getMessageStore()
    {
        return _messageStore;
    }
}
