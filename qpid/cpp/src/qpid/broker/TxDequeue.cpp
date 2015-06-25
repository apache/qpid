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
#include "qpid/broker/TxDequeue.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/TransactionObserver.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace broker {

TxDequeue::TxDequeue(QueueCursor m, boost::shared_ptr<Queue> q,
                     qpid::framing::SequenceNumber mId, qpid::framing::SequenceNumber rId)
    : message(m), queue(q), messageId(mId), replicationId(rId), releaseOnAbort(true), redeliveredOnAbort(true) {}

bool TxDequeue::prepare(TransactionContext* ctxt) throw()
{
    try{
        queue->dequeue(ctxt, message);
        return true;
    }catch(const std::exception& e){
        QPID_LOG(error, "Failed to prepare: " << e.what());
        return false;
    }catch(...){
        QPID_LOG(error, "Failed to prepare");
        return false;
    }
}

void TxDequeue::commit() throw()
{
    try {
        queue->dequeueCommitted(message);
    } catch (const std::exception& e) {
        QPID_LOG(error, "Failed to commit: " << e.what());
    } catch(...) {
        QPID_LOG(error, "Failed to commit (unknown error)");
    }
}

void TxDequeue::rollback() throw()
{
    if (releaseOnAbort) queue->release(message, redeliveredOnAbort);
}

void TxDequeue::callObserver(const boost::shared_ptr<TransactionObserver>& observer)
{
    observer->dequeue(queue, messageId, replicationId);
}

}} // namespace qpid::broker
