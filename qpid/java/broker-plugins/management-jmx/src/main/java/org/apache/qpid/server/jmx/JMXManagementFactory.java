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

import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.plugin.PluginFactory;

public class JMXManagementFactory implements PluginFactory
{
    private static final Logger LOGGER = Logger.getLogger(JMXManagementFactory.class);

    @Override
    public Plugin createInstance(UUID id, Map<String, Object> attributes, Broker broker)
    {
        if (JMXManagement.PLUGIN_TYPE.equals(attributes.get(PLUGIN_TYPE)))
        {
            return new JMXManagement(id, broker, attributes);
        }
        else
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Skipping registration of JMX plugin as JMX Management disabled in config.");
            }
            return null;
        }
    }
}
