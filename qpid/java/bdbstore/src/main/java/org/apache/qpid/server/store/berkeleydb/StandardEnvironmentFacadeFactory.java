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

import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.store.MessageStore;

public class StandardEnvironmentFacadeFactory implements EnvironmentFacadeFactory
{

    @SuppressWarnings("unchecked")
    @Override
    public EnvironmentFacade createEnvironmentFacade(Map<String, Object> messageStoreSettings)
    {
        Map<String, String> envConfigMap = new HashMap<String, String>();
        envConfigMap.putAll(EnvironmentFacade.ENVCONFIG_DEFAULTS);

        Object environmentConfigurationAttributes = messageStoreSettings.get(ENVIRONMENT_CONFIGURATION);
        if (environmentConfigurationAttributes instanceof Map)
        {
            envConfigMap.putAll((Map<String, String>) environmentConfigurationAttributes);
        }
        String storeLocation = (String) messageStoreSettings.get(MessageStore.STORE_PATH);
        return new StandardEnvironmentFacade(storeLocation, envConfigMap);
    }

    @Override
    public String getType()
    {
        return StandardEnvironmentFacade.TYPE;
    }

}
