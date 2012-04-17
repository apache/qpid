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
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.message.EnqueableMessage;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.Transaction;
import org.apache.qpid.server.virtualhost.VirtualHost;
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
    private VirtualHost _vhost;
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


    public DtxBranch(Xid xid, MessageStore store, VirtualHost vhost)
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
        if(_timeoutFuture != null)
        {
            _timeoutFuture.cancel(false);
        }
        _timeout = timeout;
        _expiration = timeout == 0 ? 0 : System.currentTimeMillis() + timeout;

        if(_timeout == 0)
        {
            _timeoutFuture = null;
        }
        else
        {
            _timeoutFuture = _vhost.scheduleTask(_timeout, new Runnable()
            {
                public void run()
                {
                    setState(State.TIMEDOUT);
                    try
                    {
                        rollback();
                    }
                    catch (AMQStoreException e)
                    {
                        _logger.error("Unexpected error when attempting to rollback XA transaction ("+
                                      _xid + ") due to  timeout", e);
                        throw new RuntimeException(e);
                    }
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

    public void prepare() throws AMQStoreException
    {

        Transaction txn = _store.newTransaction();
        txn.recordXid(_xid.getFormat(),
                      _xid.getGlobalId(),
                      _xid.getBranchId(),
                      _enqueueRecords.toArray(new Record[_enqueueRecords.size()]),
                      _dequeueRecords.toArray(new Record[_dequeueRecords.size()]));
        txn.commitTran();

        prePrepareTransaction();
    }

    public synchronized void rollback() throws AMQStoreException
    {
        if(_timeoutFuture != null)
        {
            _timeoutFuture.cancel(false);
            _timeoutFuture = null;
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

    public void commit() throws AMQStoreException
    {
        if(_timeoutFuture != null)
        {
            _timeoutFuture.cancel(false);
            _timeoutFuture = null;
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

    public void prePrepareTransaction() throws AMQStoreException
    {
        _transaction = _store.newTransaction();

        for(Record enqueue : _enqueueRecords)
        {
            if(enqueue.isDurable())
            {
                _transaction.enqueueMessage(enqueue.getQueue(), enqueue.getMessage());
            }
        }


        for(Record enqueue : _dequeueRecords)
        {
            if(enqueue.isDurable())
            {
                _transaction.dequeueMessage(enqueue.getQueue(), enqueue.getMessage());
            }
        }
    }


    public void addPostTransactionAcion(ServerTransaction.Action postTransactionAction)
    {
        _postTransactionActions.add(postTransactionAction);
    }


    public void dequeue(BaseQueue queue, EnqueableMessage message)
    {
        _dequeueRecords.add(new Record(queue, message));
    }


    public void enqueue(BaseQueue queue, EnqueableMessage message)
    {
        _enqueueRecords.add(new Record(queue, message));
    }

    private static final class Record implements Transaction.Record
    {
        private final BaseQueue _queue;
        private final EnqueableMessage _message;

        public Record(BaseQueue queue, EnqueableMessage message)
        {
            _queue = queue;
            _message = message;
        }

        public BaseQueue getQueue()
        {
            return _queue;
        }

        public EnqueableMessage getMessage()
        {
            return _message;
        }

        public boolean isDurable()
        {
            return _message.isPersistent() && _queue.isDurable();
        }
    }


    public void close()
    {
        if(_transaction != null)
        {
            try
            {
                _state = null;
                _transaction.abortTran();
            }
            catch(AMQStoreException e)
            {
                _logger.error("Error while closing XA branch", e);
            }
        }
    }
}
