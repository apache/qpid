/*
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
 */
package org.apache.qpid.server.store.jdbc;


import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.store.StoreFuture;
import org.apache.qpid.server.store.Transaction;

public abstract class GenericAbstractJDBCMessageStore extends org.apache.qpid.server.store.AbstractJDBCMessageStore
{
    private final AtomicBoolean _messageStoreOpen = new AtomicBoolean(false);
    private final List<RecordedJDBCTransaction> _transactions = new CopyOnWriteArrayList<>();

    private ConfiguredObject<?> _parent;

    @Override
    public final void openMessageStore(ConfiguredObject<?> parent)
    {
        if (_messageStoreOpen.compareAndSet(false, true))
        {
            _parent = parent;

            doOpen(parent);

            createOrOpenMessageStoreDatabase();
            setMaximumMessageId();
        }
    }

    protected abstract void doOpen(final ConfiguredObject<?> parent)
            throws StoreException;

    @Override
    public final void upgradeStoreStructure() throws StoreException
    {
        checkMessageStoreOpen();

        upgrade(_parent);
    }

    @Override
    public final void closeMessageStore()
    {
        if (_messageStoreOpen.compareAndSet(true,  false))
        {
            try
            {
                while(!_transactions.isEmpty())
                {
                    RecordedJDBCTransaction txn = _transactions.get(0);
                    txn.abortTran();
                }
            }
            finally
            {
                doClose();
            }

        }
    }

    protected abstract void doClose();

    protected boolean isMessageStoreOpen()
    {
        return _messageStoreOpen.get();
    }

    @Override
    protected void checkMessageStoreOpen()
    {
        if (!_messageStoreOpen.get())
        {
            throw new IllegalStateException("Message store is not open");
        }
    }

    @Override
    protected void storedSizeChange(int contentSize)
    {
    }

    @Override
    public Transaction newTransaction()
    {
        return new RecordedJDBCTransaction();
    }


    private class RecordedJDBCTransaction extends JDBCTransaction
    {
        private RecordedJDBCTransaction()
        {
            super();
            GenericAbstractJDBCMessageStore.this._transactions.add(this);
        }

        @Override
        public void commitTran()
        {
            try
            {
                super.commitTran();
            }
            finally
            {
                GenericAbstractJDBCMessageStore.this._transactions.remove(this);
            }
        }

        @Override
        public StoreFuture commitTranAsync()
        {
            try
            {
                return super.commitTranAsync();
            }
            finally
            {
                GenericAbstractJDBCMessageStore.this._transactions.remove(this);
            }
        }

        @Override
        public void abortTran()
        {
            try
            {
                super.abortTran();
            }
            finally
            {
                GenericAbstractJDBCMessageStore.this._transactions.remove(this);
            }
        }
    }
}
