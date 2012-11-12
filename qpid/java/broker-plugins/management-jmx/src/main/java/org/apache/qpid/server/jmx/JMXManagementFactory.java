/*
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
 */
package org.apache.qpid.server.jmx;

import static org.apache.qpid.server.util.MapValueConverter.getBooleanAttribute;
import static org.apache.qpid.server.util.MapValueConverter.getStringAttribute;

import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.plugin.PluginFactory;

public class JMXManagementFactory implements PluginFactory
{
    private static final Logger LOGGER = Logger.getLogger(JMXManagementFactory.class);

    public static final String USE_PLATFORM_MBEAN_SERVER = "usePlatformMBeanServer";
    public static final String KEY_STORE_PATH = "keyStorePath";
    public static final String KEY_STORE_PASSWORD = "keyStorePassword";
    public static final String MANAGEMENT_RIGHTS_INFER_ALL_ACCESS = "managementRightsInferAllAccess";

    public static final String PLUGIN_NAME = "MANAGEMENT-JMX";

    @Override
    public ConfiguredObject createInstance(UUID id, Map<String, Object> attributes, Broker broker)
    {
        if (PLUGIN_NAME.equals(attributes.get(PLUGIN_TYPE)))
        {
            JMXConfiguration jmxConfiguration = new JMXConfiguration(
                    getBooleanAttribute(USE_PLATFORM_MBEAN_SERVER, attributes, true),
                    getStringAttribute(KEY_STORE_PATH, attributes, null),
                    getStringAttribute(KEY_STORE_PASSWORD, attributes, null),
                    getBooleanAttribute(MANAGEMENT_RIGHTS_INFER_ALL_ACCESS, attributes, true));

            return new JMXManagement(id, broker, jmxConfiguration);
        }
        else
        {
            if(LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Skipping registration of JMX plugin as JMX Management disabled in config.");
            }
            return null;
        }
    }
}
