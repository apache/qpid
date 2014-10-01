package org.apache.qpid.server.model.testmodel;

import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.Map;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;

@ManagedObject
public class TestConfiguredObject extends AbstractConfiguredObject
{
    private boolean _throwExceptionOnOpen;
    private boolean _opened;
    private boolean _throwExceptionOnValidationOnCreate;
    private boolean _throwExceptionOnPostResolve;
    private boolean _throwExceptionOnCreate;

    public TestConfiguredObject(String name)
    {
        this(createParents(), Collections.<String, Object>singletonMap(ConfiguredObject.NAME, name), createTaskExecutor(), new TestModel(null));
    }

    public final static Map<Class<? extends ConfiguredObject>, ConfiguredObject<?>> createParents()
    {
        return Collections.<Class<? extends ConfiguredObject>, ConfiguredObject<?>>singletonMap(null, mock(ConfiguredObject.class));
    }

    public final static TaskExecutor createTaskExecutor()
    {
        TaskExecutor taskExecutor = new CurrentThreadTaskExecutor();
        taskExecutor.start();
        return taskExecutor;
    }

    public TestConfiguredObject(Map parents, Map<String, Object> attributes, TaskExecutor taskExecutor, Model model)
    {
        super(parents, attributes, taskExecutor, model);
        _opened = false;
    }

    @Override
    protected void postResolve()
    {
        if (_throwExceptionOnPostResolve)
        {
            throw new IllegalConfigurationException("Cannot resolve");
        }
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
}
