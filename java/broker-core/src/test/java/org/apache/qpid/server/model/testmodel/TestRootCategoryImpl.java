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
package org.apache.qpid.server.model.testmodel;

import java.util.Map;

import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.State;

@ManagedObject( category = false , type = "test" )
public class TestRootCategoryImpl extends AbstractConfiguredObject<TestRootCategoryImpl>
        implements TestRootCategory<TestRootCategoryImpl>
{
    @ManagedAttributeField
    private String _automatedPersistedValue;

    @ManagedAttributeField
    private String _automatedNonPersistedValue;

    @ManagedAttributeField
    private String _defaultedValue;

    @ManagedAttributeField
    private String _stringValue;

    @ManagedAttributeField
    private Map<String,String> _mapValue;

    @ManagedAttributeField
    private String _validValue;


    @ManagedObjectFactoryConstructor
    public TestRootCategoryImpl(final Map<String, Object> attributes)
    {
        super(parentsMap(), attributes, newTaskExecutor(), TestModel.getInstance());
    }

    private static CurrentThreadTaskExecutor newTaskExecutor()
    {
        CurrentThreadTaskExecutor currentThreadTaskExecutor = new CurrentThreadTaskExecutor();
        currentThreadTaskExecutor.start();
        return currentThreadTaskExecutor;
    }

    public TestRootCategoryImpl(final Map<String, Object> attributes,
                                final TaskExecutor taskExecutor)
    {
        super(parentsMap(), attributes, taskExecutor);
    }

    @Override
    protected boolean setState(final State desiredState)
    {
        return false;
    }

    @Override
    public String getAutomatedPersistedValue()
    {
        return _automatedPersistedValue;
    }

    @Override
    public String getAutomatedNonPersistedValue()
    {
        return _automatedNonPersistedValue;
    }

    @Override
    public String getDefaultedValue()
    {
        return _defaultedValue;
    }

    @Override
    public String getStringValue()
    {
        return _stringValue;
    }

    @Override
    public Map<String, String> getMapValue()
    {
        return _mapValue;
    }

    @Override
    public State getState()
    {
        return null;
    }

    @Override
    public String getValidValue()
    {
        return _validValue;
    }
}
