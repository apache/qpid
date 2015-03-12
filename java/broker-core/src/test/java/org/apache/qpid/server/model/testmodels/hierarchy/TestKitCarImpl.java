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
package org.apache.qpid.server.model.testmodels.hierarchy;

import java.util.Map;

import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.testmodels.TestSecurityManager;
import org.apache.qpid.server.security.SecurityManager;

@ManagedObject( category = false,
                type = TestKitCarImpl.TEST_KITCAR_TYPE)
public class TestKitCarImpl extends AbstractConfiguredObject<TestKitCarImpl>
        implements TestKitCar<TestKitCarImpl>
{
    public static final String TEST_KITCAR_TYPE = "testkitcar";
    private final SecurityManager _securityManager;

    @ManagedObjectFactoryConstructor
    public TestKitCarImpl(final Map<String, Object> attributes)
    {
        super(parentsMap(), attributes, newTaskExecutor(), TestModel.getInstance());
        _securityManager = new TestSecurityManager(this);
    }

    @Override
    public <C extends ConfiguredObject> C createChild(final Class<C> childClass,
                                                      final Map<String, Object> attributes,
                                                      final ConfiguredObject... otherParents)
    {
        return (C) getObjectFactory().create(childClass, attributes, this);
    }

    private static CurrentThreadTaskExecutor newTaskExecutor()
    {
        CurrentThreadTaskExecutor currentThreadTaskExecutor = new CurrentThreadTaskExecutor();
        currentThreadTaskExecutor.start();
        return currentThreadTaskExecutor;
    }

    @Override
    protected SecurityManager getSecurityManager()
    {
        return _securityManager;
    }
}
