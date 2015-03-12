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

package org.apache.qpid.server.model.testmodels.lifecycle;

import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.ConfiguredObjectFactoryImpl;
import org.apache.qpid.server.model.ConfiguredObjectTypeRegistry;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;
import org.apache.qpid.server.model.testmodels.TestSecurityManager;
import org.apache.qpid.server.plugin.ConfiguredObjectRegistration;
import org.apache.qpid.server.security.SecurityManager;

@ManagedObject
public class TestConfiguredObject extends AbstractConfiguredObject
{
    private final SecurityManager _securityManager;
    private boolean _opened;
    private boolean _validated;
    private boolean _resolved;
    private boolean _throwExceptionOnOpen;
    private boolean _throwExceptionOnValidationOnCreate;
    private boolean _throwExceptionOnPostResolve;
    private boolean _throwExceptionOnCreate;
    private boolean _throwExceptionOnValidate;

    public final static Map<Class<? extends ConfiguredObject>, ConfiguredObject<?>> createParents(ConfiguredObject<?> parent)
    {
        return Collections.<Class<? extends ConfiguredObject>, ConfiguredObject<?>>singletonMap(parent.getCategoryClass(), parent);
    }

    public TestConfiguredObject(String name)
    {
        this(name, mock(ConfiguredObject.class), CurrentThreadTaskExecutor.newStartedInstance());
    }

    public TestConfiguredObject(String name, ConfiguredObject<?> parent, TaskExecutor taskExecutor)
    {
        this(createParents(parent), Collections.<String, Object>singletonMap(ConfiguredObject.NAME, name), taskExecutor, TestConfiguredObjectModel.INSTANCE);
    }

    public TestConfiguredObject(ConfiguredObject<?> parent, Map<String, Object> attributes)
    {
        this(createParents(parent), attributes, parent.getTaskExecutor(), TestConfiguredObjectModel.INSTANCE);
    }

    public TestConfiguredObject(Map parents, Map<String, Object> attributes, TaskExecutor taskExecutor, Model model)
    {
        super(parents, attributes, taskExecutor, model);
        _opened = false;
        _securityManager = new TestSecurityManager(this);
    }

    @Override
    protected SecurityManager getSecurityManager()
    {
        return _securityManager;
    }

    @Override
    protected void postResolve()
    {
        if (_throwExceptionOnPostResolve)
        {
            throw new IllegalConfigurationException("Cannot resolve");
        }
        _resolved = true;
    }

    @Override
    protected void onCreate()
    {
        if (_throwExceptionOnCreate)
        {
            throw new IllegalConfigurationException("Cannot create");
        }
    }

    @Override
    protected void onOpen()
    {
        if (_throwExceptionOnOpen)
        {
            throw new IllegalConfigurationException("Cannot open");
        }
        _opened = true;
    }

    @Override
    protected void validateOnCreate()
    {
        if (_throwExceptionOnValidationOnCreate)
        {
            throw new IllegalConfigurationException("Cannot validate on create");
        }
    }

    @Override
    public void onValidate()
    {
        if (_throwExceptionOnValidate)
        {
            throw new IllegalConfigurationException("Cannot validate");
        }
        _validated = true;
    }

    @StateTransition( currentState = {State.ERRORED, State.UNINITIALIZED}, desiredState = State.ACTIVE )
    protected void activate()
    {
        setState(State.ACTIVE);
    }

    @StateTransition( currentState = {State.ERRORED, State.UNINITIALIZED}, desiredState = State.DELETED )
    protected void doDelete()
    {
        setState(State.DELETED);
    }

    public boolean isOpened()
    {
        return _opened;
    }

    public void setThrowExceptionOnOpen(boolean throwException)
    {
        _throwExceptionOnOpen = throwException;
    }

    public void setThrowExceptionOnValidationOnCreate(boolean throwException)
    {
        _throwExceptionOnValidationOnCreate = throwException;
    }

    public void setThrowExceptionOnPostResolve(boolean throwException)
    {
        _throwExceptionOnPostResolve = throwException;
    }

    public void setThrowExceptionOnCreate(boolean throwExceptionOnCreate)
    {
        _throwExceptionOnCreate = throwExceptionOnCreate;
    }

    public void setThrowExceptionOnValidate(boolean throwException)
    {
        _throwExceptionOnValidate= throwException;
    }

    public boolean isValidated()
    {
        return _validated;
    }

    public boolean isResolved()
    {
        return _resolved;
    }

    public static class TestConfiguredObjectModel extends  Model
    {

        private Collection<Class<? extends ConfiguredObject>> CATEGORIES = Collections.<Class<? extends ConfiguredObject>>singleton(TestConfiguredObject.class);
        private ConfiguredObjectFactoryImpl _configuredObjectFactory;

        private static TestConfiguredObjectModel INSTANCE = new TestConfiguredObjectModel();
        private ConfiguredObjectTypeRegistry _configuredObjectTypeRegistry;

        private TestConfiguredObjectModel()
        {
            _configuredObjectFactory = new ConfiguredObjectFactoryImpl(this);
            ConfiguredObjectRegistration configuredObjectRegistration = new ConfiguredObjectRegistration()
            {
                @Override
                public Collection<Class<? extends ConfiguredObject>> getConfiguredObjectClasses()
                {
                    return CATEGORIES;
                }

                @Override
                public String getType()
                {
                    return TestConfiguredObjectModel.class.getSimpleName();
                }
            };
            _configuredObjectTypeRegistry = new ConfiguredObjectTypeRegistry(Arrays.asList(configuredObjectRegistration), CATEGORIES);
        }

        @Override
        public Collection<Class<? extends ConfiguredObject>> getSupportedCategories()
        {
            return CATEGORIES;
        }

        @Override
        public Collection<Class<? extends ConfiguredObject>> getChildTypes(Class<? extends ConfiguredObject> parent)
        {
            return TestConfiguredObject.class.isAssignableFrom(parent)
                    ? CATEGORIES
                    : Collections.<Class<? extends ConfiguredObject>>emptySet();
        }

        @Override
        public Class<? extends ConfiguredObject> getRootCategory()
        {
            return TestConfiguredObject.class;
        }

        @Override
        public Collection<Class<? extends ConfiguredObject>> getParentTypes(final Class<? extends ConfiguredObject> child)
        {
            return TestConfiguredObject.class.isAssignableFrom(child)
                    ? CATEGORIES
                    : Collections.<Class<? extends ConfiguredObject>>emptySet();
        }

        @Override
        public int getMajorVersion()
        {
            return 99;
        }

        @Override
        public int getMinorVersion()
        {
            return 99;
        }

        @Override
        public ConfiguredObjectFactory getObjectFactory()
        {
            return _configuredObjectFactory;
        }

        @Override
        public ConfiguredObjectTypeRegistry getTypeRegistry()
        {
            return _configuredObjectTypeRegistry;
        }
    }
}
