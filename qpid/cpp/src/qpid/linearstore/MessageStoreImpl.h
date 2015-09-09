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

#ifndef QPID_LINEARSTORE_MESSAGESTOREIMPL_H
#define QPID_LINEARSTORE_MESSAGESTOREIMPL_H

#include "qpid/broker/MessageStore.h"

#include "qpid/Options.h"
#include "qpid/linearstore/IdSequence.h"
#include "qpid/linearstore/JournalLogImpl.h"
#include "qpid/linearstore/journal/jcfg.h"
#include "qpid/linearstore/journal/EmptyFilePoolTypes.h"
#include "qpid/linearstore/PreparedTransaction.h"
#include "qpid/sys/Time.h"

#include "qmf/org/apache/qpid/linearstore/Store.h"

#include <iomanip>

// Assume DB_VERSION_MAJOR == 4
#if (DB_VERSION_MINOR == 2)
#include <errno.h>
#define DB_BUFFER_SMALL ENOMEM
#endif

class Db;
class DbEnv;
class Dbt;
class DbTxn;

namespace qpid {
namespace broker {
    class Broker;
}
namespace sys {
    class Timer;
}
namespace linearstore{
namespace journal {
    class EmptyFilePool;
    class EmptyFilePoolManager;
}

class IdDbt;
class JournalImpl;
class TplJournalImpl;
class TxnCtxt;

/**
 * An implementation of the MessageStore interface based on Berkeley DB
 */
class MessageStoreImpl : public qpid::broker::MessageStore, public qpid::management::Manageable
{
  public:
    typedef boost::shared_ptr<Db> db_ptr;
    typedef boost::shared_ptr<DbEnv> dbEnv_ptr;

    struct StoreOptions : public qpid::Options {
        StoreOptions(const std::string& name="Linear Store Options");
        std::string clusterName;
        std::string storeDir;
        bool truncateFlag;
        uint32_t wCachePageSizeKib;
        uint32_t tplWCachePageSizeKib;
        uint16_t efpPartition;
        uint64_t efpFileSizeKib;
        bool overwriteBeforeReturnFlag;
        qpid::sys::Duration journalFlushTimeout;
    };

  private:
    typedef std::map<uint64_t, qpid::broker::RecoverableQueue::shared_ptr> queue_index;
    typedef std::map<uint64_t, qpid::broker::RecoverableExchange::shared_ptr> exchange_index;
    typedef std::map<uint64_t, qpid::broker::RecoverableMessage::shared_ptr> message_index;

    typedef LockedMappings::map txn_lock_map;
    typedef boost::ptr_list<PreparedTransaction> txn_list;

    typedef std::map<std::string, JournalImpl*> JournalListMap;
    typedef JournalListMap::iterator JournalListMapItr;

    // Default store settings
    static const bool defTruncateFlag = false;
    static const uint32_t defWCachePageSizeKib = QLS_WMGR_DEF_PAGE_SIZE_KIB;
    static const uint32_t defTplWCachePageSizeKib = defWCachePageSizeKib / 8;
    static const uint16_t defEfpPartition = 1;
    static const uint64_t defEfpFileSizeKib = 512 * QLS_SBLK_SIZE_KIB;
    static const bool defOverwriteBeforeReturnFlag = false;
    static const std::string storeTopLevelDir;

    // FIXME aconway 2010-03-09: was 10ms
    static const uint64_t defJournalGetEventsTimeoutNs =   1 * 1000000; // 1ms
    static const uint64_t defJournalFlushTimeoutNs     = 500 * 1000000; // 500ms

    std::list<db_ptr> dbs;
    dbEnv_ptr dbenv;
    db_ptr queueDb;
    db_ptr configDb;
    db_ptr exchangeDb;
    db_ptr mappingDb;
    db_ptr bindingDb;
    db_ptr generalDb;

    // Pointer to Transaction Prepared List (TPL) journal instance
    boost::shared_ptr<TplJournalImpl> tplStorePtr;
    qpid::sys::Mutex tplInitLock;
    JournalListMap journalList;
    qpid::sys::Mutex journalListLock;
    qpid::sys::Mutex bdbLock;

    IdSequence queueIdSequence;
    IdSequence exchangeIdSequence;
    IdSequence generalIdSequence;
    IdSequence messageIdSequence;
    std::string storeDir;
    qpid::linearstore::journal::efpPartitionNumber_t defaultEfpPartitionNumber;
    qpid::linearstore::journal::efpDataSize_kib_t defaultEfpFileSize_kib;
    bool     overwriteBeforeReturnFlag;
    uint32_t wCachePgSizeSblks;
    uint16_t wCacheNumPages;
    uint32_t tplWCachePgSizeSblks;
    uint16_t tplWCacheNumPages;
    uint64_t highestRid;
    qpid::sys::Duration journalFlushTimeout;
    bool isInit;
    const char* envPath;
    qpid::broker::Broker* broker;
    JournalLogImpl jrnlLog;
    boost::shared_ptr<qpid::linearstore::journal::EmptyFilePoolManager> efpMgr;

    qmf::org::apache::qpid::linearstore::Store::shared_ptr mgmtObject;
    qpid::management::ManagementAgent* agent;


    // Parameter validation and calculation
    static uint32_t chkJrnlWrPageCacheSize(const uint32_t param,
                                           const std::string& paramName/*,
                                           const uint16_t jrnlFsizePgs*/);
    static uint16_t getJrnlWrNumPages(const uint32_t wrPageSizeKiB);
    static qpid::linearstore::journal::efpPartitionNumber_t chkEfpPartition(const qpid::linearstore::journal::efpPartitionNumber_t partition,
                                                                const std::string& paramName);
    static qpid::linearstore::journal::efpDataSize_kib_t chkEfpFileSizeKiB(const qpid::linearstore::journal::efpDataSize_kib_t efpFileSizeKiB,
                                                              const std::string& paramName);

    void init(const bool truncateFlag);

    void recoverQueues(TxnCtxt& txn,
                       qpid::broker::RecoveryManager& recovery,
                       queue_index& index,
                       txn_list& locked,
                       message_index& messages);
    void recoverMessages(TxnCtxt& txn,
                         qpid::broker::RecoveryManager& recovery,
                         queue_index& index,
                         txn_list& locked,
                         message_index& prepared);
    void recoverMessages(TxnCtxt& txn,
                         qpid::broker::RecoveryManager& recovery,
                         qpid::broker::RecoverableQueue::shared_ptr& queue,
                         txn_list& locked,
                         message_index& prepared,
                         long& rcnt,
                         long& idcnt);
    qpid::broker::RecoverableMessage::shared_ptr getExternMessage(qpid::broker::RecoveryManager& recovery,
                                                                  uint64_t mId,
                                                                  unsigned& headerSize);
    void recoverExchanges(TxnCtxt& txn,
                          qpid::broker::RecoveryManager& recovery,
                          exchange_index& index);
    void recoverBindings(TxnCtxt& txn,
                         exchange_index& exchanges,
                         queue_index& queues);
    void recoverGeneral(TxnCtxt& txn,
                        qpid::broker::RecoveryManager& recovery);
    int enqueueMessage(TxnCtxt& txn,
                       IdDbt& msgId,
                       qpid::broker::RecoverableMessage::shared_ptr& msg,
                       queue_index& index,
                       txn_list& locked,
                       message_index& prepared);
    void recoverTplStore();
    void recoverLockedMappings(txn_list& txns);
    TxnCtxt* check(qpid::broker::TransactionContext* ctxt);
    uint64_t msgEncode(std::vector<char>& buff, const boost::intrusive_ptr<qpid::broker::PersistableMessage>& message);
    void store(const qpid::broker::PersistableQueue* queue,
               TxnCtxt* txn,
               const boost::intrusive_ptr<qpid::broker::PersistableMessage>& message);
    void async_dequeue(qpid::broker::TransactionContext* ctxt,
                       const boost::intrusive_ptr<qpid::broker::PersistableMessage>& msg,
                       const qpid::broker::PersistableQueue& queue);
    void destroy(db_ptr db,
                 const qpid::broker::Persistable& p);
    bool create(db_ptr db,
                IdSequence& seq,
                const qpid::broker::Persistable& p);
    void completed(TxnCtxt& txn,
                   bool commit);
    void deleteBindingsForQueue(const qpid::broker::PersistableQueue& queue);
    void deleteBinding(const qpid::broker::PersistableExchange& exchange,
                       const qpid::broker::PersistableQueue& queue,
                       const std::string& key);

    void put(db_ptr db,
             DbTxn* txn,
             Dbt& key,
             Dbt& value);
    void open(db_ptr db,
              DbTxn* txn,
              const char* file,
              bool dupKey);
    void closeDbs();

    // journal functions
    void createJrnlQueue(const qpid::broker::PersistableQueue& queue);
    std::string getJrnlDir(const std::string& queueName);
    qpid::linearstore::journal::EmptyFilePool* getEmptyFilePool(const qpid::linearstore::journal::efpPartitionNumber_t p, const qpid::linearstore::journal::efpDataSize_kib_t s);
    qpid::linearstore::journal::EmptyFilePool* getEmptyFilePool(const qpid::framing::FieldTable& args);
    std::string getStoreTopLevelDir();
    std::string getJrnlBaseDir();
    std::string getBdbBaseDir();
    std::string getTplBaseDir();
    inline void checkInit() {
        // TODO: change the default dir to ~/.qpidd
        if (!isInit) { init("/tmp"); isInit = true; }
    }
    void chkTplStoreInit();

  public:
    typedef boost::shared_ptr<MessageStoreImpl> shared_ptr;

    MessageStoreImpl(qpid::broker::Broker* broker, const char* envpath = 0);

    virtual ~MessageStoreImpl();

    bool init(const qpid::Options* options);

    bool init(const std::string& dir,
              qpid::linearstore::journal::efpPartitionNumber_t efpPartition = defEfpPartition,
              qpid::linearstore::journal::efpDataSize_kib_t efpFileSizeKib = defEfpFileSizeKib,
              const bool truncateFlag = false,
              uint32_t wCachePageSize = defWCachePageSizeKib,
              uint32_t tplWCachePageSize = defTplWCachePageSizeKib,
              const bool overwriteBeforeReturnFlag_ = false);

    void truncateInit();

    void initManagement ();

    void finalize();

    // --- Implementation of qpid::broker::MessageStore ---

    void create(qpid::broker::PersistableQueue& queue,
                const qpid::framing::FieldTable& args);

    void destroy(qpid::broker::PersistableQueue& queue);

    void create(const qpid::broker::PersistableExchange& queue,
                const qpid::framing::FieldTable& args);

    void destroy(const qpid::broker::PersistableExchange& queue);

    void bind(const qpid::broker::PersistableExchange& exchange,
              const qpid::broker::PersistableQueue& queue,
              const std::string& key,
              const qpid::framing::FieldTable& args);

    void unbind(const qpid::broker::PersistableExchange& exchange,
                const qpid::broker::PersistableQueue& queue,
                const std::string& key,
                const qpid::framing::FieldTable& args);

    void create(const qpid::broker::PersistableConfig& config);

    void destroy(const qpid::broker::PersistableConfig& config);

    void recover(qpid::broker::RecoveryManager& queues);

    void stage(const boost::intrusive_ptr<qpid::broker::PersistableMessage>& msg);

    void destroy(qpid::broker::PersistableMessage& msg);

    void appendContent(const boost::intrusive_ptr<const qpid::broker::PersistableMessage>& msg,
                       const std::string& data);

    void loadContent(const qpid::broker::PersistableQueue& queue,
                     const boost::intrusive_ptr<const qpid::broker::PersistableMessage>& msg,
                     std::string& data,
                     uint64_t offset,
                     uint32_t length);

    void enqueue(qpid::broker::TransactionContext* ctxt,
                 const boost::intrusive_ptr<qpid::broker::PersistableMessage>& msg,
                 const qpid::broker::PersistableQueue& queue);

    void dequeue(qpid::broker::TransactionContext* ctxt,
                 const boost::intrusive_ptr<qpid::broker::PersistableMessage>& msg,
                 const qpid::broker::PersistableQueue& queue);

    void flush(const qpid::broker::PersistableQueue& queue);

    inline uint32_t outstandingQueueAIO(const qpid::broker::PersistableQueue& /*queue*/) { return 0; }; // TODO: Deprecate this call

    void collectPreparedXids(std::set<std::string>& xids);

    std::auto_ptr<qpid::broker::TransactionContext> begin();

    std::auto_ptr<qpid::broker::TPCTransactionContext> begin(const std::string& xid);

    void prepare(qpid::broker::TPCTransactionContext& ctxt);

    void localPrepare(TxnCtxt* ctxt);

    void commit(qpid::broker::TransactionContext& ctxt);

    void abort(qpid::broker::TransactionContext& ctxt);

    // --- Implementation of qpid::management::Managable ---

    qpid::management::ManagementObject::shared_ptr GetManagementObject (void) const
        { return mgmtObject; }

    inline qpid::management::Manageable::status_t ManagementMethod (uint32_t, qpid::management::Args&, std::string&)
        { return qpid::management::Manageable::STATUS_OK; }

    std::string getStoreDir() const;

  private:
    void journalDeleted(JournalImpl&);

}; // class MessageStoreImpl

} // namespace msgstore
} // namespace mrg

#endif // ifndef QPID_LINEARSTORE_MESSAGESTOREIMPL_H
