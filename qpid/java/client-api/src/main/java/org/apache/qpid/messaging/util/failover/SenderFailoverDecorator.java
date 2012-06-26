package org.apache.qpid.messaging.util.failover;

import org.apache.qpid.messaging.ConnectionException;
import org.apache.qpid.messaging.Message;
import org.apache.qpid.messaging.MessagingException;
import org.apache.qpid.messaging.ReceiverException;
import org.apache.qpid.messaging.SenderException;
import org.apache.qpid.messaging.Session;
import org.apache.qpid.messaging.SessionException;
import org.apache.qpid.messaging.TransportFailureException;
import org.apache.qpid.messaging.internal.ConnectionEvent;
import org.apache.qpid.messaging.internal.ConnectionEventListener;
import org.apache.qpid.messaging.internal.SenderInternal;
import org.apache.qpid.messaging.internal.SessionInternal;
import org.apache.qpid.messaging.util.AbstractSenderDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Decorator that adds basic housekeeping tasks to a Sender.
 * This class adds,
 * 1. State management.
 * 2. Exception handling.
 * 3. Failover
 *
 */
public class SenderFailoverDecorator extends AbstractSenderDecorator implements ConnectionEventListener
{
    private static Logger _logger = LoggerFactory.getLogger(SenderFailoverDecorator.class);

    public enum SenderState {OPENED, CLOSED, FAILOVER_IN_PROGRESS};

    private SenderState _state = SenderState.OPENED;
    private long _failoverTimeout = Long.getLong("qpid.failover-timeout", 1000);
    private ReceiverException _lastException;
    private long _connSerialNumber = 0;

    public SenderFailoverDecorator(SessionInternal ssn, SenderInternal delegate)
    {
        super(ssn,delegate);
        synchronized(_connectionLock)
        {
            _connSerialNumber = ssn.getConnectionInternal().getSerialNumber();
        }
    }

    @Override
    public void send(Message message, boolean sync) throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            _delegate.send(message, sync);
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            send(message, sync);
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public void close() throws MessagingException
    {
        synchronized (_connectionLock)
        {
            if (_state == SenderState.CLOSED)
            {
                throw new MessagingException("Sender is already closed");
            }
            _state = SenderState.CLOSED;
            super.close();
        }
    }

    @Override
    public void setCapacity(int capacity) throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            _delegate.setCapacity(capacity);
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            setCapacity(capacity);
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public int getCapacity() throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            return _delegate.getCapacity();
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            return getCapacity();
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public int getAvailable() throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            return _delegate.getAvailable();
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            return getAvailable();
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public int getUnsettled() throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            return _delegate.getUnsettled();
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            return getUnsettled();
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public boolean isClosed() throws MessagingException
    {
        return _state == SenderState.CLOSED;
    }

    @Override
    public String getName() throws MessagingException
    {
        checkPreConditions();
        return getName();
    }

    @Override
    public Session getSession() throws MessagingException
    {
        checkPreConditions();
        _ssn.checkError();
        return _ssn;
    }

    @Override
    public void recreate() throws MessagingException
    {
        synchronized(_connectionLock)
        {
            _connSerialNumber = _ssn.getConnectionInternal().getSerialNumber();
            _delegate.recreate();
        }
    }

    @Override
    public void eventOccured(ConnectionEvent event)
    {
        synchronized (_connectionLock)
        {
            switch(event.getType())
            {
            case PRE_FAILOVER:
            case CONNECTION_LOST:
                _state = SenderState.FAILOVER_IN_PROGRESS;
                break;
            case RECONNCTED:
                _state = SenderState.OPENED;
                break;
            case POST_FAILOVER:
                try
                {
                    if (_state != SenderState.OPENED)
                    {
                        close();
                    }
                }
                catch (MessagingException e)
                {
                    _logger.warn("Exception when trying to close the receiver", e);
                }
                _connectionLock.notifyAll();
                break;
            default:
                break; //ignore the rest
            }
        }
    }

    @Override // From  ConnectionEventListener
    public void exception(ConnectionException e)
    {// NOOP
    }

    protected void waitForFailoverToComplete() throws SenderException
    {
        synchronized (_connectionLock)
        {
            try
            {
                _connectionLock.wait(_failoverTimeout);
            }
            catch (InterruptedException e)
            {
                //ignore.
            }
            if (_state == SenderState.CLOSED)
            {
                throw new SenderException("Receiver is closed. Failover was unsuccesfull",_lastException);
            }
        }
    }

    protected void failover(TransportFailureException e, long serialNumber) throws SenderException
    {
        synchronized (_connectionLock)
        {
            if (_connSerialNumber > serialNumber)
            {
                return; // ignore, we already have failed over.
            }
            _state = SenderState.FAILOVER_IN_PROGRESS;
            _ssn.exception(e, serialNumber); // This triggers failover.
            waitForFailoverToComplete();
        }
    }

    protected void checkPreConditions() throws SenderException
    {
        switch (_state)
        {
        case CLOSED:
            throw new SenderException("Sender is closed. You cannot invoke methods on a closed sender",_lastException);
        case FAILOVER_IN_PROGRESS:
            waitForFailoverToComplete();
        }
    }

    /**
     * Session Exceptions will generally invalidate the Session.
     * TODO this needs to be revisited again.
     * A new session will need to be created in that case.
     * @param e
     * @throws MessagingException
     */
    protected SenderException handleSessionException(SessionException e)
    {
        synchronized (_connectionLock)
        {
            // This should close all senders (including this) and receivers.
            _ssn.exception(e);
        }
        return new SenderException("Session has been closed",e);
    }
}
