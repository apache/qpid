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
import org.apache.qpid.server.messageStore.JDBCStore;

import java.util.List;
import java.util.LinkedList;

/**
 * Created by Arnaud Simon
 * Date: 16-May-2007
 * Time: 14:09:35
 */
public class JDBCTransaction implements Transaction
{
    //========================================================================
    // Static Constants
    //========================================================================
    // The logger for this class
    private static final Logger _log = Logger.getLogger(JDBCTransaction.class);
    public static long _xidId = 0;
    //========================================================================
    // Instance Fields
    //========================================================================
    // the associated connection
    private JDBCStore.MyConnection _connection;
    // Indicates whether this transaction is prepared
    private boolean _prepared = false;
    // Indicates that this transaction has heuristically rolled back
    private boolean _heurRollBack = false;
    // The list of records associated with this tx
    private List<TransactionRecord> _records = new LinkedList<TransactionRecord>();
    // The date when this tx has been created.
    private long _dateCreated;
    // The timeout in seconds
    private long _timeout;
    // this instance xid id used as primary key
    private long _thisxidId;

    //=========================================================
    // Constructors
    //=========================================================

    /**
     * Create a transaction
     *
     */
    public JDBCTransaction()
    {
        _dateCreated = System.currentTimeMillis();
        _thisxidId = _xidId++;
    }

    //=========================================================
    // Getter and Setter methods
    //=========================================================

    /**
     * Notify that this tx has been prepared
     */
    public void prepare()
    {
        _prepared = true;
    }

    /**
     * Specify whether this transaction is prepared
     *
     * @return true if this transaction is prepared, false otherwise
     */
    public boolean isPrepared()
    {
        return _prepared;
    }

     /**
     * Notify that this tx has been heuristically rolled back
     */
    public void heurRollback()
    {
        _heurRollBack = true;
    }

    /**
     * Specify whether this transaction has been heuristically rolled back
     *
     * @return true if this transaction has been heuristically rolled back , false otherwise
     */
    public boolean isHeurRollback()
    {
        return _heurRollBack;
    }

    /**
     * Add an abstract record to this tx.
     *
     * @param record The record to be added
     */
    public void addRecord(TransactionRecord record)
    {
        _records.add(record);
    }

    /**
     * Get the list of records associated with this tx.
     *
     * @return The list of records associated with this tx.
     */
    public List<TransactionRecord> getrecords()
    {
        return _records;
    }

    /**
     * Set this tx timeout
     *
     * @param timeout This tx timeout in seconds
     */
    public void setTimeout(long timeout)
    {
        _timeout = timeout;
    }

    /**
     * Get this tx timeout
     *
     * @return This tx timeout in seconds
     */
    public long getTimeout()
    {
        return _timeout;
    }

    /**
     * Specify whether this tx has expired
     *
     * @return true if this tx has expired, false otherwise
     */
    public boolean hasExpired()
    {
        long currentDate = System.currentTimeMillis();
        boolean result = currentDate - _dateCreated > _timeout * 1000;
        if (_log.isDebugEnabled() && result)
        {
            _log.debug("transaction has expired");
        }
        return result;
    }

    /**
     * Get the JDBC connection
     * @return The JDBC connection
     */
    public JDBCStore.MyConnection getConnection()
    {
        return _connection;
    }

    /**
     * Set the JDBC connection
     *
     * @param connection The new JDBC connection
     */
    public void setConnection(JDBCStore.MyConnection connection)
    {
        _connection = connection;
    }

    /**
     * This tx xid id used as primary key
     *
     * @return this tx xid id 
     */
    public long getXidID()
    {
        return _thisxidId;
    }

}
