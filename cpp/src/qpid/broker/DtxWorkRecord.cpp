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
#include "qpid/broker/DtxWorkRecord.h"
#include "qpid/broker/DtxManager.h"
#include "qpid/broker/DtxTimeout.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/sys/Timer.h"

#include <boost/format.hpp>
#include <boost/mem_fn.hpp>
using boost::mem_fn;
using qpid::sys::Mutex;

using namespace qpid::broker;
using namespace qpid::framing;

DtxWorkRecord::DtxWorkRecord(const std::string& _xid, AsyncTransactionalStore* const _ats) :
    xid(_xid), asyncTxnStore(_ats), completed(false), rolledback(false), prepared(false), expired(false) {}

DtxWorkRecord::~DtxWorkRecord()
{
    if (timeout.get()) {
        timeout->cancel();
    }
}

void DtxWorkRecord::setTimeout(boost::intrusive_ptr<DtxTimeout> t)
{ timeout = t; }

boost::intrusive_ptr<DtxTimeout> DtxWorkRecord::getTimeout()
{ return timeout; }

bool DtxWorkRecord::prepare()
{
    Mutex::ScopedLock locker(lock);
    if (check()) {
//        txn = asyncTxnStore->begin(xid);
//        if (prepare(txn.get())) {
//            asyncTxnStore->prepare(*txn);
//            prepared = true;
//        } else {
//            abort();
//            //TODO: this should probably be flagged as internal error
//        }
        // TODO: kpvdr: Async transaction prepare here
    } else {
        //some part of the work has been marked rollback only
        abort();
    }
    return prepared;
}

bool DtxWorkRecord::prepare(TransactionContext* _txn)
{
    bool succeeded(true);
    for (Work::iterator i = work.begin(); succeeded && i != work.end(); i++) {
        succeeded = (*i)->prepare(_txn);
    }
    return succeeded;
}

bool DtxWorkRecord::commit(bool onePhase) // why is onePhase necessary if prepared already contains the necessary state?
{
    Mutex::ScopedLock locker(lock);
    if (check()) {
        if (prepared) {
            //already prepared i.e. 2pc
            if (onePhase) {
                throw IllegalStateException(QPID_MSG("Branch with xid " << DtxManager::convert(xid) << " has been prepared, one-phase option not valid!"));
            }

//            asyncTxnStore->commit(*txn);
            // TODO: kpvdr: Async transaction commit here
            txn.reset();

            std::for_each(work.begin(), work.end(), mem_fn(&TxBuffer::commit));
            return true;
        } else {
            //1pc commit optimisation, don't need a 2pc transaction context:
            if (!onePhase) {
                throw IllegalStateException(QPID_MSG("Branch with xid " << DtxManager::convert(xid) << " has not been prepared, one-phase option required!"));
            }
//            std::auto_ptr<TransactionContext> localtxn = asyncTxnStore->begin();
//            if (prepare(localtxn.get())) {
//                asyncTxnStore->commit(*localtxn);
//                std::for_each(work.begin(), work.end(), mem_fn(&TxBuffer::commit));
//                return true;
//            } else {
//                asyncTxnStore->abort(*localtxn);
//                abort();
//                //TODO: this should probably be flagged as internal error
//                return false;
//            }
            // TODO: kpvdr: Local transaction async prepare and commit here
            // temp return value:
            return true;
        }
    } else {
        //some part of the work has been marked rollback only
        abort();
        return false;
    }
}

void DtxWorkRecord::rollback()
{
    Mutex::ScopedLock locker(lock);
    check();
    abort();
}

void DtxWorkRecord::add(DtxBuffer::shared_ptr ops)
{
    Mutex::ScopedLock locker(lock);
    if (expired) {
        throw DtxTimeoutException(QPID_MSG("Branch with xid " << DtxManager::convert(xid) << " has timed out."));
    }
    if (completed) {
        throw CommandInvalidException(QPID_MSG("Branch with xid " << DtxManager::convert(xid) << " has been completed!"));
    }
    work.push_back(ops);
}

bool DtxWorkRecord::check()
{
    if (expired) {
        throw DtxTimeoutException();
    }
    if (!completed) {
        //iterate through all DtxBuffers and ensure they are all ended
        for (Work::iterator i = work.begin(); i != work.end(); i++) {
            if (!(*i)->isEnded()) {
                throw IllegalStateException(QPID_MSG("Branch with xid " << DtxManager::convert(xid) << " not completed!"));
            } else if ((*i)->isRollbackOnly()) {
                rolledback = true;
            }
        }
        completed = true;
    }
    return !rolledback;
}

void DtxWorkRecord::abort()
{
    if (txn.get()) {
//        asyncTxnStore->abort(*txn);
        // TODO: kpvdr: Async transaction abore here
        txn.reset();
    }
    std::for_each(work.begin(), work.end(), mem_fn(&TxBuffer::rollback));
}

void DtxWorkRecord::recover(std::auto_ptr<TPCTransactionContext> _txn, DtxBuffer::shared_ptr ops)
{
    add(ops);
    txn = _txn;
    ops->markEnded();
    completed = true;
    prepared = true;
}

void DtxWorkRecord::timedout()
{
    Mutex::ScopedLock locker(lock);
    expired = true;
    rolledback = true;
    if (!completed) {
        for (Work::iterator i = work.begin(); i != work.end(); i++) {
            if (!(*i)->isEnded()) {
                (*i)->timedout();
            }
        }
    }
    abort();
}
