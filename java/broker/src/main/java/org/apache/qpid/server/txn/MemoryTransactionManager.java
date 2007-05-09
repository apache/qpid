/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpid.server.txn;

import org.apache.qpid.server.exception.*;
import org.apache.qpid.server.messageStore.MessageStore;
import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;

import javax.transaction.xa.Xid;
import java.util.Set;
import java.util.HashMap;

/**
 * Created by Arnaud Simon
 * Date: 02-May-2007
 * Time: 08:41:33
 */
public class MemoryTransactionManager implements TransactionManager
{
    //========================================================================
    // Static Constants
    //========================================================================
    // The logger for this class
    private static final Logger _log = Logger.getLogger(MemoryTransactionManager.class);

    private static final String ENVIRONMENT_TX_TIMEOUT = "environment-tx-timeout";

    //========================================================================
    // Instance Fields
    //========================================================================
    // The underlying BDB message store
    private MessageStore _messagStore;
    // A map of XID/BDBtx
    private HashMap<Xid, Transaction> _xidMap;
    // A map of in-doubt txs
    private HashMap<Xid, MemoryTransaction> _indoubtXidMap;

    // A default tx timeout in sec
    private int _defaultTimeout;   // set to 10s if not specified in the config

    //========================================================================
    // Interface TransactionManager
    //========================================================================
    public void configure(MessageStore messageStroe, String base, Configuration config)
    {
        _messagStore = messageStroe;
        if (config != null)
        {
            _defaultTimeout = config.getInt(base + "." + ENVIRONMENT_TX_TIMEOUT, 10);
        } else
        {
            _defaultTimeout = 10;
        }
        _log.info("Using transaction timeout of " + _defaultTimeout + " s");
        _xidMap = new HashMap<Xid, Transaction>();
        _indoubtXidMap = new HashMap<Xid, MemoryTransaction>();
    }

    public XAFlag begin(Xid xid)
            throws
            InternalErrorException,
            InvalidXidException
    {
        synchronized (xid)
        {
            if (xid == null)
            {
                throw new InvalidXidException(xid, "null xid");
            }
            if (_xidMap.containsKey(xid))
            {
                throw new InvalidXidException(xid, "Xid already exist");
            }
            MemoryTransaction tx = new MemoryTransaction();
            tx.setTimeout(_defaultTimeout);
            _xidMap.put(xid, tx);
            return XAFlag.ok;
        }
    }

    public XAFlag prepare(Xid xid)
            throws
            InternalErrorException,
            CommandInvalidException,
            UnknownXidException
    {
        synchronized (xid)
        {
            // get the transaction
            MemoryTransaction tx = (MemoryTransaction) getTransaction(xid);
            XAFlag result = XAFlag.ok;
            if (tx.hasExpired())
            {
                result = XAFlag.rbtimeout;
                // rollback this tx branch
                rollback(xid);
            } else
            {
                if (tx.isPrepared())
                {
                    throw new CommandInvalidException("TransactionImpl is already prepared");
                }
                if (tx.getrecords().size() == 0)
                {
                    // the tx was read only (no work has been done)
                    _xidMap.remove(xid);
                    result = XAFlag.rdonly;
                } else
                {
                    // we need to persist the tx records
                    tx.prepare();
                }
            }
            return result;
        }
    }

    public XAFlag rollback(Xid xid)
            throws
            InternalErrorException,
            CommandInvalidException,
            UnknownXidException
    {
        synchronized (xid)
        {
            // get the transaction
            MemoryTransaction tx = (MemoryTransaction) getTransaction(xid);
            XAFlag flag = XAFlag.ok;
            if (tx.isHeurRollback())
            {
                flag = XAFlag.heurrb;
            } else
            {
                for (TransactionRecord record : tx.getrecords())
                {
                    record.rollback(_messagStore);
                }
                _xidMap.remove(xid);
            }
            if (tx.hasExpired())
            {
                flag = XAFlag.rbtimeout;
            }
            return flag;
        }
    }

    public XAFlag commit(Xid xid)
            throws
            InternalErrorException,
            CommandInvalidException,
            UnknownXidException,
            NotPreparedException
    {
        synchronized (xid)
        {
            // get the transaction
            MemoryTransaction tx = (MemoryTransaction) getTransaction(xid);
            XAFlag flag = XAFlag.ok;
            if (tx.isHeurRollback())
            {
                flag = XAFlag.heurrb;
            } else if (tx.hasExpired())
            {
                flag = XAFlag.rbtimeout;
                // rollback this tx branch
                rollback(xid);
            } else
            {
                if (!tx.isPrepared())
                {
                    throw new NotPreparedException("TransactionImpl is not prepared");
                }
                for (TransactionRecord record : tx.getrecords())
                {
                    try
                    {
                        record.commit(_messagStore, xid);
                    } catch (InvalidXidException e)
                    {
                        throw new UnknownXidException(xid, e);
                    } catch (Exception e)
                    {
                        // this should not happen as the queue and the message must exist
                        _log.error("Error when committing distributed transaction heurmix mode returned: " + xid);
                        flag = XAFlag.heurmix;
                    }
                }
                _xidMap.remove(xid);
            }
            return flag;
        }
    }

    public XAFlag commit_one_phase(Xid xid)
            throws
            InternalErrorException,
            CommandInvalidException,
            UnknownXidException
    {
        synchronized (xid)
        {
            XAFlag flag = XAFlag.ok;
            MemoryTransaction tx = (MemoryTransaction) getTransaction(xid);
            if (tx.isHeurRollback())
            {
                flag = XAFlag.heurrb;
            } else if (tx.hasExpired())
            {
                flag = XAFlag.rbtimeout;
                // rollback this tx branch
                rollback(xid);
            } else
            {
                // we need to prepare the tx
                tx.prepare();
                try
                {
                    for (TransactionRecord record : tx.getrecords())
                    {
                        try
                        {
                            record.commit(_messagStore, xid);
                        } catch (InvalidXidException e)
                        {
                            throw new UnknownXidException(xid, e);
                        } catch (Exception e)
                        {
                            // this should not happen as the queue and the message must exist
                            _log.error("Error when committing transaction heurmix mode returned: " + xid);
                            flag = XAFlag.heurmix;
                        }
                    }
                }               
                finally
                {
                    _xidMap.remove(xid);
                }
            }
            return flag;
        }
    }

    public void forget(Xid xid)
            throws
            InternalErrorException,
            CommandInvalidException,
            UnknownXidException
    {
        synchronized (xid)
        {
            _xidMap.remove(xid);
        }
    }

    public void setTimeout(Xid xid, long timeout)
            throws
            InternalErrorException,
            UnknownXidException
    {
        Transaction tx = getTransaction(xid);
        tx.setTimeout(timeout);
    }

    public long getTimeout(Xid xid)
            throws
            InternalErrorException,
            UnknownXidException
    {
        Transaction tx = getTransaction(xid);
        return tx.getTimeout();
    }

    public Set<Xid> recover(boolean startscan, boolean endscan)
            throws
            InternalErrorException,
            CommandInvalidException
    {
        return _indoubtXidMap.keySet();
    }

    public void HeuristicOutcome(Xid xid)
            throws
            UnknownXidException,
            InternalErrorException
    {
        synchronized (xid)
        {
            MemoryTransaction tx = (MemoryTransaction) getTransaction(xid);
            if (!tx.isPrepared())
            {
                // heuristically rollback this tx
                for (TransactionRecord record : tx.getrecords())
                {
                    record.rollback(_messagStore);
                }
                tx.heurRollback();
            }
            // add this branch in the list of indoubt tx
            _indoubtXidMap.put(xid, tx);
        }
    }

    public Transaction getTransaction(Xid xid)
            throws
            UnknownXidException
    {
        Transaction tx = _xidMap.get(xid);
        if (tx == null)
        {
            throw new UnknownXidException(xid);
        }
        return tx;
    }
}
