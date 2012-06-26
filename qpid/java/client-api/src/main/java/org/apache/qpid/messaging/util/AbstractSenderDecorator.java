package org.apache.qpid.messaging.util;

import org.apache.qpid.messaging.ConnectionException;
import org.apache.qpid.messaging.Message;
import org.apache.qpid.messaging.MessagingException;
import org.apache.qpid.messaging.SenderException;
import org.apache.qpid.messaging.Session;
import org.apache.qpid.messaging.SessionException;
import org.apache.qpid.messaging.internal.SenderInternal;
import org.apache.qpid.messaging.internal.SessionInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractSenderDecorator implements SenderInternal
{
    private Logger _logger = LoggerFactory.getLogger(getClass());

    protected SenderInternal _delegate;
    protected SessionInternal _ssn;
    protected final Object _connectionLock;  // global per connection lock

    public AbstractSenderDecorator(SessionInternal ssn, SenderInternal delegate)
    {
        _ssn = ssn;
        _delegate = delegate;
        _connectionLock = ssn.getConnectionInternal().getConnectionLock();
    }

    @Override
    public void send(Message message, boolean sync) throws MessagingException
    {
        checkPreConditions();
        _delegate.send(message, sync);
    }

    @Override
    public void close() throws MessagingException
    {
        synchronized (_connectionLock)
        {
            _delegate.close();
            _ssn.unregisterSender(this);
        }
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

    protected abstract void checkPreConditions() throws SenderException;}
