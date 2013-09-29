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


import java.util.Map;

public abstract class SessionInvoker {

    final void sessionAttach(byte[] name, Option ... _options) {
        invoke(new SessionAttach(name, _options));
    }

    final void sessionAttached(byte[] name, Option ... _options) {
        invoke(new SessionAttached(name, _options));
    }

    final void sessionDetach(byte[] name, Option ... _options) {
        invoke(new SessionDetach(name, _options));
    }

    final void sessionDetached(byte[] name, SessionDetachCode code, Option ... _options) {
        invoke(new SessionDetached(name, code, _options));
    }

    final void sessionRequestTimeout(long timeout, Option ... _options) {
        invoke(new SessionRequestTimeout(timeout, _options));
    }

    final void sessionTimeout(long timeout, Option ... _options) {
        invoke(new SessionTimeout(timeout, _options));
    }

    final void sessionCommandPoint(int commandId, long commandOffset, Option ... _options) {
        invoke(new SessionCommandPoint(commandId, commandOffset, _options));
    }

    final void sessionExpected(RangeSet commands, java.util.List<Object> fragments, Option ... _options) {
        invoke(new SessionExpected(commands, fragments, _options));
    }

    final void sessionConfirmed(RangeSet commands, java.util.List<Object> fragments, Option ... _options) {
        invoke(new SessionConfirmed(commands, fragments, _options));
    }

    final void sessionCompleted(RangeSet commands, Option ... _options) {
        invoke(new SessionCompleted(commands, _options));
    }

    final void sessionKnownCompleted(RangeSet commands, Option ... _options) {
        invoke(new SessionKnownCompleted(commands, _options));
    }

    final void sessionFlush(Option ... _options) {
        invoke(new SessionFlush(_options));
    }

    final void sessionGap(RangeSet commands, Option ... _options) {
        invoke(new SessionGap(commands, _options));
    }

    public final void executionSync(Option ... _options) {
        invoke(new ExecutionSync(_options));
    }

    public final void executionResult(int commandId, Struct value, Option ... _options) {
        invoke(new ExecutionResult(commandId, value, _options));
    }

    public final void executionException(ExecutionErrorCode errorCode, int commandId, short classCode, short commandCode, short fieldIndex, String description, Map<String,Object> errorInfo, Option ... _options) {
        invoke(new ExecutionException(errorCode, commandId, classCode, commandCode, fieldIndex, description, errorInfo, _options));
    }

    public final void messageTransfer(String destination, MessageAcceptMode acceptMode, MessageAcquireMode acquireMode, Header header, java.nio.ByteBuffer body, Option ... _options) {
        invoke(new MessageTransfer(destination, acceptMode, acquireMode, header, body, _options));
    }

    public final void messageAccept(RangeSet transfers, Option ... _options) {
        invoke(new MessageAccept(transfers, _options));
    }

    public final void messageReject(RangeSet transfers, MessageRejectCode code, String text, Option ... _options) {
        invoke(new MessageReject(transfers, code, text, _options));
    }

    public final void messageRelease(RangeSet transfers, Option ... _options) {
        invoke(new MessageRelease(transfers, _options));
    }

    public final Future<Acquired> messageAcquire(RangeSet transfers, Option ... _options) {
        return invoke(new MessageAcquire(transfers, _options), Acquired.class);
    }

    public final Future<MessageResumeResult> messageResume(String destination, String resumeId, Option ... _options) {
        return invoke(new MessageResume(destination, resumeId, _options), MessageResumeResult.class);
    }

    public final void messageSubscribe(String queue, String destination, MessageAcceptMode acceptMode, MessageAcquireMode acquireMode, String resumeId, long resumeTtl, Map<String,Object> arguments, Option ... _options) {
        invoke(new MessageSubscribe(queue, destination, acceptMode, acquireMode, resumeId, resumeTtl, arguments, _options));
    }

    public final void messageCancel(String destination, Option ... _options) {
        invoke(new MessageCancel(destination, _options));
    }

    public final void messageSetFlowMode(String destination, MessageFlowMode flowMode, Option ... _options) {
        invoke(new MessageSetFlowMode(destination, flowMode, _options));
    }

    public final void messageFlow(String destination, MessageCreditUnit unit, long value, Option ... _options) {
        invoke(new MessageFlow(destination, unit, value, _options));
    }

    public final void messageFlush(String destination, Option ... _options) {
        invoke(new MessageFlush(destination, _options));
    }

    public final void messageStop(String destination, Option ... _options) {
        invoke(new MessageStop(destination, _options));
    }

    public final void txSelect(Option ... _options) {
        invoke(new TxSelect(_options));
    }

    public final void txCommit(Option ... _options) {
        invoke(new TxCommit(_options));
    }

    public final void txRollback(Option ... _options) {
        invoke(new TxRollback(_options));
    }

    public final void dtxSelect(Option ... _options) {
        invoke(new DtxSelect(_options));
    }

    public final Future<XaResult> dtxStart(Xid xid, Option ... _options) {
        return invoke(new DtxStart(xid, _options), XaResult.class);
    }

    public final Future<XaResult> dtxEnd(Xid xid, Option ... _options) {
        return invoke(new DtxEnd(xid, _options), XaResult.class);
    }

    public final Future<XaResult> dtxCommit(Xid xid, Option ... _options) {
        return invoke(new DtxCommit(xid, _options), XaResult.class);
    }

    public final void dtxForget(Xid xid, Option ... _options) {
        invoke(new DtxForget(xid, _options));
    }

    public final Future<GetTimeoutResult> dtxGetTimeout(Xid xid, Option ... _options) {
        return invoke(new DtxGetTimeout(xid, _options), GetTimeoutResult.class);
    }

    public final Future<XaResult> dtxPrepare(Xid xid, Option ... _options) {
        return invoke(new DtxPrepare(xid, _options), XaResult.class);
    }

    public final Future<RecoverResult> dtxRecover(Option ... _options) {
        return invoke(new DtxRecover(_options), RecoverResult.class);
    }

    public final Future<XaResult> dtxRollback(Xid xid, Option ... _options) {
        return invoke(new DtxRollback(xid, _options), XaResult.class);
    }

    public final void dtxSetTimeout(Xid xid, long timeout, Option ... _options) {
        invoke(new DtxSetTimeout(xid, timeout, _options));
    }

    public final void exchangeDeclare(String exchange, String type, String alternateExchange, Map<String,Object> arguments, Option ... _options) {
        invoke(new ExchangeDeclare(exchange, type, alternateExchange, arguments, _options));
    }

    public final void exchangeDelete(String exchange, Option ... _options) {
        invoke(new ExchangeDelete(exchange, _options));
    }

    public final Future<ExchangeQueryResult> exchangeQuery(String name, Option ... _options) {
        return invoke(new ExchangeQuery(name, _options), ExchangeQueryResult.class);
    }

    public final void exchangeBind(String queue, String exchange, String bindingKey, Map<String,Object> arguments, Option ... _options) {
        invoke(new ExchangeBind(queue, exchange, bindingKey, arguments, _options));
    }

    public final void exchangeUnbind(String queue, String exchange, String bindingKey, Option ... _options) {
        invoke(new ExchangeUnbind(queue, exchange, bindingKey, _options));
    }

    public final Future<ExchangeBoundResult> exchangeBound(String exchange, String queue, String bindingKey, Map<String,Object> arguments, Option ... _options) {
        return invoke(new ExchangeBound(exchange, queue, bindingKey, arguments, _options), ExchangeBoundResult.class);
    }

    public final void queueDeclare(String queue, String alternateExchange, Map<String,Object> arguments, Option ... _options) {
        invoke(new QueueDeclare(queue, alternateExchange, arguments, _options));
    }

    public final void queueDelete(String queue, Option ... _options) {
        invoke(new QueueDelete(queue, _options));
    }

    public final void queuePurge(String queue, Option ... _options) {
        invoke(new QueuePurge(queue, _options));
    }

    public final Future<QueueQueryResult> queueQuery(String queue, Option ... _options) {
        return invoke(new QueueQuery(queue, _options), QueueQueryResult.class);
    }

    protected abstract void invoke(Method method);

    protected abstract <T> Future<T> invoke(Method method, Class<T> resultClass);

}
