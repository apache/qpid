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
package org.apache.qpid.server.transport;

import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.*;

import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.configuration.ConnectionConfig;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.GenericActor;
import org.apache.qpid.server.logging.messages.ConnectionMessages;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.transport.Connection;
import org.apache.qpid.transport.ConnectionCloseCode;
import org.apache.qpid.transport.ExecutionErrorCode;
import org.apache.qpid.transport.ExecutionException;
import org.apache.qpid.transport.Method;
import org.apache.qpid.transport.ProtocolEvent;
import org.apache.qpid.transport.Session;

public class ServerConnection extends Connection implements AMQConnectionModel, LogSubject
{
    private ConnectionConfig _config;
    private Runnable _onOpenTask;
    private AtomicBoolean _logClosed = new AtomicBoolean(false);
    private LogActor _actor = GenericActor.getInstance(this);

    private ApplicationRegistry _registry;
    private boolean _statisticsEnabled = false;
    private StatisticsCounter _messagesDelivered, _dataDelivered, _messagesReceived, _dataReceived;
    
    public ServerConnection()
    {

    }

    public UUID getId()
    {
        return _config.getId();
    }

    @Override
    protected void invoke(Method method)
    {
        super.invoke(method);
    }

    @Override
    protected void setState(State state)
    {
        super.setState(state);
        
        if (state == State.OPEN)
        {
            if (_onOpenTask != null)
            {
                _onOpenTask.run();    
            }
            _actor.message(ConnectionMessages.OPEN(getClientId(), "0-10", true, true));

            getVirtualHost().getConnectionRegistry().registerConnection(this);
        }

        if (state == State.CLOSE_RCVD || state == State.CLOSED || state == State.CLOSING)
        {
            if(_virtualHost != null)
            {
                _virtualHost.getConnectionRegistry().deregisterConnection(this);
            }
        }

        if (state == State.CLOSED)
        {
            logClosed();
        }
    }

    protected void logClosed()
    {
        if(_logClosed.compareAndSet(false, true))
        {
            CurrentActor.get().message(this, ConnectionMessages.CLOSE());
        }
    }

    @Override
    public ServerConnectionDelegate getConnectionDelegate()
    {
        return (ServerConnectionDelegate) super.getConnectionDelegate();
    }

    public void setConnectionDelegate(ServerConnectionDelegate delegate)
    {
        super.setConnectionDelegate(delegate);
    }

    private VirtualHost _virtualHost;


    public VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    public void setVirtualHost(VirtualHost virtualHost)
    {
        _virtualHost = virtualHost;
        
        initialiseStatistics();
    }

    public void setConnectionConfig(final ConnectionConfig config)
    {
        _config = config;
    }

    public ConnectionConfig getConfig()
    {
        return _config;
    }

    public void onOpen(final Runnable task)
    {
        _onOpenTask = task;
    }

    public void closeSession(AMQSessionModel session, AMQConstant cause, String message) throws AMQException
    {
        ExecutionException ex = new ExecutionException();
        ExecutionErrorCode code = ExecutionErrorCode.INTERNAL_ERROR;
        try
        {
	        code = ExecutionErrorCode.get(cause.getCode());
        }
        catch (IllegalArgumentException iae)
        {
            // Ignore, already set to INTERNAL_ERROR
        }
        ex.setErrorCode(code);
        ex.setDescription(message);
        ((ServerSession)session).invoke(ex);

        ((ServerSession)session).close();
    }
    
    public LogSubject getLogSubject()
    {
        return (LogSubject) this;
    }

    @Override
    public void received(ProtocolEvent event)
    {
        if (event.isConnectionControl())
        {
            CurrentActor.set(_actor);
        }
        else
        {
            ServerSession channel = (ServerSession) getSession(event.getChannel());
            LogActor channelActor = null;

            if (channel != null)
            {
                channelActor = channel.getLogActor();
            }

            CurrentActor.set(channelActor == null ? _actor : channelActor);
        }

        try
        {
            super.received(event);
        }
        finally
        {
            CurrentActor.remove();
        }
    }

    public String toLogString()
    {
        boolean hasVirtualHost = (null != this.getVirtualHost());
        boolean hasPrincipal = (null != getAuthorizationID());

        if (hasPrincipal && hasVirtualHost)
        {
            return "[" +
                    MessageFormat.format(CONNECTION_FORMAT,
                                         getConnectionId(),
                                         getClientId(),
                                         getConfig().getAddress(),
                                         getVirtualHost().getName())
                 + "] ";
        }
        else if (hasPrincipal)
        {
            return "[" +
                    MessageFormat.format(USER_FORMAT,
                                         getConnectionId(),
                                         getClientId(),
                                         getConfig().getAddress())
                 + "] ";

        }
        else
        {
            return "[" +
                    MessageFormat.format(SOCKET_FORMAT,
                                         getConnectionId(),
                                         getConfig().getAddress())
                 + "] ";
        }
    }

    public LogActor getLogActor()
    {
        return _actor;
    }

    @Override
    public void close(AMQConstant cause, String message) throws AMQException
    {
        ConnectionCloseCode replyCode = ConnectionCloseCode.NORMAL;
        try
        {
	        replyCode = ConnectionCloseCode.get(cause.getCode());
        }
        catch (IllegalArgumentException iae)
        {
            // Ignore
        }
        close(replyCode, message);
    }

    @Override
    public List<AMQSessionModel> getSessionModels()
    {
        List<AMQSessionModel> sessions = new ArrayList<AMQSessionModel>();
        for (Session ssn : getChannels())
        {
            sessions.add((AMQSessionModel) ssn);
        }
        return sessions;
    }

    public void registerMessageDelivered(long messageSize)
    {
        if (isStatisticsEnabled())
        {
            _messagesDelivered.registerEvent(1L);
            _dataDelivered.registerEvent(messageSize);
        }
        _virtualHost.registerMessageDelivered(messageSize);
    }

    public void registerMessageReceived(long messageSize, long timestamp)
    {
        if (isStatisticsEnabled())
        {
            _messagesReceived.registerEvent(1L, timestamp);
            _dataReceived.registerEvent(messageSize, timestamp);
        }
        _virtualHost.registerMessageReceived(messageSize, timestamp);
    }
    
    public StatisticsCounter getMessageReceiptStatistics()
    {
        return _messagesReceived;
    }
    
    public StatisticsCounter getDataReceiptStatistics()
    {
        return _dataReceived;
    }
    
    public StatisticsCounter getMessageDeliveryStatistics()
    {
        return _messagesDelivered;
    }
    
    public StatisticsCounter getDataDeliveryStatistics()
    {
        return _dataDelivered;
    }
    
    public void resetStatistics()
    {
        _messagesDelivered.reset();
        _dataDelivered.reset();
        _messagesReceived.reset();
        _dataReceived.reset();
    }

    public void initialiseStatistics()
    {
        setStatisticsEnabled(!StatisticsCounter.DISABLE_STATISTICS &&
                _virtualHost.getApplicationRegistry().getConfiguration().isStatisticsGenerationConnectionsEnabled());
        
        _messagesDelivered = new StatisticsCounter("messages-delivered-" + getConnectionId());
        _dataDelivered = new StatisticsCounter("data-delivered-" + getConnectionId());
        _messagesReceived = new StatisticsCounter("messages-received-" + getConnectionId());
        _dataReceived = new StatisticsCounter("data-received-" + getConnectionId());
    }

    public boolean isStatisticsEnabled()
    {
        return _statisticsEnabled;
    }

    public void setStatisticsEnabled(boolean enabled)
    {
        _statisticsEnabled = enabled;
    }
}
