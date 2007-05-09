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
package org.apache.qpid.server.txn;

import org.apache.qpid.server.exception.*;
import org.apache.qpid.server.messageStore.MessageStore;
import org.apache.commons.configuration.Configuration;

import javax.transaction.xa.Xid;
import java.util.Set;

/**
 * Created by Arnaud Simon
 * Date: 29-Mar-2007
 * Time: 13:29:31
 */
public interface TransactionManager
{

    /**
      * Configure this TM with the Message store implementation
      *
      * @param base         The base element identifier from which all configuration items are relative. For example, if the base
      *                     element is "store", the all elements used by concrete classes will be "store.foo" etc.
      * @param config       The apache commons configuration object
      * @param messageStroe the message store associated with the TM
      */
     public void configure(MessageStore messageStroe, String base, Configuration config);

    /**
     * Begin a transaction branch identified by Xid
     *
     * @param xid The xid of the branch to begin
     * @return <ul>
     *         <li>  <code>XAFlag.ok</code>: Normal execution.
     *         <li> <code>XAFlag.rbrollback</code>: The transaction branch was marked rollback-only for an unspecified reason.
     *         </ul>
     * @throws InternalErrorException In case of internal problem
     * @throws InvalidXidException    The Xid is invalid
     */
    public XAFlag begin(Xid xid)
            throws
            InternalErrorException,
            InvalidXidException;

    /**
     * Prepare the transaction branch identified by Xid
     *
     * @param xid The xid of the branch to prepare
     * @return <ul>
     *         <li> <code>XAFlag.ok</code>: Normal execution.
     *         <li> <code>XAFlag.rdonly</code>: The transaction branch was read-only and has been committed.
     *         <li> <code>XAFlag.rbrollback</code>: The transaction branch was marked rollback-only for an unspeci?ed reason.
     *         <li> <code>XAFlag.rbtimeout</code>: The work represented by this transaction branch took too long.
     *         </ul>
     * @throws InternalErrorException  In case of internal problem
     * @throws CommandInvalidException Prepare has been call in an improper context
     * @throws UnknownXidException     The Xid is unknown
     */
    public XAFlag prepare(Xid xid)
            throws
            InternalErrorException,
            CommandInvalidException,
            UnknownXidException;

    /**
     * Rollback the transaction branch identified by Xid
     *
     * @param xid The xid of the branch to rollback
     * @return <ul>
     *         <li> <code>XAFlag.ok</code>: Normal execution,
     *         <li> <code>XAFlag.heurhaz</code>: Due to some failure, the work done on behalf of the specified transaction branch may have been heuristically completed.
     *         <li> <code>XAFlag.heurcom</code>: Due to a heuristic decision, the work done on behalf of the speci?ed transaction branch was committed.
     *         <li> <code>XAFlag.heurrb</code>: Due to a heuristic decision, the work done on behalf of the speci?ed transaction branch was rolled back.
     *         <li> <code>XAFlag.heurmix</code>: Due to a heuristic decision, the work done on behalf of the speci?ed transaction branch was partially committed and partially rolled back.
     *         <li> <code>XAFlag.rbrollback</code>: The broker marked the transaction branch rollback-only for an unspeci?ed reason.
     *         <li> <code>XAFlag.rbtimeout</code>: The work represented by this transaction branch took too long.
     *         </ul>
     * @throws InternalErrorException  In case of internal problem
     * @throws CommandInvalidException Rollback has been call in an improper context
     * @throws UnknownXidException     The Xid is unknown
     */
    public XAFlag rollback(Xid xid)
            throws
            InternalErrorException,
            CommandInvalidException,
            UnknownXidException;

    /**
     * Commit the transaction branch identified by Xid
     *
     * @param xid The xid of the branch to commit
     * @return <ul>
     *         <li> <code>XAFlag.ok</code>: Normal execution,
     *         <li> <code>XAFlag.heurhaz</code>: Due to some failure, the work done on behalf of the specified transaction branch may have been heuristically completed.
     *         <li> <code>XAFlag.heurcom</code>: Due to a heuristic decision, the work done on behalf of the speci?ed transaction branch was committed.
     *         <li> <code>XAFlag.heurrb</code>: Due to a heuristic decision, the work done on behalf of the speci?ed transaction branch was rolled back.
     *         <li> <code>XAFlag.heurmix</code>: Due to a heuristic decision, the work done on behalf of the speci?ed transaction branch was partially committed and partially rolled back.
     *         </ul>
     * @throws InternalErrorException  In case of internal problem
     * @throws CommandInvalidException Commit has been call in an improper context
     * @throws UnknownXidException     The Xid is unknown
     * @throws NotPreparedException    The branch was not prepared prior to commit
     */
    public XAFlag commit(Xid xid)
            throws
            InternalErrorException,
            CommandInvalidException,
            UnknownXidException,
            NotPreparedException;

    /**
     * One phase commit the transaction branch identified by Xid
     *
     * @param xid The xid of the branch to one phase commit
     * @return <ul>
     *         <li> <code>XAFlag.ok</code>: Normal execution,
     *         <li> <code>XAFlag.heurhaz</code>: Due to some failure, the work done on behalf of the specified transaction branch may have been heuristically completed.
     *         <li> <code>XAFlag.heurcom</code>: Due to a heuristic decision, the work done on behalf of the speci?ed transaction branch was committed.
     *         <li> <code>XAFlag.heurrb</code>: Due to a heuristic decision, the work done on behalf of the speci?ed transaction branch was rolled back.
     *         <li> <code>XAFlag.heurmix</code>: Due to a heuristic decision, the work done on behalf of the speci?ed transaction branch was partially committed and partially rolled back.
     *         <li> <code>XAFlag.rbrollback</code>: The broker marked the transaction branch rollback-only for an unspeci?ed reason.
     *         <li> <code>XAFlag.rbtimeout</code>: The work represented by this transaction branch took too long.
     *         </ul>
     * @throws InternalErrorException  In case of internal problem
     * @throws CommandInvalidException Commit has been call in an improper context
     * @throws UnknownXidException     The Xid is unknown
     */
    public XAFlag commit_one_phase(Xid xid)
            throws
            InternalErrorException,
            CommandInvalidException,
            UnknownXidException;

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
            UnknownXidException;

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
            UnknownXidException;

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
            UnknownXidException;

    /**
     * Get a set of Xids the RM has prepared or  heuristically completed
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
            CommandInvalidException;


    /**
     * An error happened (for example the channel has been abruptly closed)
     * with this Xid, TM must make a heuristical decision.
     *
     * @param xid The Xid of the transaction branch to be heuristically completed
     * @throws UnknownXidException    The Xid is unknown
     * @throws InternalErrorException  In case of internal problem     
     */
    public void HeuristicOutcome(Xid xid)
            throws
            UnknownXidException,
            InternalErrorException;

    /**
     * Get the Transaction corresponding to the provided Xid
     * @param xid The Xid of the transaction to ger
     * @return The transaction with the provided Xid
     * @throws UnknownXidException  The Xid is unknown
     */
      public Transaction getTransaction(Xid xid)
            throws
            UnknownXidException;
}
