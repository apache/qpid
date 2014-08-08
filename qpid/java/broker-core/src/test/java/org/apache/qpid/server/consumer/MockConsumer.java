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

package org.apache.qpid.server.consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.SocketAddress;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.filter.Filterable;
import org.apache.qpid.server.filter.MessageFilter;
import org.apache.qpid.server.filter.SimpleFilterManager;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.ConfiguredObjectFactoryImpl;
import org.apache.qpid.server.model.Consumer;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Session;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.ConsumerListener;
import org.apache.qpid.server.protocol.SessionModelListener;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.StateChangeListener;

public class MockConsumer implements ConsumerTarget
{

    private final List<String> _messageIds;
    private boolean _closed = false;
    private String tag = "mocktag";
    private AMQQueue queue = null;
    private StateChangeListener<ConsumerTarget, State> _listener = null;
    private State _state = State.ACTIVE;
    private ArrayList<MessageInstance> messages = new ArrayList<MessageInstance>();
    private final Lock _stateChangeLock = new ReentrantLock();

    private boolean _isActive = true;

    public MockConsumer()
    {
        _messageIds = null;
    }

    public MockConsumer(List<String> messageIds)
    {
        _messageIds = messageIds;
    }

    public boolean close()
    {
        _closed = true;
        if (_listener != null)
        {
            _listener.stateChanged(this, _state, State.CLOSED);
        }
        _state = State.CLOSED;
        return true;
    }

    public String getName()
    {
        return tag;
    }

    public FilterManager getFilters()
    {
        if(_messageIds != null)
        {
            SimpleFilterManager filters = new SimpleFilterManager();
            filters.add(new MessageFilter()
            {
                @Override
                public boolean matches(final Filterable message)
                {
                    final String messageId = message.getMessageHeader().getMessageId();
                    return _messageIds.contains(messageId);
                }
            });
            return filters;
        }
        else
        {
            return null;
        }
    }

    public long getUnacknowledgedBytes()
    {
        return 0;  // TODO - Implement
    }

    public long getUnacknowledgedMessages()
    {
        return 0;  // TODO - Implement
    }

    public AMQQueue getQueue()
    {
        return queue;
    }

    public AMQSessionModel getSessionModel()
    {
        return new MockSessionModel();
    }

    public boolean isActive()
    {
        return _isActive ;
    }



    public boolean isClosed()
    {
        return _closed;
    }


    public boolean isSuspended()
    {
        return false;
    }

    public void queueDeleted()
    {
    }

    public void restoreCredit(ServerMessage message)
    {
    }

    public void send(MessageInstance entry, boolean batch)
    {
        if (messages.contains(entry))
        {
            entry.setRedelivered();
        }
        messages.add(entry);
    }

    public void flushBatched()
    {

    }

    @Override
    public void acquisitionRemoved(final MessageInstance node)
    {

    }

    public State getState()
    {
        return _state;
    }

    @Override
    public void consumerAdded(final ConsumerImpl sub)
    {
    }

    @Override
    public void consumerRemoved(final ConsumerImpl sub)
    {

    }

    public void setState(State state)
    {
        State oldState = _state;
        _state = state;
        if(_listener != null)
        {
            _listener.stateChanged(this, oldState, state);
        }
    }

    @Override
    public void setStateListener(final StateChangeListener<ConsumerTarget, State> listener)
    {
        _listener = listener;
    }

    public ArrayList<MessageInstance> getMessages()
    {
        return messages;
    }


    public void queueEmpty()
    {
    }

    @Override
    public boolean allocateCredit(final ServerMessage msg)
    {
        return true;
    }

    public void setActive(final boolean isActive)
    {
        _isActive = isActive;
    }

    private static class MockSessionModel implements AMQSessionModel
    {
        private final UUID _id = UUID.randomUUID();
        private Session _modelObject;

        private MockSessionModel()
        {
            _modelObject = mock(Session.class);
            when(_modelObject.getCategoryClass()).thenReturn(Session.class);
            ConfiguredObjectFactory factory = new ConfiguredObjectFactoryImpl(BrokerModel.getInstance());
            when(_modelObject.getObjectFactory()).thenReturn(factory);
            when(_modelObject.getModel()).thenReturn(factory.getModel());
            when(_modelObject.getTaskExecutor()).thenReturn(CurrentThreadTaskExecutor.newStartedInstance());
        }

        @Override
        public UUID getId()
        {
            return _id;
        }

        @Override
        public AMQConnectionModel getConnectionModel()
        {
            return new MockConnectionModel();
        }

        @Override
        public String getClientID()
        {
            return null;
        }

        @Override
        public void close()
        {
        }

        @Override
        public LogSubject getLogSubject()
        {
            return null;
        }

        @Override
        public void checkTransactionStatus(long openWarn, long openClose,
                long idleWarn, long idleClose)
        {
        }

        @Override
        public void block(AMQQueue queue)
        {
        }

        @Override
        public void unblock(AMQQueue queue)
        {
        }

        @Override
        public void block()
        {
        }

        @Override
        public void unblock()
        {
        }

        @Override
        public boolean getBlocking()
        {
            return false;
        }

        @Override
        public Object getConnectionReference()
        {
            return this;
        }

        @Override
        public int getUnacknowledgedMessageCount()
        {
            return 0;
        }

        @Override
        public Long getTxnCount()
        {
            return null;
        }

        @Override
        public Long getTxnStart()
        {
            return null;
        }

        @Override
        public Long getTxnCommits()
        {
            return null;
        }

        @Override
        public Long getTxnRejects()
        {
            return null;
        }

        @Override
        public int getChannelId()
        {
            return 0;
        }

        @Override
        public int getConsumerCount()
        {
            return 0;
        }

        @Override
        public Collection<Consumer<?>> getConsumers()
        {
            return null;
        }

        @Override
        public void addConsumerListener(final ConsumerListener listener)
        {

        }

        @Override
        public void setModelObject(final Session session)
        {
            _modelObject = session;
        }

        @Override
        public Session<?> getModelObject()
        {
            return _modelObject;
        }

        @Override
        public long getTransactionStartTime()
        {
            return 0;
        }

        @Override
        public long getTransactionUpdateTime()
        {
            return 0;
        }

        @Override
        public void removeConsumerListener(final ConsumerListener listener)
        {

        }

        @Override
        public void close(AMQConstant cause, String message)
        {
        }

        @Override
        public void addDeleteTask(final Action task)
        {

        }

        @Override
        public void removeDeleteTask(final Action task)
        {

        }

        @Override
        public int compareTo(final Object o)
        {
            return 0;
        }
    }

    private static class MockConnectionModel implements AMQConnectionModel
    {

        @Override
        public void registerMessageReceived(long messageSize, long timestamp)
        {
        }

        @Override
        public void registerMessageDelivered(long messageSize)
        {
        }

        @Override
        public StatisticsCounter getMessageDeliveryStatistics()
        {
            return null;
        }

        @Override
        public StatisticsCounter getMessageReceiptStatistics()
        {
            return null;
        }

        @Override
        public StatisticsCounter getDataDeliveryStatistics()
        {
            return null;
        }

        @Override
        public StatisticsCounter getDataReceiptStatistics()
        {
            return null;
        }

        @Override
        public void resetStatistics()
        {

        }

        @Override
        public void close(AMQConstant cause, String message)
        {
        }

        @Override
        public void closeSession(AMQSessionModel session, AMQConstant cause,
                String message)
        {
        }

        @Override
        public long getConnectionId()
        {
            return 0;
        }

        @Override
        public List<AMQSessionModel> getSessionModels()
        {
            return null;
        }

        @Override
        public void block()
        {
        }

        @Override
        public void unblock()
        {
        }

        @Override
        public LogSubject getLogSubject()
        {
            return null;
        }

        @Override
        public boolean isSessionNameUnique(byte[] name)
        {
            return false;
        }

        @Override
        public String getRemoteAddressString()
        {
            return "remoteAddress:1234";
        }

        public SocketAddress getRemoteAddress()
        {
            return null;
        }

        @Override
        public String getRemoteProcessPid()
        {
            return null;
        }

        @Override
        public String getClientId()
        {
            return null;
        }

        @Override
        public String getRemoteContainerName()
        {
            return null;
        }

        @Override
        public void addSessionListener(final SessionModelListener listener)
        {

        }

        @Override
        public void removeSessionListener(final SessionModelListener listener)
        {

        }

        @Override
        public String getClientVersion()
        {
            return null;
        }

        @Override
        public String getClientProduct()
        {
            return null;
        }

        @Override
        public Principal getAuthorizedPrincipal()
        {
            return null;
        }

        @Override
        public long getSessionCountLimit()
        {
            return 0;
        }

        @Override
        public long getLastIoTime()
        {
            return 0;
        }

        @Override
        public Port<?> getPort()
        {
            return null;
        }

        @Override
        public Transport getTransport()
        {
            return null;
        }

        @Override
        public void stop()
        {
        }

        @Override
        public boolean isStopped()
        {
            return false;
        }

        @Override
        public String getVirtualHostName()
        {
            return null;
        }

        @Override
        public VirtualHost<?, ?, ?> getVirtualHost()
        {
            return null;
        }

        @Override
        public void addDeleteTask(final Action task)
        {

        }

        @Override
        public void removeDeleteTask(final Action task)
        {

        }
    }
}
