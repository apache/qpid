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
package org.apache.qpid.server.model.testmodels.singleton;

import java.util.Map;
import java.util.Set;

import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.testmodels.TestSecurityManager;
import org.apache.qpid.server.security.SecurityManager;

@ManagedObject( category = false, type = TestSingletonImpl.TEST_SINGLETON_TYPE)
public class TestSingletonImpl extends AbstractConfiguredObject<TestSingletonImpl>
        implements TestSingleton<TestSingletonImpl>
{
    public static final String TEST_SINGLETON_TYPE = "testsingleton";

    public static final int DERIVED_VALUE = -100;
    private final SecurityManager _securityManager;

    @ManagedAttributeField
    private String _automatedPersistedValue;

    @ManagedAttributeField
    private String _automatedNonPersistedValue;

    @ManagedAttributeField
    private String _defaultedValue;

    @ManagedAttributeField
    private String _stringValue;

    @ManagedAttributeField
    private int _intValue;

    @ManagedAttributeField
    private Map<String,String> _mapValue;

    @ManagedAttributeField
    private String _validValue;

    @ManagedAttributeField
    private TestEnum _enumValue;

    @ManagedAttributeField
    private Set<TestEnum> _enumSetValues;

    @ManagedAttributeField
    private String _secureValue;


    @ManagedObjectFactoryConstructor
    public TestSingletonImpl(final Map<String, Object> attributes)
    {
        super(parentsMap(), attributes, newTaskExecutor(), TestModel.getInstance());
        _securityManager = new TestSecurityManager(this);
    }

    private static CurrentThreadTaskExecutor newTaskExecutor()
    {
        CurrentThreadTaskExecutor currentThreadTaskExecutor = new CurrentThreadTaskExecutor();
        currentThreadTaskExecutor.start();
        return currentThreadTaskExecutor;
    }

    public TestSingletonImpl(final Map<String, Object> attributes,
                             final TaskExecutor taskExecutor)
    {
        super(parentsMap(), attributes, taskExecutor);
        _securityManager = new TestSecurityManager(this);
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
    public TestEnum getEnumValue()
    {
        return _enumValue;
    }

    @Override
    public Set<TestEnum> getEnumSetValues()
    {
        return _enumSetValues;
    }

    @Override
    public String getValidValue()
    {
        return _validValue;
    }

    @Override
    public int getIntValue()
    {
        return _intValue;
    }

    @Override
    public long getDerivedValue()
    {
        return DERIVED_VALUE;
    }

    @Override
    public String getSecureValue()
    {
        return _secureValue;
    }

    @Override
    protected SecurityManager getSecurityManager()
    {
        return _securityManager;
    }
}
