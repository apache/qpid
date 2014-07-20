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

package org.apache.qpid.server.txn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import org.apache.log4j.Logger;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.Transaction;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.transport.Xid;

public class DtxBranch
{
    private static final Logger _logger = Logger.getLogger(DtxBranch.class);

    private final Xid _xid;
    private final List<ServerTransaction.Action> _postTransactionActions = new ArrayList<ServerTransaction.Action>();
    private       State                          _state = State.ACTIVE;
    private long _timeout;
    private Map<AMQSessionModel, State> _associatedSessions = new HashMap<AMQSessionModel, State>();
    private final List<Record> _enqueueRecords = new ArrayList<Record>();
    private final List<Record> _dequeueRecords = new ArrayList<Record>();

    private Transaction _transaction;
    private long _expiration;
    private VirtualHostImpl _vhost;
    private ScheduledFuture<?> _timeoutFuture;
    private MessageStore _store;


    public enum State
    {
        ACTIVE,
        PREPARED,
        TIMEDOUT,
        SUSPENDED,
        FORGOTTEN,
        HEUR_COM,
        HEUR_RB,
        ROLLBACK_ONLY
    }

    public DtxBranch(Xid xid, MessageStore store, VirtualHostImpl vhost)
    {
        _xid = xid;
        _store = store;
        _vhost = vhost;
    }

    public Xid getXid()
    {
        return _xid;
    }

    public State getState()
    {
        return _state;
    }

    public void setState(State state)
    {
        _state = state;
    }

    public long getTimeout()
    {
        return _timeout;
    }

    public void setTimeout(long timeout)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("Setting timeout to " + timeout + "s for DtxBranch " + _xid);
        }

        if(_timeoutFuture != null)
        {
            if(_logger.isDebugEnabled())
            {
                _logger.debug("Attempting to cancel previous timeout task future for DtxBranch " + _xid);
            }

            boolean succeeded = _timeoutFuture.cancel(false);

            if(_logger.isDebugEnabled())
            {
                _logger.debug("Cancelling previous timeout task " + (succeeded ? "succeeded" : "failed")
                              + " for DtxBranch " + _xid);
            }
        }

        _timeout = timeout;
        _expiration = timeout == 0 ? 0 : System.currentTimeMillis() + (1000 * timeout);

        if(_timeout == 0)
        {
            _timeoutFuture = null;
        }
        else
        {
            long delay = 1000*_timeout;

            if(_logger.isDebugEnabled())
            {
                _logger.debug("Scheduling timeout and rollback after " + delay/1000 +
                              "s for DtxBranch " + _xid);
            }

            _timeoutFuture = _vhost.scheduleTask(delay, new Runnable()
            {
                public void run()
                {
                    if(_logger.isDebugEnabled())
                    {
                        _logger.debug("Timing out DtxBranch " + _xid);
                    }

                    setState(State.TIMEDOUT);
                    rollback();
                }
            });
        }
    }

    public boolean expired()
    {
        return _timeout != 0 && _expiration < System.currentTimeMillis();
    }

    public synchronized boolean isAssociated(AMQSessionModel session)
    {
        return _associatedSessions.containsKey(session);
    }

    public synchronized boolean hasAssociatedSessions()
    {
        return !_associatedSessions.isEmpty();
    }


    public synchronized boolean hasAssociatedActiveSessions()
    {
        if(hasAssociatedSessions())
        {
            for(State state : _associatedSessions.values())
            {
                if(state != State.SUSPENDED)
                {
                    return true;
                }
            }
        }
        return false;
    }

    public synchronized void clearAssociations()
    {
        _associatedSessions.clear();
    }

    synchronized boolean associateSession(AMQSessionModel associatedSession)
    {
        return _associatedSessions.put(associatedSession, State.ACTIVE) != null;
    }

    synchronized boolean disassociateSession(AMQSessionModel associatedSession)
    {
        return _associatedSessions.remove(associatedSession) != null;
    }

    public synchronized boolean resumeSession(AMQSessionModel session)
    {
        if(_associatedSessions.containsKey(session) && _associatedSessions.get(session) == State.SUSPENDED)
        {
            _associatedSessions.put(session, State.ACTIVE);
            return true;
        }
        return false;
    }

    public synchronized boolean suspendSession(AMQSessionModel session)
    {
        if(_associatedSessions.containsKey(session) && _associatedSessions.get(session) == State.ACTIVE)
        {
            _associatedSessions.put(session, State.SUSPENDED);
            return true;
        }
        return false;
    }

    public void prepare() throws StoreException
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("Performing prepare for DtxBranch " + _xid);
        }

        Transaction txn = _store.newTransaction();
        txn.recordXid(_xid.getFormat(),
                      _xid.getGlobalId(),
                      _xid.getBranchId(),
                      _enqueueRecords.toArray(new Record[_enqueueRecords.size()]),
                      _dequeueRecords.toArray(new Record[_dequeueRecords.size()]));
        txn.commitTran();

        prePrepareTransaction();
    }

    public synchronized void rollback() throws StoreException
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("Performing rollback for DtxBranch " + _xid);
        }

        if(_timeoutFuture != null)
        {
            if(_logger.isDebugEnabled())
            {
                _logger.debug("Attempting to cancel previous timeout task future for DtxBranch " + _xid);
            }

            boolean succeeded = _timeoutFuture.cancel(false);
            _timeoutFuture = null;

            if(_logger.isDebugEnabled())
            {
                _logger.debug("Cancelling previous timeout task " + (succeeded ? "succeeded" : "failed")
                              + " for DtxBranch " + _xid);
            }
        }

        if(_transaction != null)
        {
            // prepare has previously been called

            Transaction txn = _store.newTransaction();
            txn.removeXid(_xid.getFormat(), _xid.getGlobalId(), _xid.getBranchId());
            txn.commitTran();

            _transaction.abortTran();
        }

        for(ServerTransaction.Action action : _postTransactionActions)
        {
            action.onRollback();
        }
        _postTransactionActions.clear();
    }

    public void commit() throws StoreException
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("Performing commit for DtxBranch " + _xid);
        }

        if(_timeoutFuture != null)
        {
            if(_logger.isDebugEnabled())
            {
                _logger.debug("Attempting to cancel previous timeout task future for DtxBranch " + _xid);
            }

            boolean succeeded = _timeoutFuture.cancel(false);
            _timeoutFuture = null;

            if(_logger.isDebugEnabled())
            {
                _logger.debug("Cancelling previous timeout task " + (succeeded ? "succeeded" : "failed")
                              + " for DtxBranch " + _xid);
            }
        }

        if(_transaction == null)
        {
            prePrepareTransaction();
        }
        else
        {
            _transaction.removeXid(_xid.getFormat(), _xid.getGlobalId(), _xid.getBranchId());
        }
        _transaction.commitTran();

        for(ServerTransaction.Action action : _postTransactionActions)
        {
            action.postCommit();
        }
        _postTransactionActions.clear();
    }

    public void prePrepareTransaction() throws StoreException
    {
        _transaction = _store.newTransaction();

        for(Record enqueue : _enqueueRecords)
        {
            if(enqueue.isDurable())
            {
                _transaction.enqueueMessage(enqueue.getResource(), enqueue.getMessage());
            }
        }


        for(Record enqueue : _dequeueRecords)
        {
            if(enqueue.isDurable())
            {
                _transaction.dequeueMessage(enqueue.getResource(), enqueue.getMessage());
            }
        }
    }


    public void addPostTransactionAction(ServerTransaction.Action postTransactionAction)
    {
        _postTransactionActions.add(postTransactionAction);
    }


    public void dequeue(TransactionLogResource resource, EnqueueableMessage message)
    {
        _dequeueRecords.add(new Record(resource, message));
    }


    public void enqueue(TransactionLogResource queue, EnqueueableMessage message)
    {
        _enqueueRecords.add(new Record(queue, message));
    }

    private static final class Record implements Transaction.Record
    {
        private final TransactionLogResource _resource;
        private final EnqueueableMessage _message;

        public Record(TransactionLogResource resource, EnqueueableMessage message)
        {
            _resource = resource;
            _message = message;
        }

        public TransactionLogResource getResource()
        {
            return _resource;
        }

        public EnqueueableMessage getMessage()
        {
            return _message;
        }

        public boolean isDurable()
        {
            return _message.isPersistent() && _resource.isDurable();
        }
    }


    public void close()
    {
        if(_transaction != null)
        {
            _state = null;
            _transaction.abortTran();
        }
    }
}
