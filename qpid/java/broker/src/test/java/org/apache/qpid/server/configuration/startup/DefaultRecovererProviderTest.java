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
package org.apache.qpid.server.configuration.startup;

import static org.mockito.Mockito.mock;
import junit.framework.TestCase;

import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public class DefaultRecovererProviderTest extends TestCase
{
    public void testGetRecoverer()
    {
        String[] supportedTypes = {Broker.class.getSimpleName(),
                VirtualHost.class.getSimpleName(), AuthenticationProvider.class.getSimpleName(),
                GroupProvider.class.getSimpleName(), Plugin.class.getSimpleName(), Port.class.getSimpleName()};

        // mocking the required object
        StatisticsGatherer statisticsGatherer = mock(StatisticsGatherer.class);
        VirtualHostRegistry virtualHostRegistry = mock(VirtualHostRegistry.class);
        LogRecorder logRecorder = mock(LogRecorder.class);
        RootMessageLogger rootMessageLogger = mock(RootMessageLogger.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);

        DefaultRecovererProvider provider = new DefaultRecovererProvider(statisticsGatherer, virtualHostRegistry, logRecorder, rootMessageLogger, taskExecutor);
        for (String configuredObjectType : supportedTypes)
        {
            ConfiguredObjectRecoverer<?> recovever = provider.getRecoverer(configuredObjectType);
            assertNotNull("Null recoverer for type: " + configuredObjectType, recovever);
        }
    }

}
