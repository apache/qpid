/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.txn;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.store.MessageStore;

import java.util.ArrayList;
import java.util.List;

public class TxnBuffer
{
    private final MessageStore _store;
    private final List<TxnOp> _ops = new ArrayList<TxnOp>();

    public TxnBuffer(MessageStore store)
    {
        _store = store;
    }

    public void commit() throws AMQException
    {
        _store.beginTran();
        boolean failed = true;
        try
        {
            for(TxnOp op : _ops)
            {
                op.commit();
            }
            _ops.clear();
            failed = false;
        }
        finally
        {
            if(failed)
            {
                _store.abortTran();
            }
            else
            {
                _store.commitTran();
            }
        }
    }

    public void rollback() throws AMQException
    {
        for(TxnOp op : _ops)
        {
            op.rollback();
        }
        _ops.clear();
    }

    public void enlist(TxnOp op)
    {
        _ops.add(op);
    }
}
