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


class StructFactory {

    public static Struct create(int type)
    {
        switch (type)
        {
        case SessionHeader.TYPE:
            return new SessionHeader();
        case SessionCommandFragment.TYPE:
            return new SessionCommandFragment();
        case DeliveryProperties.TYPE:
            return new DeliveryProperties();
        case FragmentProperties.TYPE:
            return new FragmentProperties();
        case ReplyTo.TYPE:
            return new ReplyTo();
        case MessageProperties.TYPE:
            return new MessageProperties();
        case XaResult.TYPE:
            return new XaResult();
        case Xid.TYPE:
            return new Xid();
        case Acquired.TYPE:
            return new Acquired();
        case MessageResumeResult.TYPE:
            return new MessageResumeResult();
        case GetTimeoutResult.TYPE:
            return new GetTimeoutResult();
        case RecoverResult.TYPE:
            return new RecoverResult();
        case ExchangeQueryResult.TYPE:
            return new ExchangeQueryResult();
        case ExchangeBoundResult.TYPE:
            return new ExchangeBoundResult();
        case QueueQueryResult.TYPE:
            return new QueueQueryResult();
        default:
            throw new IllegalArgumentException("type: " + type);
        }
    }

    public static Struct createInstruction(int type)
    {
        switch (type)
        {
        case ConnectionStart.TYPE:
            return new ConnectionStart();
        case ConnectionStartOk.TYPE:
            return new ConnectionStartOk();
        case ConnectionSecure.TYPE:
            return new ConnectionSecure();
        case ConnectionSecureOk.TYPE:
            return new ConnectionSecureOk();
        case ConnectionTune.TYPE:
            return new ConnectionTune();
        case ConnectionTuneOk.TYPE:
            return new ConnectionTuneOk();
        case ConnectionOpen.TYPE:
            return new ConnectionOpen();
        case ConnectionOpenOk.TYPE:
            return new ConnectionOpenOk();
        case ConnectionRedirect.TYPE:
            return new ConnectionRedirect();
        case ConnectionHeartbeat.TYPE:
            return new ConnectionHeartbeat();
        case ConnectionClose.TYPE:
            return new ConnectionClose();
        case ConnectionCloseOk.TYPE:
            return new ConnectionCloseOk();
        case SessionAttach.TYPE:
            return new SessionAttach();
        case SessionAttached.TYPE:
            return new SessionAttached();
        case SessionDetach.TYPE:
            return new SessionDetach();
        case SessionDetached.TYPE:
            return new SessionDetached();
        case SessionRequestTimeout.TYPE:
            return new SessionRequestTimeout();
        case SessionTimeout.TYPE:
            return new SessionTimeout();
        case SessionCommandPoint.TYPE:
            return new SessionCommandPoint();
        case SessionExpected.TYPE:
            return new SessionExpected();
        case SessionConfirmed.TYPE:
            return new SessionConfirmed();
        case SessionCompleted.TYPE:
            return new SessionCompleted();
        case SessionKnownCompleted.TYPE:
            return new SessionKnownCompleted();
        case SessionFlush.TYPE:
            return new SessionFlush();
        case SessionGap.TYPE:
            return new SessionGap();
        case ExecutionSync.TYPE:
            return new ExecutionSync();
        case ExecutionResult.TYPE:
            return new ExecutionResult();
        case ExecutionException.TYPE:
            return new ExecutionException();
        case MessageTransfer.TYPE:
            return new MessageTransfer();
        case MessageAccept.TYPE:
            return new MessageAccept();
        case MessageReject.TYPE:
            return new MessageReject();
        case MessageRelease.TYPE:
            return new MessageRelease();
        case MessageAcquire.TYPE:
            return new MessageAcquire();
        case MessageResume.TYPE:
            return new MessageResume();
        case MessageSubscribe.TYPE:
            return new MessageSubscribe();
        case MessageCancel.TYPE:
            return new MessageCancel();
        case MessageSetFlowMode.TYPE:
            return new MessageSetFlowMode();
        case MessageFlow.TYPE:
            return new MessageFlow();
        case MessageFlush.TYPE:
            return new MessageFlush();
        case MessageStop.TYPE:
            return new MessageStop();
        case TxSelect.TYPE:
            return new TxSelect();
        case TxCommit.TYPE:
            return new TxCommit();
        case TxRollback.TYPE:
            return new TxRollback();
        case DtxSelect.TYPE:
            return new DtxSelect();
        case DtxStart.TYPE:
            return new DtxStart();
        case DtxEnd.TYPE:
            return new DtxEnd();
        case DtxCommit.TYPE:
            return new DtxCommit();
        case DtxForget.TYPE:
            return new DtxForget();
        case DtxGetTimeout.TYPE:
            return new DtxGetTimeout();
        case DtxPrepare.TYPE:
            return new DtxPrepare();
        case DtxRecover.TYPE:
            return new DtxRecover();
        case DtxRollback.TYPE:
            return new DtxRollback();
        case DtxSetTimeout.TYPE:
            return new DtxSetTimeout();
        case ExchangeDeclare.TYPE:
            return new ExchangeDeclare();
        case ExchangeDelete.TYPE:
            return new ExchangeDelete();
        case ExchangeQuery.TYPE:
            return new ExchangeQuery();
        case ExchangeBind.TYPE:
            return new ExchangeBind();
        case ExchangeUnbind.TYPE:
            return new ExchangeUnbind();
        case ExchangeBound.TYPE:
            return new ExchangeBound();
        case QueueDeclare.TYPE:
            return new QueueDeclare();
        case QueueDelete.TYPE:
            return new QueueDelete();
        case QueuePurge.TYPE:
            return new QueuePurge();
        case QueueQuery.TYPE:
            return new QueueQuery();
        default:
            throw new IllegalArgumentException("type: " + type);
        }
    }

}
