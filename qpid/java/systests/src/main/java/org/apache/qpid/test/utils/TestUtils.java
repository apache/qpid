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
package org.apache.qpid.test.utils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.plugin.DurableConfigurationStoreFactory;
import org.apache.qpid.server.store.ConfiguredObjectRecordImpl;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.JsonFileConfigStore;
import org.apache.qpid.server.store.MemoryConfigurationStore;
import org.apache.qpid.server.store.MemoryMessageStore;
import org.apache.qpid.server.virtualhost.ProvidedStoreVirtualHost;
import org.apache.qpid.util.FileUtils;
import org.apache.qpid.util.Strings;

public class TestUtils
{
    public static String dumpThreads()
    {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(true, true);
        StringBuilder dump = new StringBuilder();
        dump.append(String.format("%n"));
        for (ThreadInfo threadInfo : threadInfos)
        {
            dump.append(threadInfo);
        }

        long[] deadLocks = threadMXBean.findDeadlockedThreads();
        if (deadLocks != null && deadLocks.length > 0)
        {
            ThreadInfo[] deadlockedThreads = threadMXBean.getThreadInfo(deadLocks);
            dump.append(String.format("%n"));
            dump.append("Deadlock is detected!");
            dump.append(String.format("%n"));
            for (ThreadInfo threadInfo : deadlockedThreads)
            {
                dump.append(threadInfo);
            }
        }
        return dump.toString();
    }

    public static String createStoreWithVirtualHostEntry(Map<String, Object> messageStoreSettings, TestBrokerConfiguration config, String configStoreType)
    {
        UUID virtualHostId = UUID.randomUUID();
        Map<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put(VirtualHost.NAME, TestBrokerConfiguration.ENTRY_NAME_VIRTUAL_HOST);
        virtualHostAttributes.put(VirtualHost.ID, virtualHostId);
        virtualHostAttributes.put(VirtualHost.TYPE, MemoryMessageStore.TYPE.equals(configStoreType) ? configStoreType : ProvidedStoreVirtualHost.VIRTUAL_HOST_TYPE);
        virtualHostAttributes.put(VirtualHost.MESSAGE_STORE_SETTINGS, messageStoreSettings);
        virtualHostAttributes.put(VirtualHost.MODEL_VERSION, BrokerModel.MODEL_VERSION);

        // If using MMS, switch to split store with JSON config store.
        if (MemoryConfigurationStore.TYPE.equals(configStoreType))
        {
            configStoreType =  JsonFileConfigStore.TYPE;
        }
        DurableConfigurationStoreFactory storeFactory = DurableConfigurationStoreFactory.FACTORY_LOADER.get(configStoreType);
        DurableConfigurationStore store = storeFactory.createDurableConfigurationStore();

        config.setObjectAttribute(VirtualHostNode.class, TestBrokerConfiguration.ENTRY_NAME_VIRTUAL_HOST, VirtualHostNode.TYPE, configStoreType);

        Map<String,Object> nodeAttributes = config.getObjectAttributes(VirtualHostNode.class, TestBrokerConfiguration.ENTRY_NAME_VIRTUAL_HOST);
        String storePath = (String)nodeAttributes.get(DurableConfigurationStore.STORE_PATH);
        String path = Strings.expand(storePath, false, Strings.JAVA_SYS_PROPS_RESOLVER, Strings.ENV_VARS_RESOLVER);

        File pathFile = new File(path);
        if (pathFile.exists())
        {
            FileUtils.delete(pathFile, true);
        }

        Map<String, Object> attributes =  new HashMap<String, Object>(nodeAttributes);
        attributes.put(DurableConfigurationStore.STORE_PATH, path);

        VirtualHostNode<?> virtualHostNode = mock(VirtualHostNode.class);
        when(virtualHostNode.getModel()).thenReturn(BrokerModel.getInstance());
        when(virtualHostNode.getName()).thenReturn(TestBrokerConfiguration.ENTRY_NAME_VIRTUAL_HOST);

        try
        {
            store.openConfigurationStore(virtualHostNode, attributes);
            store.create(new ConfiguredObjectRecordImpl(virtualHostId, VirtualHost.class.getSimpleName(), virtualHostAttributes));
        }
        finally
        {
            store.closeConfigurationStore();
        }
        return path;
    }
}
