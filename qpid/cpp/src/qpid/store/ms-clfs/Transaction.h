#ifndef QPID_STORE_MSCLFS_TRANSACTION_H
#define QPID_STORE_MSCLFS_TRANSACTION_H

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

#include <qpid/broker/TransactionalStore.h>
#include <qpid/sys/Mutex.h>
#include <boost/enable_shared_from_this.hpp>
#include <boost/shared_ptr.hpp>
#include <string>
#include <vector>

#include "TransactionLog.h"

namespace qpid {
namespace store {
namespace ms_clfs {

class Messages;

/**
 * @class Transaction
 *
 * Class representing an AMQP transaction. This is used around a set of
 * enqueue and dequeue operations that occur when the broker is acting
 * on a transaction commit/abort from the client.
 * This class is what the store uses internally to implement things a
 * transaction needs; the broker knows about TransactionContext, which
 * holds a pointer to Transaction.
 *
 * NOTE: All references to Transactions (and TPCTransactions, below) are
 * through Boost shared_ptr instances. All messages enrolled in a transaction
 * hold a shared_ptr. Thus, a Transaction object will not be deleted until all
 * messages holding a reference to it are deleted. This fact is also used
 * during recovery to automatically clean up and delete any Transaction without
 * messages left referring to it.
 */
class Transaction : public boost::enable_shared_from_this<Transaction> {
private:
    // TransactionLog has to create all Transaction instances.
    Transaction() {}

public:

    typedef boost::shared_ptr<Transaction> shared_ptr;
    typedef enum { TRANS_OPEN = 1,
                   TRANS_PREPARED,
                   TRANS_ABORTED,
                   TRANS_COMMITTED,
                   TRANS_DELETED    } State;

    virtual ~Transaction();

    uint64_t getId() { return id; }
    State getState() { return state; }

    void enroll(uint64_t msgId);
    void unenroll(uint64_t msgId);   // For failed ops, not normal end-of-trans

    void abort(Messages& messages);
    void commit(Messages& messages);

protected:
    friend class TransactionLog;
    Transaction(uint64_t _id, const TransactionLog::shared_ptr& _log)
        : id(_id), state(TRANS_OPEN), log(_log) {}

    uint64_t id;
    State state;
    TransactionLog::shared_ptr log;
    std::vector<uint64_t> enrolledMessages;
    qpid::sys::RWlock enrollLock;
};

class TransactionContext : public qpid::broker::TransactionContext {
    Transaction::shared_ptr transaction;

public:
    TransactionContext(const Transaction::shared_ptr& _transaction)
        : transaction(_transaction) {}

    virtual Transaction::shared_ptr& getTransaction() { return transaction; }
};

/**
 * @class TPCTransaction
 *
 * Class representing a Two-Phase-Commit (TPC) AMQP transaction. This is
 * used around a set of enqueue and dequeue operations that occur when the
 * broker is acting on a transaction prepare/commit/abort from the client.
 * This class is what the store uses internally to implement things a
 * transaction needs; the broker knows about TPCTransactionContext, which
 * holds a pointer to TPCTransaction.
 */
class TPCTransaction : public Transaction {

    friend class TransactionLog;
    TPCTransaction(uint64_t _id,
                   const TransactionLog::shared_ptr& _log,
                   const std::string& _xid)
        : Transaction(_id, _log), xid(_xid) {}

    std::string  xid;

public: 
    typedef boost::shared_ptr<TPCTransaction> shared_ptr;

    virtual ~TPCTransaction() {}

    void prepare();
    bool isPrepared() const { return state == TRANS_PREPARED; }

    const std::string& getXid(void) const { return xid; }
};

class TPCTransactionContext : public qpid::broker::TPCTransactionContext {
    TPCTransaction::shared_ptr transaction;

public:
    TPCTransactionContext(const TPCTransaction::shared_ptr& _transaction)
        : transaction(_transaction) {}

    virtual TPCTransaction::shared_ptr& getTransaction() { return transaction; }
};

}}}  // namespace qpid::store::ms_clfs

#endif /* QPID_STORE_MSCLFS_TRANSACTION_H */
