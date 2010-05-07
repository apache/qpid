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
package org.apache.qpid.server.virtualhost.plugin.SlowConsumer;

import org.apache.qpid.server.configuration.plugin.ConfigurationPluginFactory;
import org.apache.qpid.server.virtualhost.plugin.VirtualHostPluginFactory;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Activator that loads our OSGi bundles for the Slow Consumer Detection plugin.
 *
 * This includes Configuration 
 *
 * @author ritchiem
 */
public class Activator implements BundleActivator
{
    public void start(BundleContext ctx) throws Exception
    {
        if (null != ctx)
        {
            ctx.registerService(ConfigurationPluginFactory.class.getName(), new SlowConsumerDetectionQueueConfiguration.SlowConsumerDetectionQueueConfigurationFactory(), null);
            ctx.registerService(ConfigurationPluginFactory.class.getName(), new SlowConsumerDetectionConfiguration.SlowConsumerDetectionConfigurationFactory(), null);
            ctx.registerService(ConfigurationPluginFactory.class.getName(), new SlowConsumerDetectionPolicyConfiguration.SlowConsumerDetectionPolicyConfigurationFactory(), null);
            ctx.registerService(VirtualHostPluginFactory.class.getName(), new SlowConsumerDetection.SlowConsumerFactory(), null);
            ctx.registerService(SlowConsumerPolicyPluginFactory.class.getName(), new TopicDeletePolicy.DeletePolicyFactory(), null);
        }
    }

    public void stop(BundleContext ctx) throws Exception
    {
        // no need to do anything here, osgi will unregister the service for us
    }

}
