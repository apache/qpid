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
#include "qpid/broker/DtxManager.h"
#include "qpid/broker/DtxTimeout.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/StructHelper.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Timer.h"
#include "qpid/ptr_map.h"

#include <boost/format.hpp>
#include <boost/bind.hpp>
#include <boost/function.hpp>

#include <iostream>

using boost::intrusive_ptr;
using qpid::sys::Mutex;
using qpid::ptr_map_ptr;
using namespace qpid::broker;
using namespace qpid::framing;

#if DEFINE_UNUSED
namespace {
    typedef boost::function0<void> FireFunction;
    struct DtxCleanup : public qpid::sys::TimerTask
    {
        FireFunction fireFunction;

        DtxCleanup(uint32_t timeout, FireFunction f);
        void fire();
    };

    DtxCleanup::DtxCleanup(uint32_t _timeout, FireFunction f)
    : TimerTask(qpid::sys::Duration(_timeout * qpid::sys::TIME_SEC),"DtxCleanup"), fireFunction(f){}

    void DtxCleanup::fire()
    {
        try {
            fireFunction();
        } catch (qpid::ConnectionException& /*e*/) {
            //assume it was explicitly cleaned up after a call to prepare, commit or rollback
        }
    }

}
#endif

DtxManager::DtxManager(qpid::sys::Timer& t, uint32_t _dtxDefaultTimeout) :
    store(0),
    timer(&t),
    dtxDefaultTimeout(_dtxDefaultTimeout)
{
}

DtxManager::~DtxManager() {}

void DtxManager::start(const std::string& xid, boost::intrusive_ptr<DtxBuffer> ops)
{
    createWork(xid)->add(ops);
}

void DtxManager::join(const std::string& xid, boost::intrusive_ptr<DtxBuffer> ops)
{
    getWork(xid)->add(ops);
}

void DtxManager::recover(const std::string& xid, std::auto_ptr<TPCTransactionContext> txn, boost::intrusive_ptr<DtxBuffer> ops)
{
    createWork(xid)->recover(txn, ops);
}

bool DtxManager::prepare(const std::string& xid)
{
    QPID_LOG(debug, "preparing: " << convert(xid));
    try {
        return getWork(xid)->prepare();
    } catch (DtxTimeoutException& e) {
        remove(xid);
        throw e;
    }
}

bool DtxManager::commit(const std::string& xid, bool onePhase)
{
    QPID_LOG(debug, "committing: " << convert(xid));
    try {
        bool result = getWork(xid)->commit(onePhase);
        remove(xid);
        return result;
    } catch (DtxTimeoutException& e) {
        remove(xid);
        throw e;
    }
}

void DtxManager::rollback(const std::string& xid)
{
    QPID_LOG(debug, "rolling back: " << convert(xid));
    try {
        getWork(xid)->rollback();
        remove(xid);
    } catch (DtxTimeoutException& e) {
        remove(xid);
        throw e;
    }
}

DtxWorkRecord* DtxManager::getWork(const std::string& xid)
{
    Mutex::ScopedLock locker(lock);
    WorkMap::iterator i = work.find(xid);
    if (i == work.end()) {
        throw NotFoundException(QPID_MSG("Unrecognised xid " << convert(xid)));
    }
    return ptr_map_ptr(i);
}

bool DtxManager::exists(const std::string& xid) {
    Mutex::ScopedLock locker(lock);
    return  work.find(xid) != work.end();
}

void DtxManager::remove(const std::string& xid)
{
    Mutex::ScopedLock locker(lock);
    WorkMap::iterator i = work.find(xid);
    if (i == work.end()) {
        throw NotFoundException(QPID_MSG("Unrecognised xid " << convert(xid)));
    } else {
        work.erase(i);
    }
}

DtxWorkRecord* DtxManager::createWork(const std::string& xid)
{
    Mutex::ScopedLock locker(lock);
    WorkMap::iterator i = work.find(xid);
    if (i != work.end()) {
        throw NotAllowedException(QPID_MSG("Xid " << convert(xid) << " is already known (use 'join' to add work to an existing xid)"));
    } else {
        std::string ncxid = xid; // Work around const correctness problems with work.insert
        DtxWorkRecord* dtxWorkRecord = new DtxWorkRecord(xid, store);
        work.insert(ncxid, dtxWorkRecord);
        if (dtxDefaultTimeout>0)
            setTimeout(xid, dtxDefaultTimeout);
        return dtxWorkRecord;
    }
}

void DtxManager::setTimeout(const std::string& xid, uint32_t secs)
{
    DtxWorkRecord* record = getWork(xid);
    intrusive_ptr<DtxTimeout> timeout = record->getTimeout();
    if (timeout.get()) {
        if (timeout->timeout == secs) return;//no need to do anything further if timeout hasn't changed
        timeout->cancel();
    }
    timeout = intrusive_ptr<DtxTimeout>(new DtxTimeout(secs, *this, xid));
    record->setTimeout(timeout);
    timer->add(timeout);
}

uint32_t DtxManager::getTimeout(const std::string& xid)
{
    intrusive_ptr<DtxTimeout> timeout = getWork(xid)->getTimeout();
    return !timeout ? 0 : timeout->timeout;
}

void DtxManager::timedout(const std::string& xid)
{
    Mutex::ScopedLock locker(lock);
    WorkMap::iterator i = work.find(xid);
    if (i == work.end()) {
        QPID_LOG(warning, "Transaction timeout failed: no record for xid");
    } else {
        ptr_map_ptr(i)->timedout();
        //TODO: do we want to have a timed task to cleanup, or can we rely on an explicit completion?
        //timer->add(new DtxCleanup(60*30/*30 mins*/, boost::bind(&DtxManager::remove, this, xid)));
    }
}

void DtxManager::setStore (TransactionalStore* _store)
{
    store = _store;
}

std::string DtxManager::convert(const qpid::framing::Xid& xid)
{
    qpid::framing::StructHelper helper;
    std::string encoded;
    helper.encode(xid, encoded);
    return encoded;
}

qpid::framing::Xid DtxManager::convert(const std::string& xid)
{
    qpid::framing::StructHelper helper;
    qpid::framing::Xid decoded;
    helper.decode(decoded, xid);
    return decoded;
}
