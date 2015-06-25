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

#include "qpid/legacystore/TxnCtxt.h"

#include <sstream>

#include "qpid/legacystore/jrnl/jexception.h"
#include "qpid/legacystore/StoreException.h"

namespace mrg {
namespace msgstore {

void TxnCtxt::completeTxn(bool commit) {
    sync();
    for (ipqItr i = impactedQueues.begin(); i != impactedQueues.end(); i++) {
        commitTxn(static_cast<JournalImpl*>(*i), commit);
    }
    impactedQueues.clear();
    if (preparedXidStorePtr)
        commitTxn(preparedXidStorePtr, commit);
}

void TxnCtxt::commitTxn(JournalImpl* jc, bool commit) {
    if (jc && loggedtx) { /* if using journal */
        boost::intrusive_ptr<DataTokenImpl> dtokp(new DataTokenImpl);
        dtokp->addRef();
        dtokp->set_external_rid(true);
        dtokp->set_rid(loggedtx->next());
        try {
            if (commit) {
                jc->txn_commit(dtokp.get(), getXid());
                sync();
            } else {
                jc->txn_abort(dtokp.get(), getXid());
            }
        } catch (const journal::jexception& e) {
            THROW_STORE_EXCEPTION(std::string("Error commit") + e.what());
        }
    }
}

// static
qpid::sys::uuid_t TxnCtxt::uuid;

// static
IdSequence TxnCtxt::uuidSeq;

// static
bool TxnCtxt::staticInit = TxnCtxt::setUuid();

// static
bool TxnCtxt::setUuid() {
    qpid::sys::uuid_generate(uuid);
    return true;
}

TxnCtxt::TxnCtxt(IdSequence* _loggedtx) : loggedtx(_loggedtx), dtokp(new DataTokenImpl), preparedXidStorePtr(0), txn(0) {
    if (loggedtx) {
//         // Human-readable tid: 53 bytes
//         // uuit_t is a char[16]
//         tid.reserve(53);
//         u_int64_t* u1 = (u_int64_t*)uuid;
//         u_int64_t* u2 = (u_int64_t*)(uuid + sizeof(u_int64_t));
//         std::stringstream s;
//         s << "tid:" << std::hex << std::setfill('0') << std::setw(16) << uuidSeq.next() << ":" << std::setw(16) << *u1 << std::setw(16) << *u2;
//         tid.assign(s.str());

        // Binary tid: 24 bytes
        tid.reserve(24);
        u_int64_t c = uuidSeq.next();
        tid.append((char*)&c, sizeof(c));
        tid.append((char*)&uuid, sizeof(uuid));
    }
}

TxnCtxt::TxnCtxt(std::string _tid, IdSequence* _loggedtx) : loggedtx(_loggedtx), dtokp(new DataTokenImpl), preparedXidStorePtr(0), tid(_tid), txn(0) {}

TxnCtxt::~TxnCtxt() { abort(); }

void TxnCtxt::sync() {
    if (loggedtx) {
        try {
            for (ipqItr i = impactedQueues.begin(); i != impactedQueues.end(); i++)
                jrnl_flush(static_cast<JournalImpl*>(*i));
            if (preparedXidStorePtr)
                jrnl_flush(preparedXidStorePtr);
            for (ipqItr i = impactedQueues.begin(); i != impactedQueues.end(); i++)
                jrnl_sync(static_cast<JournalImpl*>(*i), &journal::jcntl::_aio_cmpl_timeout);
            if (preparedXidStorePtr)
                jrnl_sync(preparedXidStorePtr, &journal::jcntl::_aio_cmpl_timeout);
        } catch (const journal::jexception& e) {
            THROW_STORE_EXCEPTION(std::string("Error during txn sync: ") + e.what());
        }
    }
}

void TxnCtxt::jrnl_flush(JournalImpl* jc) {
    if (jc && !(jc->is_txn_synced(getXid())))
        jc->flush();
}

void TxnCtxt::jrnl_sync(JournalImpl* jc, timespec* timeout) {
    if (!jc || jc->is_txn_synced(getXid()))
        return;
    while (jc->get_wr_aio_evt_rem()) {
        if (jc->get_wr_events(timeout) == journal::jerrno::AIO_TIMEOUT && timeout)
            THROW_STORE_EXCEPTION(std::string("Error: timeout waiting for TxnCtxt::jrnl_sync()"));
    }
}

void TxnCtxt::begin(DbEnv* env, bool sync) {
    int err;
    try { err = env->txn_begin(0, &txn, 0); }
    catch (const DbException&) { txn = 0; throw; }
    if (err != 0) {
        std::ostringstream oss;
        oss << "Error: Env::txn_begin() returned error code: " << err;
        THROW_STORE_EXCEPTION(oss.str());
    }
    if (sync)
        globalHolder = AutoScopedLock(new qpid::sys::Mutex::ScopedLock(globalSerialiser));
}

void TxnCtxt::commit() {
    if (txn) {
        txn->commit(0);
        txn = 0;
        globalHolder.reset();
    }
}

void TxnCtxt::abort(){
    if (txn) {
        txn->abort();
        txn = 0;
        globalHolder.reset();
    }
}

DbTxn* TxnCtxt::get() { return txn; }

bool TxnCtxt::isTPC() { return false; }

const std::string& TxnCtxt::getXid() { return tid; }

void TxnCtxt::addXidRecord(qpid::broker::ExternalQueueStore* queue) { impactedQueues.insert(queue); }

void TxnCtxt::complete(bool commit) { completeTxn(commit); }

bool TxnCtxt::impactedQueuesEmpty() { return impactedQueues.empty(); }

DataTokenImpl* TxnCtxt::getDtok() { return dtokp.get(); }

void TxnCtxt::incrDtokRef() { dtokp->addRef(); }

void TxnCtxt::recoverDtok(const u_int64_t rid, const std::string xid) {
    dtokp->set_rid(rid);
    dtokp->set_wstate(DataTokenImpl::ENQ);
    dtokp->set_xid(xid);
    dtokp->set_external_rid(true);
}

TPCTxnCtxt::TPCTxnCtxt(const std::string& _xid, IdSequence* _loggedtx) : TxnCtxt(_loggedtx), xid(_xid) {}

}}
