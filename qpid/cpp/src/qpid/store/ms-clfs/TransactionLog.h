#ifndef QPID_STORE_MSCLFS_TRANSACTIONLOG_H
#define QPID_STORE_MSCLFS_TRANSACTIONLOG_H

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

#include <set>

#include <boost/enable_shared_from_this.hpp>
#include <boost/shared_ptr.hpp>

#include <qpid/broker/RecoveryManager.h>
#include <qpid/sys/IntegerTypes.h>
#include <qpid/sys/Mutex.h>

#include "Log.h"

namespace qpid {
namespace store {
namespace ms_clfs {

class Transaction;
class TPCTransaction;

/**
 * @class TransactionLog
 *
 * Represents a CLFS-housed transaction log.
 */
class TransactionLog : public Log,
                       public boost::enable_shared_from_this<TransactionLog> {

    // To know when it's ok to move the log tail the lowest valid Id must
    // always be known. Keep track of valid Ids here. These are transactions
    // which have not yet been Deleted in the log. They may be new, in progress,
    // prepared, committed, or aborted - but not deleted.
    // Entries corresponding to not-yet-finalized transactions (i.e., open or
    // prepared) also have a weak_ptr so the Transaction can be accessed.
    // This is primarily to check its state and get a list of prepared Xids.
    std::map<uint64_t, boost::weak_ptr<Transaction> > validIds;
    qpid::sys::Mutex idsLock;

protected:
    // Transaction log needs to have a no-op first record written in the log
    // to ensure that no real transaction gets an ID 0; messages think trans
    // id 0 means "no transaction."
    virtual void initialize();

public:
    // Inherited and reimplemented from Log. Figure the minimum marshalling
    // buffer size needed for the records this class writes.
    virtual uint32_t marshallingBufferSize();

    typedef boost::shared_ptr<TransactionLog> shared_ptr;

    // Get a new Transaction
    boost::shared_ptr<Transaction> begin();

    // Get a new TPCTransaction
    boost::shared_ptr<TPCTransaction> begin(const std::string& xid);

    void recordPrepare(uint64_t transId);
    void recordCommit(uint64_t transId);
    void recordAbort(uint64_t transId);
    void deleteTransaction(uint64_t transId);

    // Fill @arg preparedMap with Xid->TPCTransaction::shared_ptr for all
    // currently prepared transactions.
    void collectPreparedXids(std::map<std::string, boost::shared_ptr<TPCTransaction> >& preparedMap);

    // Recover the transactions and their state from the log.
    // Every non-deleted transaction recovered from the log will be
    // represented in @arg transMap. The recovering messages can use this
    // information to tell if a transaction referred to in an enqueue/dequeue
    // operation should be recovered or dropped by examining transaction state.
    //
    // @param recoverer  Recovery manager used to recreate broker objects from
    //                   entries recovered from the log.
    // @param transMap   This method fills in the map of id -> shared_ptr of
    //                   recovered transactions.
    void recover(std::map<uint64_t, boost::shared_ptr<Transaction> >& transMap);
};

}}}  // namespace qpid::store::ms_clfs

#endif /* QPID_STORE_MSCLFS_TRANSACTIONLOG_H */
