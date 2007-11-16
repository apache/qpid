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
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.store.StoreContext;

/** Holds a list of TxnOp instance representing transactional operations. */
public class TxnBuffer
{
    private final List<TxnOp> _ops = new ArrayList<TxnOp>();
    private static final Logger _log = Logger.getLogger(TxnBuffer.class);

    public TxnBuffer()
    {
    }

    public void commit(StoreContext context) throws AMQException
    {
        if (_log.isDebugEnabled())
        {
            _log.debug("Committing " + _ops.size() + " ops to commit.:" + _ops);
        }

        if (prepare(context))
        {
            for (TxnOp op : _ops)
            {
                op.commit(context);
            }
        }
        _ops.clear();
    }

    private boolean prepare(StoreContext context) throws AMQException
    {
        for (int i = 0; i < _ops.size(); i++)
        {
            TxnOp op = _ops.get(i);
            try
            {
                op.prepare(context);
            }
            catch (AMQException e)
            {
                undoPrepare(i);
                throw e;
            }
            catch (RuntimeException e)
            {
                undoPrepare(i);
                throw e;
            }
        }
        return true;
    }

    private void undoPrepare(int lastPrepared)
    {
        //compensate previously prepared ops
        for (int j = 0; j < lastPrepared; j++)
        {
            _ops.get(j).undoPrepare();
        }
    }

	
	
    public void rollback(StoreContext context) throws AMQException
    {
        for (TxnOp op : _ops)
        {
            op.rollback(context);
        }
        _ops.clear();
    }

    public void enlist(TxnOp op)
    {
        _ops.add(op);
    }

    public void cancel(TxnOp op)
    {
        _ops.remove(op);
    }
}
