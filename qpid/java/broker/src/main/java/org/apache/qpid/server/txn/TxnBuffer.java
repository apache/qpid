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

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.store.MessageStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds a list of TxnOp instance representing transactional
 * operations. 
 */
public class TxnBuffer
{
    private boolean _containsPersistentChanges = false;
    private final MessageStore _store;
    private final List<TxnOp> _ops = new ArrayList<TxnOp>();
    private static final Logger _log = Logger.getLogger(TxnBuffer.class);

    public TxnBuffer(MessageStore store)
    {
        _store = store;
    }

    public void containsPersistentChanges()
    {
        _containsPersistentChanges = true;
    }

    public void commit() throws AMQException
    {
        if (_containsPersistentChanges)
        {
            _log.debug("Begin Transaction.");
            _store.beginTran();
            if(prepare())
            {
                _log.debug("Transaction Succeeded");
                _store.commitTran();
                for (TxnOp op : _ops)
                {
                    op.commit();
                }
            }
            else
            {
                _log.debug("Transaction Failed");
                _store.abortTran();
            }
        }else{
            if(prepare())
            {
                for (TxnOp op : _ops)
                {
                    op.commit();
                }
            }            
        }
        _ops.clear();
    }

    private boolean prepare() 
    {        
        for (int i = 0; i < _ops.size(); i++)
        {
            TxnOp op = _ops.get(i);
            try
            {
                op.prepare();
            }
            catch(Exception e)
            {
                //compensate previously prepared ops
                for(int j = 0; j < i; j++)
                {
                    _ops.get(j).undoPrepare();
                }    
                return false;
            }
        }
        return true;
    }   

    public void rollback() throws AMQException
    {
        for (TxnOp op : _ops)
        {
            op.rollback();
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
