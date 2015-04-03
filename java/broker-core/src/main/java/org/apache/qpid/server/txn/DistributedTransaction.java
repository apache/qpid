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

import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.store.MessageEnqueueRecord;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.transport.Xid;

import java.util.Collection;
import java.util.List;

public class DistributedTransaction implements ServerTransaction
{

    private final AutoCommitTransaction _autoCommitTransaction;

    private DtxBranch _branch;
    private AMQSessionModel _session;
    private VirtualHostImpl _vhost;


    public DistributedTransaction(AMQSessionModel session, MessageStore store, VirtualHostImpl vhost)
    {
        _session = session;
        _vhost = vhost;
        _autoCommitTransaction = new AutoCommitTransaction(vhost.getMessageStore());
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

    public void addPostTransactionAction(Action postTransactionAction)
    {
        if(_branch != null)
        {
            _branch.addPostTransactionAction(postTransactionAction);
        }
        else
        {
            _autoCommitTransaction.addPostTransactionAction(postTransactionAction);
        }
    }

    public void dequeue(MessageEnqueueRecord record, Action postTransactionAction)
    {
        if(_branch != null)
        {
            _branch.dequeue(record);
            _branch.addPostTransactionAction(postTransactionAction);
        }
        else
        {
            _autoCommitTransaction.dequeue(record, postTransactionAction);
        }
    }


    public void dequeue(Collection<MessageInstance> messages, Action postTransactionAction)
    {
        if(_branch != null)
        {
            for(MessageInstance entry : messages)
            {
                _branch.dequeue(entry.getEnqueueRecord());
            }
            _branch.addPostTransactionAction(postTransactionAction);
        }
        else
        {
            _autoCommitTransaction.dequeue(messages, postTransactionAction);
        }
    }

    public void enqueue(TransactionLogResource queue, EnqueueableMessage message, final EnqueueAction postTransactionAction)
    {
        if(_branch != null)
        {
            final MessageEnqueueRecord[] enqueueRecords = new MessageEnqueueRecord[1];
                _branch.enqueue(queue, message, new org.apache.qpid.server.util.Action<MessageEnqueueRecord>()
                {
                    @Override
                    public void performAction(final MessageEnqueueRecord record)
                    {
                        enqueueRecords[0] = record;
                    }
                });
            addPostTransactionAction(new Action()
            {
                @Override
                public void postCommit()
                {
                    postTransactionAction.postCommit(enqueueRecords);
                }

                @Override
                public void onRollback()
                {
                    postTransactionAction.onRollback();
                }
            });
        }
        else
        {
            _autoCommitTransaction.enqueue(queue, message, postTransactionAction);
        }
    }

    public void enqueue(List<? extends BaseQueue> queues, EnqueueableMessage message,
                        final EnqueueAction postTransactionAction)
    {
        if(_branch != null)
        {
            final MessageEnqueueRecord[] enqueueRecords = new MessageEnqueueRecord[queues.size()];
            int i = 0;
            for(BaseQueue queue : queues)
            {
                final int pos = i;
                _branch.enqueue(queue, message, new org.apache.qpid.server.util.Action<MessageEnqueueRecord>()
                {
                    @Override
                    public void performAction(final MessageEnqueueRecord record)
                    {
                        enqueueRecords[pos] = record;
                    }
                });
                i++;
            }
            addPostTransactionAction(new Action()
            {
                @Override
                public void postCommit()
                {
                    postTransactionAction.postCommit(enqueueRecords);
                }

                @Override
                public void onRollback()
                {
                    postTransactionAction.onRollback();
                }
            });
        }
        else
        {
            _autoCommitTransaction.enqueue(queues, message, postTransactionAction);
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
