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

import org.apache.log4j.Logger;
import org.apache.qpid.server.messageStore.MessageStore;
import org.apache.qpid.server.messageStore.JDBCStore;
import org.apache.qpid.server.exception.*;
import org.apache.commons.configuration.Configuration;

import javax.transaction.xa.Xid;
import java.util.HashMap;
import java.util.Set;


/**
 * Created by Arnaud Simon
 * Date: 16-May-2007
 * Time: 14:05:45
 */
public class JDBCTransactionManager implements TransactionManager
{
    //========================================================================
    // Static Constants
    //========================================================================
    // The logger for this class
    private static final Logger _log = Logger.getLogger(JDBCTransactionManager.class);

    private static final String ENVIRONMENT_TX_TIMEOUT = "environment-tx-timeout";

    //========================================================================
    // Instance Fields
    //========================================================================
    // The underlying jdbc message store
    private JDBCStore _messagStore;

    // A map of XID/x
    private HashMap<Xid, Transaction> _xidMap;

    // A map of in-doubt txs
    private HashMap<Xid, Transaction> _indoubtXidMap;

    // A default tx timeout in sec
    private int _defaultTimeout;   // set to 10s if not specified in the config

    //===================================
    //=== Configuartion
    //===================================

    /**
     * Configure this TM with the Message store implementation
     *
     * @param base         The base element identifier from which all configuration items are relative. For example, if the base
     *                     element is "store", the all elements used by concrete classes will be "store.foo" etc.
     * @param config       The apache commons configuration object
     * @param messageStroe the message store associated with the TM
     */
    public void configure(MessageStore messageStroe, String base, Configuration config)
    {
        _messagStore = (JDBCStore) messageStroe;
        if (config != null)
        {
            _defaultTimeout = config.getInt(base + "." + ENVIRONMENT_TX_TIMEOUT, 120);
        } else
        {
            _defaultTimeout = 120;
        }
        _log.info("Using transaction timeout of " + _defaultTimeout + " s");
        // get the list of in-doubt transactions
        try
        {
            _indoubtXidMap = _messagStore.getAllInddoubt();
            _xidMap = _indoubtXidMap;
        } catch (Exception e)
        {
            _log.fatal("Cannot recover in-doubt transactions", e);
        }
    }

    //===================================
    //=== TransactionManager interface
    //===================================

    /**
     * Begin a transaction branch identified by Xid
     *
     * @param xid The xid of the branch to begin
     * @return <ul>
     *         <li>  <code>XAFlag.ok</code>: Normal execution.
     *         </ul>
     * @throws InternalErrorException In case of internal problem
     * @throws InvalidXidException    The Xid is invalid
     */
    public synchronized XAFlag begin(Xid xid)
            throws
            InternalErrorException,
            InvalidXidException
    {
        if (xid == null)
        {
            throw new InvalidXidException(xid, "null xid");
        }
        if (_xidMap.containsKey(xid))
        {
            throw new InvalidXidException(xid, "Xid already exist");
        }
        Transaction tx = new JDBCTransaction();
        tx.setTimeout(_defaultTimeout);
        _xidMap.put(xid, tx);
        return XAFlag.ok;
    }

    /**
     * Prepare the transaction branch identified by Xid
     *
     * @param xid The xid of the branch to prepare
     * @return <ul>
     *         <li> <code>XAFlag.ok</code>: Normal execution.
     *         <li> <code>XAFlag.rdonly</code>: The transaction branch was read-only and has been committed.
     *         <li> <code>XAFlag.rbrollback</code>: The transaction branch was marked rollback-only for an unspecied reason.
     *         <li> <code>XAFlag.rbtimeout</code>: The work represented by this transaction branch took too long.
     *         </ul>
     * @throws InternalErrorException  In case of internal problem
     * @throws CommandInvalidException Prepare has been call in an improper context
     * @throws UnknownXidException     The Xid is unknown
     */
    public synchronized XAFlag prepare(Xid xid)
            throws
            InternalErrorException,
            CommandInvalidException,
            UnknownXidException
    {
        // get the transaction
        JDBCTransaction tx = getTransaction(xid);
        XAFlag result = XAFlag.ok;
        if (tx.isHeurRollback())
        {
            result = XAFlag.rdonly;
        } else if (tx.hasExpired())
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
                try
                {
                    JDBCStore.MyConnection con = _messagStore.getConnection();
                    tx.setConnection(con);
                    // save the xid
                    _messagStore.saveXID(con, tx, xid);
                    for (TransactionRecord record : tx.getrecords())
                    {
                        if (record instanceof JDBCAbstractRecord)
                        {
                            ((JDBCAbstractRecord) record).prepare(_messagStore, xid);
                            _messagStore.saveRecord(con, tx, (JDBCAbstractRecord) record);
                        } else
                        {
                            record.prepare(_messagStore);
                        }
                    }
                    _messagStore.commitConnection(con);
                    tx.setConnection(null);
                } catch (Exception e)
                {
                    _log.error("Cannot prepare tx: " + xid);
                    throw new InternalErrorException("Cannot prepare tx: " + xid);
                }
                tx.prepare();
            }
        }
        return result;
    }

    /**
     * Rollback the transaction branch identified by Xid
     *
     * @param xid The xid of the branch to rollback
     * @return <ul>
     *         <li> <code>XAFlag.ok</code>: Normal execution,
     *         <li> <code>NOT SUPPORTED XAFlag.heurhaz</code>: Due to some failure, the work done on behalf of the specified transaction branch may have been heuristically completed.
     *         <li> <code>NOT SUPPORTED XAFlag.heurcom</code>: Due to a heuristic decision, the work done on behalf of the speci?ed transaction branch was committed.
     *         <li> <code>XAFlag.heurrb</code>: Due to a heuristic decision, the work done on behalf of the speci?ed transaction branch was rolled back.
     *         <li> <code>NOT SUPPORTED XAFlag.heurmix</code>: Due to a heuristic decision, the work done on behalf of the speci?ed transaction branch was partially committed and partially rolled back.
     *         <li> <code>NOT SUPPORTED XAFlag.rbrollback</code>: The broker marked the transaction branch rollback-only for an unspeci?ed reason.
     *         <li> <code>XAFlag.rbtimeout</code>: The work represented by this transaction branch took too long.
     *         </ul>
     * @throws InternalErrorException  In case of internal problem
     * @throws CommandInvalidException Rollback has been call in an improper context
     * @throws UnknownXidException     The Xid is unknown
     */
    public synchronized XAFlag rollback(Xid xid)
            throws
            InternalErrorException,
            CommandInvalidException,
            UnknownXidException
    {
        // get the transaction
        JDBCTransaction tx = getTransaction(xid);
        XAFlag flag = XAFlag.ok;
        if (tx.isHeurRollback())
        {
            flag = XAFlag.heurrb;
        } else
        {
            try
            {
                JDBCStore.MyConnection con = _messagStore.getConnection();
                tx.setConnection(con);
                for (TransactionRecord record : tx.getrecords())
                {
                    if (record instanceof JDBCAbstractRecord)
                    {
                        ((JDBCAbstractRecord) record).rollback(_messagStore, xid);
                    } else
                    {
                        record.rollback(_messagStore);
                    }
                }
                if (tx.isPrepared())
                {
                    _messagStore.deleteRecords(con, tx);
                    _messagStore.deleteXID(con, tx);
                    _messagStore.commitConnection(con);
                }
                _messagStore.commitConnection(con);
                tx.setConnection(null);
            }
            catch (Exception e)
            {
                // this should not happen
                _log.error("Error when rolling back distributed transaction: " + xid);
                throw new InternalErrorException("Error when rolling back distributed transaction: " + xid, e);
            }
            removeTransaction(xid);
        }
        if (tx.hasExpired())
        {
            flag = XAFlag.rbtimeout;
        }
        return flag;
    }

    /**
     * Commit the transaction branch identified by Xid
     *
     * @param xid The xid of the branch to commit
     * @return <ul>
     *         <li> <code>XAFlag.ok</code>: Normal execution,
     *         <li> <code>NOT SUPPORTED XAFlag.heurhaz</code>: Due to some failure, the work done on behalf of the specified transaction branch may have been heuristically completed.
     *         <li> <code>NOT SUPPORTED XAFlag.heurcom</code>: Due to a heuristic decision, the work done on behalf of the specied transaction branch was committed.
     *         <li> <code>XAFlag.heurrb</code>: Due to a heuristic decision, the work done on behalf of the speci?ed transaction branch was rolled back.
     *         <li> <code>XAFlag.heurmix</code>: Due to a heuristic decision, the work done on behalf of the speci?ed transaction branch was partially committed and partially rolled back.
     *         </ul>
     * @throws InternalErrorException  In case of internal problem
     * @throws CommandInvalidException Commit has been call in an improper context
     * @throws UnknownXidException     The Xid is unknown
     * @throws org.apache.qpid.server.exception.NotPreparedException
     *                                 The branch was not prepared prior to commit
     */
    public synchronized XAFlag commit(Xid xid)
            throws
            InternalErrorException,
            CommandInvalidException,
            UnknownXidException,
            NotPreparedException
    {
        // get the transaction
        JDBCTransaction tx = getTransaction(xid);
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
            try
            {
                JDBCStore.MyConnection con = _messagStore.getConnection();
                tx.setConnection(con);
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
                _messagStore.deleteRecords(con, tx);
                _messagStore.deleteXID(con, tx);
                _messagStore.commitConnection(con);
                tx.setConnection(null);
            } catch (Exception e)
            {
                // this should not happen
                _log.error("Error when committing distributed transaction heurrb mode returned: " + xid);
                throw new InternalErrorException("Error when committing distributed transaction: " + xid, e);
            }
            removeTransaction(xid);
        }
        return flag;
    }

    /**
     * One phase commit the transaction branch identified by Xid
     *
     * @param xid The xid of the branch to one phase commit
     * @return <ul>
     *         <li> <code>XAFlag.ok</code>: Normal execution,
     *         <li> <code>NOT SUPPORTED XAFlag.heurhaz</code>: Due to some failure, the work done on behalf of the specified transaction branch may have been heuristically completed.
     *         <li> <code>NOT SUPPORTED XAFlag.heurcom</code>: Due to a heuristic decision, the work done on behalf of the speci?ed transaction branch was committed.
     *         <li> <code>XAFlag.heurrb</code>: Due to a heuristic decision, the work done on behalf of the speci?ed transaction branch was rolled back.
     *         <li> <code>NOT SUPPORTED XAFlag.heurmix</code>: Due to a heuristic decision, the work done on behalf of the speci?ed transaction branch was partially committed and partially rolled back.
     *         <li> <code>NOT SUPPORTED XAFlag.rbrollback</code>: The broker marked the transaction branch rollback-only for an unspeci?ed reason.
     *         <li> <code>XAFlag.rbtimeout</code>: The work represented by this transaction branch took too long.
     *         </ul>
     * @throws InternalErrorException  In case of internal problem
     * @throws CommandInvalidException Commit has been call in an improper context
     * @throws UnknownXidException     The Xid is unknown
     */
    public synchronized XAFlag commit_one_phase(Xid xid)
            throws
            InternalErrorException,
            CommandInvalidException,
            UnknownXidException
    {
        XAFlag flag = XAFlag.ok;
        JDBCTransaction tx = getTransaction(xid);
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
            try
            {
                // we do not need to prepare the tx
                tx.prepare();
                JDBCStore.MyConnection con = _messagStore.getConnection();
                tx.setConnection(con);
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
                _messagStore.commitConnection(con);
                tx.setConnection(null);
            } catch (Exception e)
            {
                e.printStackTrace();
                throw new InternalErrorException("cannot commit transaxtion with xid " + xid + " " + e, e);
            }
            finally
            {
                removeTransaction(xid);
            }
        }
        return flag;
    }

    /**
     * Forget about the transaction branch identified by Xid
     *
     * @param xid The xid of the branch to forget
     * @throws InternalErrorException  In case of internal problem
     * @throws CommandInvalidException Forget has been call in an improper context
     * @throws UnknownXidException     The Xid is unknown
     */
    public void forget(Xid xid)
            throws
            InternalErrorException,
            CommandInvalidException,
            UnknownXidException
    {
        synchronized (xid)
        {
            getTransaction(xid);
            removeTransaction(xid);
        }
    }

    /**
     * Set the transaction branch timeout value in seconds
     *
     * @param xid     The xid of the branch to set timeout
     * @param timeout Timeout value in seconds
     * @throws InternalErrorException In case of internal problem
     * @throws UnknownXidException    The Xid is unknown
     */
    public void setTimeout(Xid xid, long timeout)
            throws
            InternalErrorException,
            UnknownXidException
    {
        JDBCTransaction tx = getTransaction(xid);
        tx.setTimeout(timeout);
    }

    /**
     * Get the transaction branch timeout
     *
     * @param xid The xid of the branch to get the timeout from
     * @return The timeout associated with the branch identified with xid
     * @throws InternalErrorException In case of internal problem
     * @throws UnknownXidException    The Xid is unknown
     */
    public long getTimeout(Xid xid)
            throws
            InternalErrorException,
            UnknownXidException
    {
        JDBCTransaction tx = getTransaction(xid);
        return tx.getTimeout();
    }

    /**
     * Get a set of Xids the RM has prepared or heuristically completed
     *
     * @param startscan Indicates that recovery scan should start
     * @param endscan   Indicates that the recovery scan should end after returning the Xids
     * @return Set of Xids the RM has prepared or  heuristically completed
     * @throws InternalErrorException  In case of internal problem
     * @throws CommandInvalidException Recover has been call in an improper context
     */
    public Set<Xid> recover(boolean startscan, boolean endscan)
            throws
            InternalErrorException,
            CommandInvalidException
    {
        return _indoubtXidMap.keySet();
    }

    /**
     * An error happened (for example the channel has been abruptly closed)
     * with this Xid, TM must make a heuristical decision.
     *
     * @param xid The Xid of the transaction branch to be heuristically completed
     * @throws UnknownXidException    The Xid is unknown
     * @throws InternalErrorException In case of internal problem
     */
    public void HeuristicOutcome(Xid xid)
            throws
            UnknownXidException,
            InternalErrorException
    {
        synchronized (xid)
        {
            JDBCTransaction tx = getTransaction(xid);
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


    public JDBCTransaction getTransaction(Xid xid)
            throws
            UnknownXidException
    {
        Transaction tx = _xidMap.get(xid);
        if (tx == null)
        {
            throw new UnknownXidException(xid);
        }
        return (JDBCTransaction) tx;
    }

    //==========================================================================
    //== Methods for Message Store
    //==========================================================================

    /**
     * Get the default tx timeout in seconds
     *
     * @return the default tx timeout in seconds
     */
    public int getDefaultTimeout()
    {
        return _defaultTimeout;
    }
    //==========================================================================
    //== Private Methods
    //==========================================================================

    private void removeTransaction(Xid xid)
    {
        _xidMap.remove(xid);
        _indoubtXidMap.remove(xid);
    }
}
