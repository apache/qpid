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
package org.apache.qpid.server.store.berkeleydb;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.sleepycat.je.config.ConfigParam;
import com.sleepycat.je.config.EnvironmentParams;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.store.FileBasedSettings;

public class StandardEnvironmentFacadeFactory implements EnvironmentFacadeFactory
{
    @SuppressWarnings("unchecked")
    @Override
    public EnvironmentFacade createEnvironmentFacade(final ConfiguredObject<?> parent)
    {
        final FileBasedSettings settings = (FileBasedSettings)parent;
        final String storeLocation = settings.getStorePath();

        StandardEnvironmentConfiguration sec = new StandardEnvironmentConfiguration()
        {
            @Override
            public String getName()
            {
                return parent.getName();
            }

            @Override
            public String getStorePath()
            {
                return storeLocation;
            }

            @Override
            public Map<String, String> getParameters()
            {
                return buildEnvironmentConfiguration(parent);
            }
        };

        return new StandardEnvironmentFacade(sec);
    }

    private Map<String, String> buildEnvironmentConfiguration(ConfiguredObject<?> parent)
    {
        Map<String, String> envConfigMap = new HashMap<>();

        for (ConfigParam cp : EnvironmentParams.SUPPORTED_PARAMS.values())
        {
            final String parameterName = cp.getName();
            Set<String> contextKeys = parent.getContextKeys(false);
            if (!cp.isForReplication() && contextKeys.contains(parameterName))
            {
                envConfigMap.put(parameterName, parent.getContextValue(String.class, parameterName));
            }
        }
        return Collections.unmodifiableMap(envConfigMap);
    }
}
