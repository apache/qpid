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
package org.apache.qpidity.client;

import javax.transaction.xa.Xid;

import org.apache.qpidity.Option;

/**
 * This session�s resources are control under the scope of a distributed transaction.
 */
public interface DtxSession extends Session
{

    /**
     * This method is called when messages should be produced and consumed on behalf a transaction
     * branch identified by xid.
     * possible options are:
     * <ul>
     * <li> {@link Option#JOIN}:  Indicate that the start applies to joining a transaction previously seen.
     * <li> {@link Option#RESUME}: Indicate that the start applies to resuming a suspended transaction branch specified.
     * </ul>
     *
     * @param xid     Specifies the xid of the transaction branch to be started.
     * @param options Possible options are: {@link Option#JOIN} and {@link Option#RESUME}.
     */
    public void dtxDemarcationStart(Xid xid, Option... options);

    /**
     * This method is called when the work done on behalf a transaction branch finishes or needs to
     * be suspended.
     * possible options are:
     * <ul>
     * <li> {@link Option#FAIL}: indicates that this portion of work has failed;
     * otherwise this portion of work has
     * completed successfully.
     * <li> {@link Option#SUSPEND}: Indicates that the transaction branch is
     * temporarily suspended in an incomplete state.
     * </ul>
     *
     * @param xid     Specifies the xid of the transaction branch to be ended.
     * @param options Available options are: {@link Option#FAIL} and {@link Option#SUSPEND}.
     */
    public void dtxDemarcationEnd(Xid xid, Option... options);

    /**
     * Commit the work done on behalf a transaction branch. This method commits the work associated
     * with xid. Any produced messages are made available and any consumed messages are discarded.
     * possible option is:
     * <ul>
     * <li> {@link Option#ONE_PHASE}: When set then one-phase commit optimization is used.
     * </ul>
     *
     * @param xid     Specifies the xid of the transaction branch to be committed.
     * @param options Available option is: {@link Option#ONE_PHASE}
     */
    public void dtxCoordinationCommit(Xid xid, Option... options);

    /**
     * This method is called to forget about a heuristically completed transaction branch.
     *
     * @param xid Specifies the xid of the transaction branch to be forgotten.
     */
    public void dtxCoordinationForget(Xid xid);

    /**
     * This method obtains the current transaction timeout value in seconds. If set-timeout was not
     * used prior to invoking this method, the return value is the default timeout; otherwise, the
     * value used in the previous set-timeout call is returned.
     *
     * @param xid Specifies the xid of the transaction branch for getting the timeout.
     * @return The current transaction timeout value in seconds.
     */
    public long dtxCoordinationGetTimeout(Xid xid);

    /**
     * This method prepares for commitment any message produced or consumed on behalf of xid.
     *
     * @param xid Specifies the xid of the transaction branch that can be prepared.
     * @return The status of the prepare operation: can be one of those:
     *         xa-ok: Normal execution.
     *         <p/>
     *         xa-rdonly: The transaction branch was read-only and has been committed.
     *         <p/>
     *         xa-rbrollback: The broker marked the transaction branch rollback-only for an unspecified
     *         reason.
     *         <p/>
     *         xa-rbtimeout: The work represented by this transaction branch took too long.
     */
    public short dtxCoordinationPrepare(Xid xid);

    /**
     * This method is called to obtain a list of transaction branches that are in a prepared or
     * heuristically completed state.
     *
     * @return a array of xids to be recovered.
     */
    public Xid[] dtxCoordinationRecover();

    /**
     * This method rolls back the work associated with xid. Any produced messages are discarded and
     * any consumed messages are re-enqueued.
     *
     * @param xid Specifies the xid of the transaction branch that can be rolled back.
     */
    public void dtxCoordinationRollback(Xid xid);

    /**
     * Sets the specified transaction branch timeout value in seconds.
     *
     * @param xid     Specifies the xid of the transaction branch for setting the timeout.
     * @param timeout The transaction timeout value in seconds.
     */
    public void dtxCoordinationSetTimeout(Xid xid, long timeout);
}
