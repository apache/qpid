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
#include "qpid/broker/TxBuffer.h"
#include "qpid/broker/TransactionObserver.h"
#include "qpid/log/Statement.h"
#include "qpid/framing/reply_exceptions.h"

#include <boost/mem_fn.hpp>
#include <boost/bind.hpp>

namespace qpid {
namespace broker{

using boost::mem_fn;
using framing::InternalErrorException;

TxBuffer::TxBuffer() : observer(new NullTransactionObserver) {}

bool TxBuffer::prepare(TransactionContext* const ctxt)
{
    // The observer may call startCompleter to delay completion.
    if (!observer->prepare()) return false;
    for(op_iterator i = ops.begin(); i != ops.end(); i++){
        if(!(*i)->prepare(ctxt)) return false;
    }
    // At this point prepare has succeeded locally but if completion is delayed,
    // then completing threads may call setError to indicate an error.
    return true;
}

void TxBuffer::commit()
{
    observer->commit();
    std::for_each(ops.begin(), ops.end(), mem_fn(&TxOp::commit));
    ops.clear();
}

void TxBuffer::rollback()
{
    observer->rollback();
    std::for_each(ops.begin(), ops.end(), mem_fn(&TxOp::rollback));
    ops.clear();
}

void TxBuffer::enlist(TxOp::shared_ptr op)
{
    op->callObserver(observer);
    ops.push_back(op);
}

void TxBuffer::startCommit(TransactionalStore* const store)
{
    if (!store) throw Exception("Can't commit transaction, no store.");
    txContext.reset(store->begin().release());
    if (!prepare(txContext.get()))
        setError("Transaction prepare failed.");
}

// Called when async completion is complete.
std::string TxBuffer::endCommit(TransactionalStore* const store) {
    std::string e;
    {
        sys::Mutex::ScopedLock l(errorLock);
        e = error;
    }
    if (!e.empty()) {
        store->abort(*txContext);
        rollback();
        throw InternalErrorException(e);
    }
    else {
        store->commit(*txContext);
        commit();
    }
    return std::string();       // There is no result from tx.commit
}

void TxBuffer::setError(const std::string& e) {
    QPID_LOG(error, "Asynchronous transaction error: " << e);
    sys::Mutex::ScopedLock l(errorLock);
    if (!error.empty()) error += " ";
    error += e;
}

}} // namespace qpid::broker
