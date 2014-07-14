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
package org.apache.qpid.server.store.berkeleydb.replication;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.store.berkeleydb.EnvironmentFacade;
import org.apache.qpid.server.store.berkeleydb.EnvironmentFacadeFactory;
import org.apache.qpid.server.store.berkeleydb.HASettings;

public class ReplicatedEnvironmentFacadeFactory implements EnvironmentFacadeFactory
{
    public static final Pattern NON_REP_JE_PARAM_PATTERN = Pattern.compile("^je\\.(?!rep\\.).*");
    public static final Pattern REP_JE_PARAM_PATTERN = Pattern.compile("^je\\.rep\\..*");

    @Override
    public EnvironmentFacade createEnvironmentFacade(final ConfiguredObject<?> parent)
    {
        final HASettings settings = (HASettings) parent;

        ReplicatedEnvironmentConfiguration configuration = new ReplicatedEnvironmentConfiguration()
        {
            @Override
            public boolean isDesignatedPrimary()
            {
                return settings.isDesignatedPrimary();
            }

            @Override
            public String getStorePath()
            {
                return settings.getStorePath();
            }

            @Override
            public Map<String, String> getParameters()
            {
                return buildEnvironmentConfigParameters(parent);
            }

            @Override
            public Map<String, String> getReplicationParameters()
            {
                return buildReplicationConfigParameters(parent);
            }

            @Override
            public String getHelperNodeName()
            {
                return settings.getHelperNodeName();
            }

            @Override
            public int getQuorumOverride()
            {
                return settings.getQuorumOverride();
            }

            @Override
            public int getPriority()
            {
                return settings.getPriority();
            }

            @Override
            public String getName()
            {
                return parent.getName();
            }

            @Override
            public String getHostPort()
            {
                return settings.getAddress();
            }

            @Override
            public String getHelperHostPort()
            {
                return settings.getHelperAddress();
            }

            @Override
            public String getGroupName()
            {
                return settings.getGroupName();
            }
        };
        return new ReplicatedEnvironmentFacade(configuration);

    }

    private Map<String, String> buildEnvironmentConfigParameters(ConfiguredObject<?> parent)
    {
        return buildConfig(parent, NON_REP_JE_PARAM_PATTERN);
    }

    private Map<String, String> buildReplicationConfigParameters(ConfiguredObject<?> parent)
    {

        return buildConfig(parent, REP_JE_PARAM_PATTERN);
    }

    private Map<String, String> buildConfig(ConfiguredObject<?> parent, Pattern paramName)
    {
        Map<String, String> targetMap = new HashMap<>();
        for (String name : parent.getContext().keySet())
        {
            if (paramName.matcher(name).matches())
            {
                String contextValue = parent.getContext().get(name);
                targetMap.put(name, contextValue);
            }
        }

        return Collections.unmodifiableMap(targetMap);
    }


}
