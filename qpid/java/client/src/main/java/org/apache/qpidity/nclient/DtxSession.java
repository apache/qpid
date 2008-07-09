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
package org.apache.qpidity.nclient;

import org.apache.qpidity.transport.Future;
import org.apache.qpidity.transport.GetTimeoutResult;
import org.apache.qpidity.transport.Option;
import org.apache.qpidity.transport.RecoverResult;
import org.apache.qpidity.transport.XaResult;
import org.apache.qpidity.transport.Xid;

/**
 * The resources for this session are controlled under the scope of a distributed transaction.
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
     * @return Confirms to the client that the transaction branch is started or specify the error condition.
     */
    public Future<XaResult> dtxStart(Xid xid, Option... options);

    /**
     * This method is called when the work done on behalf of a transaction branch finishes or needs to
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
     * @return  Confirms to the client that the transaction branch is ended or specifies the error condition.
     */
    public Future<XaResult> dtxEnd(Xid xid, Option... options);

    /**
     * Commit the work done on behalf of a transaction branch. This method commits the work associated
     * with xid. Any produced messages are made available and any consumed messages are discarded.
     * The only possible option is:
     * <ul>
     * <li> {@link Option#ONE_PHASE}: When set, one-phase commit optimization is used.
     * </ul>
     *
     * @param xid     Specifies the xid of the transaction branch to be committed.
     * @param options Available option is: {@link Option#ONE_PHASE}
     * @return Confirms to the client that the transaction branch is committed or specifies the error condition.
     */
    public Future<XaResult> dtxCommit(Xid xid, Option... options);

    /**
     * This method is called to forget about a heuristically completed transaction branch.
     *
     * @param xid Specifies the xid of the transaction branch to be forgotten.
     */
    public void dtxForget(Xid xid, Option ... options);

    /**
     * This method obtains the current transaction timeout value in seconds. If set-timeout was not
     * used prior to invoking this method, the return value is the default timeout value; otherwise, the
     * value used in the previous set-timeout call is returned.
     *
     * @param xid Specifies the xid of the transaction branch used for getting the timeout.
     * @return The current transaction timeout value in seconds.
     */
    public Future<GetTimeoutResult> dtxGetTimeout(Xid xid, Option ... options);

    /**
     * This method prepares any message produced or consumed on behalf of xid, ready for commitment.
     *
     * @param xid Specifies the xid of the transaction branch to be prepared.
     * @return The status of the prepare operation can be any one of:
     *         xa-ok: Normal execution.
     *         <p/>
     *         xa-rdonly: The transaction branch was read-only and has been committed.
     *         <p/>
     *         xa-rbrollback: The broker marked the transaction branch rollback-only for an unspecified
     *         reason.
     *         <p/>
     *         xa-rbtimeout: The work represented by this transaction branch took too long.
     */
    public Future<XaResult> dtxPrepare(Xid xid, Option ... options);

    /**
     * This method is called to obtain a list of transaction branches that are in a prepared or
     * heuristically completed state.
     * @return a array of xids to be recovered.
     */
    public Future<RecoverResult> dtxRecover(Option ... options);

    /**
     * This method rolls back the work associated with xid. Any produced messages are discarded and
     * any consumed messages are re-queued.
     *
     * @param xid Specifies the xid of the transaction branch to be rolled back.
     * @return Confirms to the client that the transaction branch is rolled back or specifies the error condition.
     */
    public Future<XaResult> dtxRollback(Xid xid, Option ... options);

    /**
     * Sets the specified transaction branch timeout value in seconds.
     *
     * @param xid     Specifies the xid of the transaction branch for setting the timeout.
     * @param timeout The transaction timeout value in seconds.
     */
    public void dtxSetTimeout(Xid xid, long timeout, Option ... options);
}
