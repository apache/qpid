/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpid.messaging.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.qpid.messaging.Connection;
import org.apache.qpid.messaging.ConnectionException;
import org.apache.qpid.messaging.Message;
import org.apache.qpid.messaging.MessagingException;
import org.apache.qpid.messaging.Receiver;
import org.apache.qpid.messaging.Sender;
import org.apache.qpid.messaging.SessionException;
import org.apache.qpid.messaging.TransportFailureException;
import org.apache.qpid.messaging.internal.ConnectionInternal;
import org.apache.qpid.messaging.internal.ReceiverInternal;
import org.apache.qpid.messaging.internal.SenderInternal;
import org.apache.qpid.messaging.internal.SessionInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractSessionDecorator implements SessionInternal
{
    protected final Logger _logger = LoggerFactory.getLogger(getClass());

    protected ConnectionInternal _conn;
    protected SessionInternal _delegate;
    protected List<ReceiverInternal> _receivers = new ArrayList<ReceiverInternal>();
    protected List<SenderInternal> _senders = new ArrayList<SenderInternal>();
    protected final Object _connectionLock;  // global per connection lock

    public AbstractSessionDecorator(ConnectionInternal conn, SessionInternal delegate)
    {
        _conn = conn;
        _delegate = delegate;
        _connectionLock = conn.getConnectionLock();
    }

    @Override
    public void close() throws MessagingException
    {
        synchronized(_connectionLock)
        {
            Sender[] senders =  new Sender[_senders.size()];
            senders = _senders.toArray(senders);
            for (Sender sender: senders)
            {
                try
                {
                    sender.close();
                }
                catch (Exception e)
                {
                    _logger.warn("Error closing sender", e);
                }
            }
            _senders.clear();

            Receiver[] receivers =  new Receiver[_receivers.size()];
            receivers = _receivers.toArray(receivers);
            for (Receiver receiver: receivers)
            {
                try
                {
                    receiver.close();
                }
                catch (Exception e)
                {
                    _logger.warn("Error closing receiver", e);
                }
            }
            _receivers.clear();
            _delegate.close();
            _conn.unregisterSession(this);
        }
    }
    
    @Override
    public boolean isClosed()
    {
        return _delegate.isClosed();
    }

    @Override
    public void commit() throws MessagingException
    {
        checkPreConditions();
        _delegate.commit();
    }

    @Override
    public void rollback() throws MessagingException
    {
        checkPreConditions();
        _delegate.rollback();
    }

    @Override
    public void acknowledge(boolean sync) throws MessagingException
    {
        checkPreConditions();
        _delegate.acknowledge(sync);
    }

    @Override
    public void acknowledge(Message message, boolean sync)
    throws MessagingException
    {
        checkPreConditions();
        _delegate.acknowledge(message, sync);
    }

    @Override
    public void reject(Message message) throws MessagingException
    {
        checkPreConditions();
        _delegate.reject(message);
    }

    @Override
    public void release(Message message) throws MessagingException
    {
        checkPreConditions();
        _delegate.release(message);        
    }

    @Override
    public void sync(boolean block) throws MessagingException
    {
        checkPreConditions();
        _delegate.sync(block);        
    }

    @Override
    public int getReceivable() throws MessagingException
    {
        checkPreConditions();
        return _delegate.getReceivable();
    }

    @Override
    public int getUnsettledAcks() throws MessagingException
    {
        checkPreConditions();
        return _delegate.getUnsettledAcks();
    }

    @Override
    public Receiver nextReceiver(long timeout) throws MessagingException
    {
        checkPreConditions();
        return _delegate.nextReceiver(timeout);
    }

    @Override
    public Connection getConnection() throws MessagingException
    {
        checkError();
        return _conn;  // always return your peer (not your delegate's peer)
    }

    @Override
    public boolean hasError()
    {
        return _delegate.hasError();
    }

    @Override
    public void checkError() throws MessagingException
    {
        checkPreConditions(); // check if we already have the info.
        // Asking the delegate.
        _delegate.checkError();
    }

    @Override
    public ConnectionInternal getConnectionInternal()
    {
        return _conn;
    }

    @Override
    public String getName()
    {
        return _delegate.getName();
    }

    @Override
    public void unregisterReceiver(ReceiverInternal receiver)
    {
        _receivers.remove(receiver);
        _delegate.unregisterReceiver(receiver);
    }

    @Override
    public void unregisterSender(SenderInternal sender)
    {
        _senders.remove(sender);
        _delegate.unregisterSender(sender);
    }

    protected abstract void checkPreConditions() throws SessionException;
}
