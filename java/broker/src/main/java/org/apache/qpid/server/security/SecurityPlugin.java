/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.qpid.server.security;

import org.apache.qpid.server.plugins.Plugin;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;

/**
 * The two methods, {@link #access(ObjectType, Object)} and {@link #authorise(Operation, ObjectType, ObjectProperties)}, 
 * return the {@link Result} of the security decision, which may be to {@link Result#ABSTAIN} if no decision is made
 * by this plugin.
 */
public interface SecurityPlugin extends Plugin
{	
	/**
	 * Default result for {@link #access(ObjectType, Object)} or {@link #authorise(Operation, ObjectType, ObjectProperties)}.
	 */
	Result getDefault();
	
    /**
     * Authorise access granted to an object instance.
     */
    Result access(ObjectType objectType, Object instance);
    
    /**
     * Authorise an operation on an object defined by a set of properties.
     */
    Result authorise(Operation operation, ObjectType objectType, ObjectProperties properties);
}
