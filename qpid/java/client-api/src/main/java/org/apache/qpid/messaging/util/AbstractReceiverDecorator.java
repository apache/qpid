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

import org.apache.qpid.messaging.ConnectionException;
import org.apache.qpid.messaging.Message;
import org.apache.qpid.messaging.MessagingException;
import org.apache.qpid.messaging.ReceiverException;
import org.apache.qpid.messaging.Session;
import org.apache.qpid.messaging.SessionException;
import org.apache.qpid.messaging.internal.ReceiverInternal;
import org.apache.qpid.messaging.internal.SessionInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractReceiverDecorator implements ReceiverInternal
{
    private Logger _logger = LoggerFactory.getLogger(getClass());

    public enum ReceiverState {OPENED, CLOSED, ERROR};

    protected ReceiverInternal _delegate;
    protected SessionInternal _ssn;
    protected final Object _connectionLock;  // global per connection lock

    public AbstractReceiverDecorator(SessionInternal ssn, ReceiverInternal delegate)
    {
        _ssn = ssn;
        _delegate = delegate;
        _connectionLock = ssn.getConnectionInternal().getConnectionLock();
    }

    @Override
    public Message get(long timeout) throws MessagingException
    {
        checkPreConditions();
        return _delegate.get(timeout);
    }

    @Override
    public Message fetch(long timeout) throws MessagingException
    {
        checkPreConditions();
        return _delegate.fetch(timeout);
    }

    @Override
    public void setCapacity(int capacity) throws MessagingException
    {
        checkPreConditions();
        _delegate.setCapacity(capacity);
    }

    @Override
    public int getCapacity() throws MessagingException
    {
        checkPreConditions();
        return _delegate.getCapacity();
    }

    @Override
    public int getAvailable() throws MessagingException
    {
        checkPreConditions();
        return _delegate.getAvailable();
    }

    @Override
    public int getUnsettled() throws MessagingException
    {
        checkPreConditions();
        return _delegate.getUnsettled();
    }

    @Override
    public void close() throws MessagingException
    {
        synchronized (_connectionLock)
        {
            _delegate.close();
            _ssn.unregisterReceiver(this);
        }
    }

    @Override
    public boolean isClosed()
    {
        return _delegate.isClosed();
    }

    @Override
    public String getName() throws MessagingException
    {
        checkPreConditions();
        return _delegate.getName();
    }

    @Override
    public Session getSession() throws MessagingException
    {
        checkPreConditions();
        _ssn.checkError();
        return _ssn;
    }

    protected abstract void checkPreConditions() throws ReceiverException;
}
