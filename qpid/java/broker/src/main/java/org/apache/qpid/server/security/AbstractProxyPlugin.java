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

import org.apache.commons.configuration.Configuration;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;

/**
 * This {@link SecurityPlugin} proxies the authorise calls to a serries of methods, one per {@link Operation}.
 * 
 * Plugins that extend this class should override the relevant authorise method and implement their own
 * {@link #setConfiguration(Configuration)} method.
 */
public abstract class AbstractProxyPlugin extends AbstractPlugin
{
    public Result authoriseConsume(ObjectType object, ObjectProperties properties)
    {
        return getDefault();
    }
    
    public Result authorisePublish(ObjectType object, ObjectProperties properties)
    {
        return getDefault();
    }
    
    public Result authoriseCreate(ObjectType object, ObjectProperties properties)
    {
        return getDefault();
    }
    
    public Result authoriseAccess(ObjectType object, ObjectProperties properties)
    {
        return getDefault();
    }
    
    public Result authoriseBind(ObjectType object, ObjectProperties properties)
    {
        return getDefault();
    }
    
    public Result authoriseUnbind(ObjectType object, ObjectProperties properties)
    {
        return getDefault();
    }
    
    public Result authoriseDelete(ObjectType object, ObjectProperties properties)
    {
        return getDefault();
    }
    
    public Result authorisePurge(ObjectType object, ObjectProperties properties)
    {
        return getDefault();
    }
    
    public Result authoriseExecute(ObjectType object, ObjectProperties properties)
    {
        return getDefault();
    }
    
    public Result authoriseUpdate(ObjectType object, ObjectProperties properties)
    {
        return getDefault();
    }
    
    public Result accessVirtualhost(Object instance)
    {
        return getDefault();
    }

    @Override
    public Result access(ObjectType objectType, Object instance)
    {
        switch (objectType)
        {
			case VIRTUALHOST:
				return accessVirtualhost(instance);
        }
		
		return getDefault();   
    }

    @Override
    public Result authorise(Operation operation, ObjectType objectType, ObjectProperties properties)
    {
        switch (operation)
        {
			case CONSUME:
				return authoriseConsume(objectType, properties);
			case PUBLISH:
				return authorisePublish(objectType, properties);
			case CREATE:
				return authoriseCreate(objectType, properties);
			case ACCESS:
				return authoriseAccess(objectType, properties);
			case BIND:
				return authoriseBind(objectType, properties);
			case UNBIND:
				return authoriseUnbind(objectType, properties);
			case DELETE:
				return authoriseDelete(objectType, properties);
			case PURGE:
				return authorisePurge(objectType, properties);
			case EXECUTE:
				return authoriseExecute(objectType, properties);
			case UPDATE:
				return authoriseUpdate(objectType, properties);
		}
		
		return getDefault(); 
    }
}
