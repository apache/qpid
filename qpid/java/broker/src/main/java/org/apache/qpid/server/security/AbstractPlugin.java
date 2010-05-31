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
package org.apache.qpid.server.security;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;

/**
 * This is intended as the parent for all simple plugins.
 */
public abstract class AbstractPlugin implements SecurityPlugin
{
	protected final Logger _logger = Logger.getLogger(getClass());
    
    public ConfigurationPlugin _config;
	
    public String getPluginName()
    {
        return getClass().getSimpleName();
    }
	
	public Result getDefault()
	{
		return Result.ABSTAIN;
	}
    
    public abstract Result access(ObjectType object, Object instance);

    public abstract Result authorise(Operation operation, ObjectType object, ObjectProperties properties);
    
    public boolean isConfigured()
    {
        if (_config == null)
        {
            return false;
        }
        
        for (String key : _config.getElementsProcessed())
        {
            if (!_config.getConfig().containsKey(key) && _config.getConfig().subset(key).isEmpty())
            {
                return false;
            }
        }

        return true;
    }
}
