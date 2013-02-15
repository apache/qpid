package org.apache.qpid.server.configuration.store;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;

public class StoreConfigurationChangeListenerTest extends QpidTestCase
{
    private ConfigurationEntryStore _store;
    private StoreConfigurationChangeListener _listener;

    protected void setUp() throws Exception
    {
        super.setUp();
        _store = mock(ConfigurationEntryStore.class);
        _listener = new StoreConfigurationChangeListener(_store);
    }

    public void testStateChanged()
    {
        notifyBrokerStarted();
        UUID id = UUID.randomUUID();
        ConfiguredObject object = mock(VirtualHost.class);
        when(object.getId()).thenReturn(id);
        _listener.stateChanged(object, State.ACTIVE, State.DELETED);
        verify(_store).remove(id);
    }

    public void testChildAdded()
    {
        notifyBrokerStarted();
        Broker broker = mock(Broker.class);
        VirtualHost child = mock(VirtualHost.class);
        _listener.childAdded(broker, child);
        verify(_store).save(any(ConfigurationEntry.class), any(ConfigurationEntry.class));
    }

    public void testChildRemoved()
    {
        notifyBrokerStarted();
        Broker broker = mock(Broker.class);
        VirtualHost child = mock(VirtualHost.class);
        _listener.childRemoved(broker, child);
        verify(_store).save(any(ConfigurationEntry.class));
    }

    public void testAttributeSet()
    {
        notifyBrokerStarted();
        Broker broker = mock(Broker.class);
        _listener.attributeSet(broker, Broker.FLOW_CONTROL_SIZE_BYTES, null, 1);
        verify(_store).save(any(ConfigurationEntry.class));
    }

    public void testChildAddedForVirtualHost()
    {
        notifyBrokerStarted();

        VirtualHost object = mock(VirtualHost.class);
        Queue queue = mock(Queue.class);
        _listener.childAdded(object, queue);
        verifyNoMoreInteractions(_store);
    }

    private void notifyBrokerStarted()
    {
        Broker broker = mock(Broker.class);
        _listener.stateChanged(broker, State.INITIALISING, State.ACTIVE);
    }
}
