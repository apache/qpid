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

#ifndef QPID_LINEARSTORE_TXNCTXT_H
#define QPID_LINEARSTORE_TXNCTXT_H

#include <boost/intrusive_ptr.hpp>
#include "qpid/broker/TransactionalStore.h"
#include "qpid/linearstore/IdSequence.h"
#include "qpid/sys/uuid.h"

class DbEnv;
class DbTxn;

namespace qpid {
namespace broker {
    class ExternalQueueStore;
}
namespace linearstore{
    class DataTokenImpl;
    class JournalImpl;

class TxnCtxt : public qpid::broker::TransactionContext
{
  protected:
    static qpid::sys::Mutex globalSerialiser;

    static sys::uuid_t uuid;
    static IdSequence uuidSeq;
    static bool staticInit;
    static bool setUuid();

    typedef std::set<qpid::broker::ExternalQueueStore*> ipqdef;
    typedef ipqdef::iterator ipqItr;
    typedef std::auto_ptr<qpid::sys::Mutex::ScopedLock> AutoScopedLock;

    ipqdef impactedQueues; // list of Queues used in the txn
    IdSequence* loggedtx;
    boost::intrusive_ptr<DataTokenImpl> dtokp;
    AutoScopedLock globalHolder;
    JournalImpl* preparedXidStorePtr;

    /**
     * local txn id, if non XA.
     */
    std::string tid;
    DbTxn* txn;

    virtual void completeTxn(bool commit);
    void commitTxn(JournalImpl* jc, bool commit);
    void jrnl_flush(JournalImpl* jc);
    void jrnl_sync(JournalImpl* jc, timespec* timeout);

  public:
    TxnCtxt(IdSequence* _loggedtx=NULL);
    TxnCtxt(std::string _tid, IdSequence* _loggedtx);
    virtual ~TxnCtxt();

    /**
     * Call to make sure all the data for this txn is written to safe store
     *
     *@return if the data successfully synced.
     */
    void sync();
    void begin(DbEnv* env, bool sync = false);
    void commit();
    void abort();
    DbTxn* get();
    virtual bool isTPC();
    virtual const std::string& getXid();

    void addXidRecord(qpid::broker::ExternalQueueStore* queue);
    inline void prepare(JournalImpl* _preparedXidStorePtr) { preparedXidStorePtr = _preparedXidStorePtr; }
    void complete(bool commit);
    bool impactedQueuesEmpty();
    DataTokenImpl* getDtok();
    void incrDtokRef();
    void recoverDtok(const uint64_t rid, const std::string xid);
};


class TPCTxnCtxt : public TxnCtxt, public qpid::broker::TPCTransactionContext
{
  protected:
    const std::string xid;

  public:
    TPCTxnCtxt(const std::string& _xid, IdSequence* _loggedtx);
    inline virtual bool isTPC() { return true; }
    inline virtual const std::string& getXid() { return xid; }
};

}}

#endif // ifndef QPID_LINEARSTORE_TXNCTXT_H


