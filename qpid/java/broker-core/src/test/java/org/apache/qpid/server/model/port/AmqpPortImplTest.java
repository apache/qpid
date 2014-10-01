package org.apache.qpid.server.model.port;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.test.utils.QpidTestCase;

public class AmqpPortImplTest extends QpidTestCase
{
    private static final String AUTHENTICATION_PROVIDER_NAME = "test";
    private TaskExecutor _taskExecutor;
    private Broker _broker;
    private ServerSocket _socket;
    private AmqpPortImpl _port;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _taskExecutor = CurrentThreadTaskExecutor.newStartedInstance();
        Model model = BrokerModel.getInstance();

        _broker = mock(Broker.class);
        when(_broker.getTaskExecutor()).thenReturn(_taskExecutor);
        when(_broker.getModel()).thenReturn(model);
        when(_broker.getId()).thenReturn(UUID.randomUUID());
        when(_broker.getSecurityManager()).thenReturn(new SecurityManager(_broker, false));
        when(_broker.getCategoryClass()).thenReturn(Broker.class);
        when(_broker.getEventLogger()).thenReturn(new EventLogger());
        AuthenticationProvider<?> provider = mock(AuthenticationProvider.class);
        when(provider.getName()).thenReturn(AUTHENTICATION_PROVIDER_NAME);
        when(provider.getParent(Broker.class)).thenReturn(_broker);
        when(_broker.getChildren(AuthenticationProvider.class)).thenReturn(Collections.<AuthenticationProvider>singleton(provider));
        when(_broker.getChildByName(AuthenticationProvider.class, AUTHENTICATION_PROVIDER_NAME)).thenReturn(provider);
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            if (_socket != null)
            {
                _socket.close();
            }
            _taskExecutor.stop();
        }
        finally
        {
            if (_port != null)
            {
                _port.close();
            }
            super.tearDown();
        }
    }

    public void testValidateOnCreate() throws Exception
    {
        _socket = openSocket();

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(AmqpPort.PORT, _socket.getLocalPort());
        attributes.put(AmqpPort.NAME, getTestName());
        attributes.put(AmqpPort.AUTHENTICATION_PROVIDER, AUTHENTICATION_PROVIDER_NAME);
        _port = new AmqpPortImpl(attributes, _broker);
        try
        {
            _port.create();
            fail("Creation should fail due to validation check");
        }
        catch (IllegalConfigurationException e)
        {
            assertEquals("Unexpected exception message",
                    String.format("Cannot bind to port %d and binding address '%s'. Port is already is use.",
                        _socket.getLocalPort(), "*"), e.getMessage());
        }
    }

    private ServerSocket openSocket() throws IOException
    {
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(findFreePort()));
        return serverSocket;
    }
}
