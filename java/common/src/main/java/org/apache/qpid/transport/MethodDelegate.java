package org.apache.qpid.transport;
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


public abstract class MethodDelegate<C> {

    public abstract void handle(C context, Method method);

    public void connectionStart(C context, ConnectionStart method) {
        handle(context, method);
    }
    public void connectionStartOk(C context, ConnectionStartOk method) {
        handle(context, method);
    }
    public void connectionSecure(C context, ConnectionSecure method) {
        handle(context, method);
    }
    public void connectionSecureOk(C context, ConnectionSecureOk method) {
        handle(context, method);
    }
    public void connectionTune(C context, ConnectionTune method) {
        handle(context, method);
    }
    public void connectionTuneOk(C context, ConnectionTuneOk method) {
        handle(context, method);
    }
    public void connectionOpen(C context, ConnectionOpen method) {
        handle(context, method);
    }
    public void connectionOpenOk(C context, ConnectionOpenOk method) {
        handle(context, method);
    }
    public void connectionRedirect(C context, ConnectionRedirect method) {
        handle(context, method);
    }
    public void connectionHeartbeat(C context, ConnectionHeartbeat method) {
        handle(context, method);
    }
    public void connectionClose(C context, ConnectionClose method) {
        handle(context, method);
    }
    public void connectionCloseOk(C context, ConnectionCloseOk method) {
        handle(context, method);
    }
    public void sessionAttach(C context, SessionAttach method) {
        handle(context, method);
    }
    public void sessionAttached(C context, SessionAttached method) {
        handle(context, method);
    }
    public void sessionDetach(C context, SessionDetach method) {
        handle(context, method);
    }
    public void sessionDetached(C context, SessionDetached method) {
        handle(context, method);
    }
    public void sessionRequestTimeout(C context, SessionRequestTimeout method) {
        handle(context, method);
    }
    public void sessionTimeout(C context, SessionTimeout method) {
        handle(context, method);
    }
    public void sessionCommandPoint(C context, SessionCommandPoint method) {
        handle(context, method);
    }
    public void sessionExpected(C context, SessionExpected method) {
        handle(context, method);
    }
    public void sessionConfirmed(C context, SessionConfirmed method) {
        handle(context, method);
    }
    public void sessionCompleted(C context, SessionCompleted method) {
        handle(context, method);
    }
    public void sessionKnownCompleted(C context, SessionKnownCompleted method) {
        handle(context, method);
    }
    public void sessionFlush(C context, SessionFlush method) {
        handle(context, method);
    }
    public void sessionGap(C context, SessionGap method) {
        handle(context, method);
    }
    public void executionSync(C context, ExecutionSync method) {
        handle(context, method);
    }
    public void executionResult(C context, ExecutionResult method) {
        handle(context, method);
    }
    public void executionException(C context, ExecutionException method) {
        handle(context, method);
    }
    public void messageTransfer(C context, MessageTransfer method) {
        handle(context, method);
    }
    public void messageAccept(C context, MessageAccept method) {
        handle(context, method);
    }
    public void messageReject(C context, MessageReject method) {
        handle(context, method);
    }
    public void messageRelease(C context, MessageRelease method) {
        handle(context, method);
    }
    public void messageAcquire(C context, MessageAcquire method) {
        handle(context, method);
    }
    public void messageResume(C context, MessageResume method) {
        handle(context, method);
    }
    public void messageSubscribe(C context, MessageSubscribe method) {
        handle(context, method);
    }
    public void messageCancel(C context, MessageCancel method) {
        handle(context, method);
    }
    public void messageSetFlowMode(C context, MessageSetFlowMode method) {
        handle(context, method);
    }
    public void messageFlow(C context, MessageFlow method) {
        handle(context, method);
    }
    public void messageFlush(C context, MessageFlush method) {
        handle(context, method);
    }
    public void messageStop(C context, MessageStop method) {
        handle(context, method);
    }
    public void txSelect(C context, TxSelect method) {
        handle(context, method);
    }
    public void txCommit(C context, TxCommit method) {
        handle(context, method);
    }
    public void txRollback(C context, TxRollback method) {
        handle(context, method);
    }
    public void dtxSelect(C context, DtxSelect method) {
        handle(context, method);
    }
    public void dtxStart(C context, DtxStart method) {
        handle(context, method);
    }
    public void dtxEnd(C context, DtxEnd method) {
        handle(context, method);
    }
    public void dtxCommit(C context, DtxCommit method) {
        handle(context, method);
    }
    public void dtxForget(C context, DtxForget method) {
        handle(context, method);
    }
    public void dtxGetTimeout(C context, DtxGetTimeout method) {
        handle(context, method);
    }
    public void dtxPrepare(C context, DtxPrepare method) {
        handle(context, method);
    }
    public void dtxRecover(C context, DtxRecover method) {
        handle(context, method);
    }
    public void dtxRollback(C context, DtxRollback method) {
        handle(context, method);
    }
    public void dtxSetTimeout(C context, DtxSetTimeout method) {
        handle(context, method);
    }
    public void exchangeDeclare(C context, ExchangeDeclare method) {
        handle(context, method);
    }
    public void exchangeDelete(C context, ExchangeDelete method) {
        handle(context, method);
    }
    public void exchangeQuery(C context, ExchangeQuery method) {
        handle(context, method);
    }
    public void exchangeBind(C context, ExchangeBind method) {
        handle(context, method);
    }
    public void exchangeUnbind(C context, ExchangeUnbind method) {
        handle(context, method);
    }
    public void exchangeBound(C context, ExchangeBound method) {
        handle(context, method);
    }
    public void queueDeclare(C context, QueueDeclare method) {
        handle(context, method);
    }
    public void queueDelete(C context, QueueDelete method) {
        handle(context, method);
    }
    public void queuePurge(C context, QueuePurge method) {
        handle(context, method);
    }
    public void queueQuery(C context, QueueQuery method) {
        handle(context, method);
    }
}
