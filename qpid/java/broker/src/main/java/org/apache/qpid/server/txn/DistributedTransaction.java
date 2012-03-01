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

import org.apache.qpid.server.message.EnqueableMessage;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.transport.Xid;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DistributedTransaction implements ServerTransaction
{

    private final AutoCommitTransaction _autoCommitTransaction;

    private volatile MessageStore.Transaction _transaction;

    private long _txnStartTime = 0L;

    private DtxBranch _branch;
    private AMQSessionModel _session;
    private VirtualHost _vhost;


    public DistributedTransaction(AMQSessionModel session, MessageStore store, VirtualHost vhost)
    {
        _session = session;
        _vhost = vhost;
        _autoCommitTransaction = new AutoCommitTransaction(vhost.getMessageStore());
    }

    public long getTransactionStartTime()
    {
        return _txnStartTime;
    }

    public void addPostTransactionAction(Action postTransactionAction)
    {
        if(_branch != null)
        {
            _branch.addPostTransactionAcion(postTransactionAction);
        }
        else
        {
            _autoCommitTransaction.addPostTransactionAction(postTransactionAction);
        }
    }

    public void dequeue(BaseQueue queue, EnqueableMessage message, Action postTransactionAction)
    {
        if(_branch != null)
        {
            _branch.dequeue(queue, message);
            _branch.addPostTransactionAcion(postTransactionAction);
        }
        else
        {
            _autoCommitTransaction.dequeue(queue, message, postTransactionAction);
        }
    }

    public void dequeue(Collection<QueueEntry> messages, Action postTransactionAction)
    {
        if(_branch != null)
        {
            for(QueueEntry entry : messages)
            {
                _branch.dequeue(entry.getQueue(), entry.getMessage());
            }
            _branch.addPostTransactionAcion(postTransactionAction);
        }
        else
        {
            _autoCommitTransaction.dequeue(messages, postTransactionAction);
        }
    }

    public void enqueue(BaseQueue queue, EnqueableMessage message, Action postTransactionAction)
    {
        if(_branch != null)
        {
            _branch.enqueue(queue, message);
            _branch.addPostTransactionAcion(postTransactionAction);
            enqueue(Collections.singletonList(queue), message, postTransactionAction, System.currentTimeMillis());
        }
        else
        {
            _autoCommitTransaction.enqueue(queue, message, postTransactionAction);
        }
    }

    public void enqueue(List<? extends BaseQueue> queues, EnqueableMessage message,
                        Action postTransactionAction, long currentTime)
    {
        if(_branch != null)
        {
            for(BaseQueue queue : queues)
            {
                _branch.enqueue(queue, message);
            }
            _branch.addPostTransactionAcion(postTransactionAction);
        }
        else
        {
            _autoCommitTransaction.enqueue(queues, message, postTransactionAction, currentTime);
        }
    }

    public void commit()
    {
        throw new IllegalStateException("Cannot call tx.commit() on a distributed transaction");
    }

    public void commit(Runnable immediatePostTransactionAction)
    {
        throw new IllegalStateException("Cannot call tx.commit() on a distributed transaction");
    }

    public void rollback()
    {
        throw new IllegalStateException("Cannot call tx.rollback() on a distributed transaction");
    }

    public boolean isTransactional()
    {
        return _branch != null;
    }
    
    public void start(Xid id, boolean join, boolean resume)
            throws UnknownDtxBranchException, AlreadyKnownDtxException, JoinAndResumeDtxException
    {
        if(join && resume)
        {
            throw new JoinAndResumeDtxException(id);
        }

        DtxBranch branch = _vhost.getDtxRegistry().getBranch(id);

        if(branch == null)
        {
            if(join || resume)
            {
                throw new UnknownDtxBranchException(id);
            }
            branch = new DtxBranch(id,_vhost.getMessageStore(), _vhost);
            if(_vhost.getDtxRegistry().registerBranch(branch))
            {
                _branch = branch;
                branch.associateSession(_session);
            }
            else
            {
                throw new AlreadyKnownDtxException(id);
            }
        }
        else
        {
            if(join)
            {
                branch.associateSession(_session);
            }
            else if(resume)
            {
                branch.resumeSession(_session);
            }
            else
            {
                throw new AlreadyKnownDtxException(id);
            }
            _branch = branch;
        }
    }
    
    public void end(Xid id, boolean fail, boolean suspend)
            throws UnknownDtxBranchException, NotAssociatedDtxException, SuspendAndFailDtxException, TimeoutDtxException
    {
        DtxBranch branch = _vhost.getDtxRegistry().getBranch(id);

        if(suspend && fail)
        {
            branch.disassociateSession(_session);
            _branch = null;
            throw new SuspendAndFailDtxException(id);
        }


        if(branch == null)
        {
            throw new UnknownDtxBranchException(id);
        }
        else
        {
            if(!branch.isAssociated(_session))
            {
                throw new NotAssociatedDtxException(id);
            }
            if(branch.expired() || branch.getState() == DtxBranch.State.TIMEDOUT)
            {
                branch.disassociateSession(_session);
                throw new TimeoutDtxException(id);
            }

            if(suspend)
            {
                branch.suspendSession(_session);
            }
            else
            {
                if(fail)
                {
                    branch.setState(DtxBranch.State.ROLLBACK_ONLY);
                }
                branch.disassociateSession(_session);
            }

            _branch = null;

        }
    }

}
