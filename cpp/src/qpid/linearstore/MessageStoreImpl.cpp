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

#include "qpid/linearstore/MessageStoreImpl.h"

#include "db-inc.h"
#include "qpid/broker/QueueSettings.h"
#include "qpid/linearstore/BindingDbt.h"
#include "qpid/linearstore/BufferValue.h"
#include "qpid/linearstore/IdDbt.h"
#include "qpid/linearstore/jrnl/EmptyFilePoolManager.h"
#include "qpid/linearstore/jrnl/txn_map.h"
#include "qpid/framing/FieldValue.h"
#include "qmf/org/apache/qpid/linearstore/Package.h"
#include "qpid/linearstore/StoreException.h"
#include <dirent.h>


#define MAX_AIO_SLEEPS 100000 // tot: ~1 sec
#define AIO_SLEEP_TIME_US  10 // 0.01 ms

namespace _qmf = qmf::org::apache::qpid::linearstore;

namespace qpid{
namespace linearstore{


const std::string MessageStoreImpl::storeTopLevelDir("qls"); // Sets the top-level store dir name

// FIXME aconway 2010-03-09: was 10
qpid::sys::Duration MessageStoreImpl::defJournalGetEventsTimeout(1 * qpid::sys::TIME_MSEC); // 10ms
qpid::sys::Duration MessageStoreImpl::defJournalFlushTimeout(500 * qpid::sys::TIME_MSEC); // 0.5s
qpid::sys::Mutex TxnCtxt::globalSerialiser;

MessageStoreImpl::TplRecoverStruct::TplRecoverStruct(const uint64_t rid_,
                                                     const bool deq_flag_,
                                                     const bool commit_flag_,
                                                     const bool tpc_flag_) :
                                                     rid(rid_),
                                                     deq_flag(deq_flag_),
                                                     commit_flag(commit_flag_),
                                                     tpc_flag(tpc_flag_)
{}

MessageStoreImpl::MessageStoreImpl(qpid::broker::Broker* broker_, const char* envpath_) :
                                   defaultEfpPartitionNumber(0),
                                   defaultEfpFileSize_kib(0),
                                   truncateFlag(false),
                                   wCachePgSizeSblks(0),
                                   wCacheNumPages(0),
                                   tplWCachePgSizeSblks(0),
                                   tplWCacheNumPages(0),
                                   highestRid(0),
                                   isInit(false),
                                   envPath(envpath_),
                                   broker(broker_),
                                   jrnlLog(qpid::qls_jrnl::JournalLog::LOG_NOTICE),
                                   mgmtObject(),
                                   agent(0)
{}

uint32_t MessageStoreImpl::chkJrnlWrPageCacheSize(const uint32_t param_, const std::string& paramName_)
{
    uint32_t p = param_;

    if (p == 0) {
        // For zero value, use default
        p = QLS_WMGR_DEF_PAGE_SIZE_KIB;
        QLS_LOG(warning, "parameter " << paramName_ << " (" << param_ << ") must be a power of 2 between 1 and 128; changing this parameter to default value (" << p << ")");
    } else if ( p > 128 || (p & (p-1)) ) {
        // For any positive value that is not a power of 2, use closest value
        if      (p <   6)   p =   4;
        else if (p <  12)   p =   8;
        else if (p <  24)   p =  16;
        else if (p <  48)   p =  32;
        else if (p <  96)   p =  64;
        else                p = 128;
        QLS_LOG(warning, "parameter " << paramName_ << " (" << param_ << ") must be a power of 2 between 1 and 128; changing this parameter to closest allowable value (" << p << ")");
    }
    return p;
}

uint16_t MessageStoreImpl::getJrnlWrNumPages(const uint32_t wrPageSizeKib_)
{
    uint32_t wrPageSizeSblks = wrPageSizeKib_ / QLS_SBLK_SIZE_KIB; // convert from KiB to number sblks
    uint32_t defTotWCacheSizeSblks = QLS_WMGR_DEF_PAGE_SIZE_SBLKS * QLS_WMGR_DEF_PAGES;
    switch (wrPageSizeKib_)
    {
      case 1:
      case 2:
      case 4:
        // 256 KiB total cache
        return defTotWCacheSizeSblks / wrPageSizeSblks / 4;
      case 8:
      case 16:
        // 512 KiB total cache
        return defTotWCacheSizeSblks / wrPageSizeSblks / 2;
      default: // 32, 64, 128
        // 1 MiB total cache
        return defTotWCacheSizeSblks / wrPageSizeSblks;
    }
}

qpid::qls_jrnl::efpPartitionNumber_t MessageStoreImpl::chkEfpPartition(const qpid::qls_jrnl::efpPartitionNumber_t partition_,
                                                                       const std::string& /*paramName_*/) {
    // TODO: check against list of existing partitions, throw if not found
    return partition_;
}

qpid::qls_jrnl::efpDataSize_kib_t MessageStoreImpl::chkEfpFileSizeKiB(const qpid::qls_jrnl::efpDataSize_kib_t efpFileSizeKib_,
                                                                     const std::string& paramName_) {
    uint8_t rem =  efpFileSizeKib_ % uint64_t(QLS_SBLK_SIZE_KIB);
    if (rem != 0) {
        uint64_t newVal = efpFileSizeKib_ - rem;
        if (rem >= (QLS_SBLK_SIZE_KIB / 2))
            newVal += QLS_SBLK_SIZE_KIB;
        QLS_LOG(warning, "Parameter " << paramName_ << " (" << efpFileSizeKib_ << ") must be a multiple of " <<
                QLS_SBLK_SIZE_KIB << "; changing this parameter to the closest allowable value (" <<
                newVal << ")");
        return newVal;
    }
    return efpFileSizeKib_;

    // TODO: check against list of existing pools in the given partition
}

void MessageStoreImpl::initManagement ()
{
    if (broker != 0) {
        agent = broker->getManagementAgent();
        if (agent != 0) {
            _qmf::Package packageInitializer(agent);
            mgmtObject = _qmf::Store::shared_ptr (
                new _qmf::Store(agent, this, broker));

            mgmtObject->set_location(storeDir);
            mgmtObject->set_tplIsInitialized(false);
            mgmtObject->set_tplDirectory(getTplBaseDir());
            mgmtObject->set_tplWritePageSize(tplWCachePgSizeSblks * QLS_SBLK_SIZE_BYTES);
            mgmtObject->set_tplWritePages(tplWCacheNumPages);

            agent->addObject(mgmtObject, 0, true);

            // Initialize all existing queues (ie those recovered before management was initialized)
            for (JournalListMapItr i=journalList.begin(); i!=journalList.end(); i++) {
                i->second->initManagement(agent);
            }
        }
    }
}

bool MessageStoreImpl::init(const qpid::Options* options_)
{
    // Extract and check options
    const StoreOptions* opts = static_cast<const StoreOptions*>(options_);
    qpid::qls_jrnl::efpPartitionNumber_t efpPartition = chkEfpPartition(opts->efpPartition, "efp-partition");
    qpid::qls_jrnl::efpDataSize_kib_t efpFilePoolSize_kib = chkEfpFileSizeKiB(opts->efpFileSizeKib, "efp-file-size");
    uint32_t jrnlWrCachePageSizeKib = chkJrnlWrPageCacheSize(opts->wCachePageSizeKib, "wcache-page-size");
    uint32_t tplJrnlWrCachePageSizeKib = chkJrnlWrPageCacheSize(opts->tplWCachePageSizeKib, "tpl-wcache-page-size");

    // Pass option values to init()
    return init(opts->storeDir, efpPartition, efpFilePoolSize_kib, opts->truncateFlag, jrnlWrCachePageSizeKib, tplJrnlWrCachePageSizeKib);
}

// These params, taken from options, are assumed to be correct and verified
bool MessageStoreImpl::init(const std::string& storeDir_,
                           qpid::qls_jrnl::efpPartitionNumber_t efpPartition_,
                           qpid::qls_jrnl::efpDataSize_kib_t efpFileSize_kib_,
                           const bool truncateFlag_,
                           uint32_t wCachePageSizeKib_,
                           uint32_t tplWCachePageSizeKib_)
{
    if (isInit) return true;

    // Set geometry members (converting to correct units where req'd)
    defaultEfpPartitionNumber = efpPartition_;
    defaultEfpFileSize_kib = efpFileSize_kib_;
    wCachePgSizeSblks = wCachePageSizeKib_ / QLS_SBLK_SIZE_KIB; // convert from KiB to number sblks
    wCacheNumPages = getJrnlWrNumPages(wCachePageSizeKib_);
    tplWCachePgSizeSblks = tplWCachePageSizeKib_ / QLS_SBLK_SIZE_KIB; // convert from KiB to number sblks
    tplWCacheNumPages = getJrnlWrNumPages(tplWCachePageSizeKib_);
    if (storeDir_.size()>0) storeDir = storeDir_;

    if (truncateFlag_)
        truncateInit();
    else
        init();

    QLS_LOG(notice, "Store module initialized; store-dir=" << storeDir_);
    QLS_LOG(info,   "> Default EFP partition: " << defaultEfpPartitionNumber);
    QLS_LOG(info,   "> Default EFP file size: " << defaultEfpFileSize_kib << " (KiB)");
    QLS_LOG(info,   "> Default write cache page size: " << wCachePageSizeKib_ << " (KiB)");
    QLS_LOG(info,   "> Default number of write cache pages: " << wCacheNumPages);
    QLS_LOG(info,   "> TPL write cache page size: " << tplWCachePageSizeKib_ << " (KiB)");
    QLS_LOG(info,   "> TPL number of write cache pages: " << tplWCacheNumPages);
    QLS_LOG(info,   "> EFP partition: " << defaultEfpPartitionNumber);
    QLS_LOG(info,   "> EFP file size pool: " << defaultEfpFileSize_kib << " (KiB)");

    return isInit;
}

void MessageStoreImpl::init()
{
    const int retryMax = 3;
    int bdbRetryCnt = 0;
    do {
        if (bdbRetryCnt++ > 0)
        {
            closeDbs();
            ::usleep(1000000); // 1 sec delay
            QLS_LOG(error, "Previoius BDB store initialization failed, retrying (" << bdbRetryCnt << " of " << retryMax << ")...");
        }

        try {
            qpid::qls_jrnl::jdir::create_dir(getBdbBaseDir());

            dbenv.reset(new DbEnv(0));
            dbenv->set_errpfx("linearstore");
            dbenv->set_lg_regionmax(256000); // default = 65000
            dbenv->open(getBdbBaseDir().c_str(), DB_THREAD | DB_CREATE | DB_INIT_TXN | DB_INIT_LOCK | DB_INIT_LOG | DB_INIT_MPOOL | DB_USE_ENVIRON | DB_RECOVER, 0);

            // Databases are constructed here instead of the constructor so that the DB_RECOVER flag can be used
            // against the database environment. Recover can only be performed if no databases have been created
            // against the environment at the time of recovery, as recovery invalidates the environment.
            queueDb.reset(new Db(dbenv.get(), 0));
            dbs.push_back(queueDb);
            configDb.reset(new Db(dbenv.get(), 0));
            dbs.push_back(configDb);
            exchangeDb.reset(new Db(dbenv.get(), 0));
            dbs.push_back(exchangeDb);
            mappingDb.reset(new Db(dbenv.get(), 0));
            dbs.push_back(mappingDb);
            bindingDb.reset(new Db(dbenv.get(), 0));
            dbs.push_back(bindingDb);
            generalDb.reset(new Db(dbenv.get(), 0));
            dbs.push_back(generalDb);

            TxnCtxt txn;
            txn.begin(dbenv.get(), false);
            try {
                open(queueDb, txn.get(), "queues.db", false);
                open(configDb, txn.get(), "config.db", false);
                open(exchangeDb, txn.get(), "exchanges.db", false);
                open(mappingDb, txn.get(), "mappings.db", true);
                open(bindingDb, txn.get(), "bindings.db", true);
                open(generalDb, txn.get(), "general.db",  false);
                txn.commit();
            } catch (...) { txn.abort(); throw; }
            // NOTE: during normal initialization, agent == 0 because the store is initialized before the management infrastructure.
            // However during a truncated initialization in a cluster, agent != 0. We always pass 0 as the agent for the
            // TplStore to keep things consistent in a cluster. See https://bugzilla.redhat.com/show_bug.cgi?id=681026
            tplStorePtr.reset(new TplJournalImpl(broker->getTimer(), "TplStore", getTplBaseDir(), jrnlLog, defJournalGetEventsTimeout, defJournalFlushTimeout, 0));
            isInit = true;
        } catch (const DbException& e) {
            if (e.get_errno() == DB_VERSION_MISMATCH)
            {
                QLS_LOG(error, "Database environment mismatch: This version of db4 does not match that which created the store database.: " << e.what());
                THROW_STORE_EXCEPTION_2("Database environment mismatch: This version of db4 does not match that which created the store database. "
                                        "(If recovery is not important, delete the contents of the store directory. Otherwise, try upgrading the database using "
                                        "db_upgrade or using db_recover - but the db4-utils package must also be installed to use these utilities.)", e);
            }
            QLS_LOG(error, "BDB exception occurred while initializing store: " << e.what());
            if (bdbRetryCnt >= retryMax)
                THROW_STORE_EXCEPTION_2("BDB exception occurred while initializing store", e);
        } catch (const StoreException&) {
            throw;
        } catch (const qpid::qls_jrnl::jexception& e) {
            QLS_LOG(error, "Journal Exception occurred while initializing store: " << e);
            THROW_STORE_EXCEPTION_2("Journal Exception occurred while initializing store", e.what());
        } catch (...) {
            QLS_LOG(error, "Unknown exception occurred while initializing store.");
            throw;
        }
    } while (!isInit);

    efpMgr.reset(new qpid::qls_jrnl::EmptyFilePoolManager(getStoreTopLevelDir(),
                                                          defaultEfpPartitionNumber,
                                                          defaultEfpFileSize_kib,
                                                          jrnlLog));
    efpMgr->findEfpPartitions();
}

void MessageStoreImpl::finalize()
{
    if (tplStorePtr.get() && tplStorePtr->is_ready()) tplStorePtr->stop(true);
    {
        qpid::sys::Mutex::ScopedLock sl(journalListLock);
        for (JournalListMapItr i = journalList.begin(); i != journalList.end(); i++)
        {
            JournalImpl* jQueue = i->second;
            jQueue->resetDeleteCallback();
            if (jQueue->is_ready()) jQueue->stop(true);
        }
    }

    if (mgmtObject.get() != 0) {
        mgmtObject->resourceDestroy();
	mgmtObject.reset();
    }
}

void MessageStoreImpl::truncateInit()
{
    if (isInit) {
        {
            qpid::sys::Mutex::ScopedLock sl(journalListLock);
            if (journalList.size()) { // check no queues exist
                std::ostringstream oss;
                oss << "truncateInit() called with " << journalList.size() << " queues still in existence";
                THROW_STORE_EXCEPTION(oss.str());
            }
        }
        closeDbs();
        dbs.clear();
        if (tplStorePtr->is_ready()) tplStorePtr->stop(true);
        dbenv->close(0);
        isInit = false;
    }

    // TODO: Linearstore: harvest all discareded journal files into the empy file pool(s).

    qpid::qls_jrnl::jdir::delete_dir(getBdbBaseDir());
    qpid::qls_jrnl::jdir::delete_dir(getJrnlBaseDir());
    qpid::qls_jrnl::jdir::delete_dir(getTplBaseDir());
    QLS_LOG(notice, "Store directory " << getStoreTopLevelDir() << " was truncated.");
    init();
}

void MessageStoreImpl::chkTplStoreInit()
{
    // Prevent multiple threads from late-initializing the TPL
    qpid::sys::Mutex::ScopedLock sl(tplInitLock);
    if (!tplStorePtr->is_ready()) {
        qpid::qls_jrnl::jdir::create_dir(getTplBaseDir());
        tplStorePtr->initialize(/*tplNumJrnlFiles, false, 0, tplJrnlFsizeSblks,*/ getEmptyFilePool(defaultEfpPartitionNumber, defaultEfpFileSize_kib), tplWCacheNumPages, tplWCachePgSizeSblks);
        if (mgmtObject.get() != 0) mgmtObject->set_tplIsInitialized(true);
    }
}

void MessageStoreImpl::open(db_ptr db_,
                            DbTxn* txn_,
                            const char* file_,
                            bool dupKey_)
{
    if(dupKey_) db_->set_flags(DB_DUPSORT);
    db_->open(txn_, file_, 0, DB_BTREE, DB_CREATE | DB_THREAD, 0);
}

void MessageStoreImpl::closeDbs()
{
    for (std::list<db_ptr >::iterator i = dbs.begin(); i != dbs.end(); i++) {
        (*i)->close(0);
    }
    dbs.clear();
}

MessageStoreImpl::~MessageStoreImpl()
{
    finalize();
    try {
        closeDbs();
    } catch (const DbException& e) {
        QLS_LOG(error, "Error closing BDB databases: " <<  e.what());
    } catch (const qpid::qls_jrnl::jexception& e) {
        QLS_LOG(error, "Error: " << e.what());
    } catch (const std::exception& e) {
        QLS_LOG(error, "Error: " << e.what());
    } catch (...) {
        QLS_LOG(error, "Unknown error in MessageStoreImpl::~MessageStoreImpl()");
    }

    if (mgmtObject.get() != 0) {
        mgmtObject->resourceDestroy();
	mgmtObject.reset();
    }
}

void MessageStoreImpl::create(qpid::broker::PersistableQueue& queue_,
                              const qpid::framing::FieldTable& args_)
{
    QLS_LOG(info,   "*** MessageStoreImpl::create() queue=\"" << queue_.getName() << "\""); // DEBUG
    checkInit();
    if (queue_.getPersistenceId()) {
        THROW_STORE_EXCEPTION("Queue already created: " + queue_.getName());
    }
    JournalImpl* jQueue = 0;

    if (queue_.getName().size() == 0)
    {
        QLS_LOG(error, "Cannot create store for empty (null) queue name - ignoring and attempting to continue.");
        return;
    }

    jQueue = new JournalImpl(broker->getTimer(), queue_.getName(), getJrnlDir(queue_.getName()), jrnlLog,
                             defJournalGetEventsTimeout, defJournalFlushTimeout, agent,
                             boost::bind(&MessageStoreImpl::journalDeleted, this, _1));
    {
        qpid::sys::Mutex::ScopedLock sl(journalListLock);
        journalList[queue_.getName()]=jQueue;
    }

    queue_.setExternalQueueStore(dynamic_cast<qpid::broker::ExternalQueueStore*>(jQueue));
    try {
        jQueue->initialize(getEmptyFilePool(args_), wCacheNumPages, wCachePgSizeSblks);
    } catch (const qpid::qls_jrnl::jexception& e) {
        THROW_STORE_EXCEPTION(std::string("Queue ") + queue_.getName() + ": create() failed: " + e.what());
    }
    try {
        if (!create(queueDb, queueIdSequence, queue_)) {
            THROW_STORE_EXCEPTION("Queue already exists: " + queue_.getName());
        }
    } catch (const DbException& e) {
        THROW_STORE_EXCEPTION_2("Error creating queue named  " + queue_.getName(), e);
    }
}

qpid::qls_jrnl::EmptyFilePool*
MessageStoreImpl::getEmptyFilePool(const qpid::qls_jrnl::efpPartitionNumber_t efpPartitionNumber_,
                                   const qpid::qls_jrnl::efpDataSize_kib_t efpFileSizeKib_) {
    qpid::qls_jrnl::EmptyFilePool* efpp = efpMgr->getEmptyFilePool(efpPartitionNumber_, efpFileSizeKib_);
    if (efpp == 0) {
        std::ostringstream oss;
        oss << "Partition=" << efpPartitionNumber_ << "; EfpFileSize=" << efpFileSizeKib_ << " KiB";
        throw qpid::qls_jrnl::jexception(qpid::qls_jrnl::jerrno::JERR_EFP_NOEFP, oss.str(), "MessageStoreImpl", "getEmptyFilePool");
    }
    return efpp;
}

qpid::qls_jrnl::EmptyFilePool*
MessageStoreImpl::getEmptyFilePool(const qpid::framing::FieldTable& args_) {
    qpid::framing::FieldTable::ValuePtr value;
    qpid::qls_jrnl::efpPartitionNumber_t localEfpPartition = defaultEfpPartitionNumber;
    value = args_.get("qpid.efp_partition");
    if (value.get() != 0 && !value->empty() && value->convertsTo<int>()) {
        localEfpPartition = chkEfpPartition((uint32_t)value->get<int>(), "qpid.efp_partition");
    }

    qpid::qls_jrnl::efpDataSize_kib_t localEfpFileSizeKib = defaultEfpFileSize_kib;
    value = args_.get("qpid.efp_file_size");
    if (value.get() != 0 && !value->empty() && value->convertsTo<int>()) {
        localEfpFileSizeKib = chkEfpFileSizeKiB((uint32_t)value->get<int>(),"qpid.efp_file_size" );
    }
    return getEmptyFilePool(localEfpPartition, localEfpFileSizeKib);
}

void MessageStoreImpl::destroy(qpid::broker::PersistableQueue& queue_)
{
    QLS_LOG(info,   "*** MessageStoreImpl::destroy() queue=\"" << queue_.getName() << "\"");
    checkInit();
    destroy(queueDb, queue_);
    deleteBindingsForQueue(queue_);
    qpid::broker::ExternalQueueStore* eqs = queue_.getExternalQueueStore();
    if (eqs) {
        JournalImpl* jQueue = static_cast<JournalImpl*>(eqs);
        jQueue->delete_jrnl_files();
        queue_.setExternalQueueStore(0); // will delete the journal if exists
        {
            qpid::sys::Mutex::ScopedLock sl(journalListLock);
            journalList.erase(queue_.getName());
        }
    }
}

void MessageStoreImpl::create(const qpid::broker::PersistableExchange& exchange_,
                              const qpid::framing::FieldTable& /*args_*/)
{
    checkInit();
    if (exchange_.getPersistenceId()) {
        THROW_STORE_EXCEPTION("Exchange already created: " + exchange_.getName());
    }
    try {
        if (!create(exchangeDb, exchangeIdSequence, exchange_)) {
            THROW_STORE_EXCEPTION("Exchange already exists: " + exchange_.getName());
        }
    } catch (const DbException& e) {
        THROW_STORE_EXCEPTION_2("Error creating exchange named " + exchange_.getName(), e);
    }
}

void MessageStoreImpl::destroy(const qpid::broker::PersistableExchange& exchange)
{
    checkInit();
    destroy(exchangeDb, exchange);
    //need to also delete bindings
    IdDbt key(exchange.getPersistenceId());
    bindingDb->del(0, &key, DB_AUTO_COMMIT);
}

void MessageStoreImpl::create(const qpid::broker::PersistableConfig& general_)
{
    checkInit();
    if (general_.getPersistenceId()) {
        THROW_STORE_EXCEPTION("General configuration item already created");
    }
    try {
        if (!create(generalDb, generalIdSequence, general_)) {
            THROW_STORE_EXCEPTION("General configuration already exists");
        }
    } catch (const DbException& e) {
        THROW_STORE_EXCEPTION_2("Error creating general configuration", e);
    }
}

void MessageStoreImpl::destroy(const qpid::broker::PersistableConfig& general_)
{
    checkInit();
    destroy(generalDb, general_);
}

bool MessageStoreImpl::create(db_ptr db_,
                              IdSequence& seq_,
                              const qpid::broker::Persistable& p_)
{
    uint64_t id (seq_.next());
    Dbt key(&id, sizeof(id));
    BufferValue value (p_);

    int status;
    TxnCtxt txn;
    txn.begin(dbenv.get(), true);
    try {
        status = db_->put(txn.get(), &key, &value, DB_NOOVERWRITE);
        txn.commit();
    } catch (...) {
        txn.abort();
        throw;
    }
    if (status == DB_KEYEXIST) {
        return false;
    } else {
        p_.setPersistenceId(id);
        return true;
    }
}

void MessageStoreImpl::destroy(db_ptr db, const qpid::broker::Persistable& p_)
{
    qpid::sys::Mutex::ScopedLock sl(bdbLock);
    IdDbt key(p_.getPersistenceId());
    db->del(0, &key, DB_AUTO_COMMIT);
}


void MessageStoreImpl::bind(const qpid::broker::PersistableExchange& e_,
                            const qpid::broker::PersistableQueue& q_,
                            const std::string& k_,
                            const qpid::framing::FieldTable& a_)
{
    checkInit();
    IdDbt key(e_.getPersistenceId());
    BindingDbt value(e_, q_, k_, a_);
    TxnCtxt txn;
    txn.begin(dbenv.get(), true);
    try {
        put(bindingDb, txn.get(), key, value);
        txn.commit();
    } catch (...) {
        txn.abort();
        throw;
    }
}

void MessageStoreImpl::unbind(const qpid::broker::PersistableExchange& e_,
                              const qpid::broker::PersistableQueue& q_,
                              const std::string& k_,
                              const qpid::framing::FieldTable& /*a_*/)
{
    checkInit();
    deleteBinding(e_, q_, k_);
}

void MessageStoreImpl::recover(qpid::broker::RecoveryManager& registry_)
{
    checkInit();
    txn_list prepared;
    recoverLockedMappings(prepared);

    queue_index queues;//id->queue
    exchange_index exchanges;//id->exchange
    message_index messages;//id->message

    TxnCtxt txn;
    txn.begin(dbenv.get(), false);
    try {
        //read all queues, calls recoversMessages
        recoverQueues(txn, registry_, queues, prepared, messages);

        //recover exchange & bindings:
        recoverExchanges(txn, registry_, exchanges);
        recoverBindings(txn, exchanges, queues);

        //recover general-purpose configuration
        recoverGeneral(txn, registry_);

        txn.commit();
    } catch (const DbException& e) {
        txn.abort();
        THROW_STORE_EXCEPTION_2("Error on recovery", e);
    } catch (...) {
        txn.abort();
        throw;
    }

    //recover transactions:
    for (txn_list::iterator i = prepared.begin(); i != prepared.end(); i++) {
        const PreparedTransaction pt = *i;
        if (mgmtObject.get() != 0) {
            mgmtObject->inc_tplTransactionDepth();
            mgmtObject->inc_tplTxnPrepares();
        }

        std::string xid = pt.xid;

        // Restore data token state in TxnCtxt
        TplRecoverMapCitr citr = tplRecoverMap.find(xid);
        if (citr == tplRecoverMap.end()) THROW_STORE_EXCEPTION("XID not found in tplRecoverMap");

        // If a record is found that is dequeued but not committed/aborted from tplStore, then a complete() call
        // was interrupted part way through committing/aborting the impacted queues. Complete this process.
        bool incomplTplTxnFlag = citr->second.deq_flag;

        if (citr->second.tpc_flag) {
            // Dtx (2PC) transaction
            TPCTxnCtxt* tpcc = new TPCTxnCtxt(xid, &messageIdSequence);
            std::auto_ptr<qpid::broker::TPCTransactionContext> txn(tpcc);
            tpcc->recoverDtok(citr->second.rid, xid);
            tpcc->prepare(tplStorePtr.get());

            qpid::broker::RecoverableTransaction::shared_ptr dtx;
            if (!incomplTplTxnFlag) dtx = registry_.recoverTransaction(xid, txn);
            if (pt.enqueues.get()) {
                for (LockedMappings::iterator j = pt.enqueues->begin(); j != pt.enqueues->end(); j++) {
                    tpcc->addXidRecord(queues[j->first]->getExternalQueueStore());
                    if (!incomplTplTxnFlag) dtx->enqueue(queues[j->first], messages[j->second]);
                }
            }
            if (pt.dequeues.get()) {
                for (LockedMappings::iterator j = pt.dequeues->begin(); j != pt.dequeues->end(); j++) {
                    tpcc->addXidRecord(queues[j->first]->getExternalQueueStore());
                    if (!incomplTplTxnFlag) dtx->dequeue(queues[j->first], messages[j->second]);
                }
            }

            if (incomplTplTxnFlag) {
                tpcc->complete(citr->second.commit_flag);
            }
        } else {
            // Local (1PC) transaction
            boost::shared_ptr<TxnCtxt> opcc(new TxnCtxt(xid, &messageIdSequence));
            opcc->recoverDtok(citr->second.rid, xid);
            opcc->prepare(tplStorePtr.get());

            if (pt.enqueues.get()) {
                for (LockedMappings::iterator j = pt.enqueues->begin(); j != pt.enqueues->end(); j++) {
                    opcc->addXidRecord(queues[j->first]->getExternalQueueStore());
                }
            }
            if (pt.dequeues.get()) {
                for (LockedMappings::iterator j = pt.dequeues->begin(); j != pt.dequeues->end(); j++) {
                    opcc->addXidRecord(queues[j->first]->getExternalQueueStore());
                }
            }
            if (incomplTplTxnFlag) {
                opcc->complete(citr->second.commit_flag);
            } else {
                completed(*opcc.get(), citr->second.commit_flag);
            }
        }
    }
    registry_.recoveryComplete();
}

void MessageStoreImpl::recoverQueues(TxnCtxt& txn,
                                     qpid::broker::RecoveryManager& registry,
                                     queue_index& queue_index,
                                     txn_list& prepared,
                                     message_index& messages)
{
    QLS_LOG(info,   "*** MessageStoreImpl::recoverQueues()");
    Cursor queues;
    queues.open(queueDb, txn.get());

    uint64_t maxQueueId(1);

    IdDbt key;
    Dbt value;
    //read all queues
    while (queues.next(key, value)) {
        qpid::framing::Buffer buffer(reinterpret_cast<char*>(value.get_data()), value.get_size());
        //create a Queue instance
        qpid::broker::RecoverableQueue::shared_ptr queue = registry.recoverQueue(buffer);
        //set the persistenceId and update max as required
        queue->setPersistenceId(key.id);

        const std::string queueName = queue->getName().c_str();
        JournalImpl* jQueue = 0;
        if (queueName.size() == 0)
        {
            QLS_LOG(error, "Cannot recover empty (null) queue name - ignoring and attempting to continue.");
            break;
        }
        jQueue = new JournalImpl(broker->getTimer(), queueName, getJrnlDir(queueName),jrnlLog,
                                 defJournalGetEventsTimeout, defJournalFlushTimeout, agent,
                                 boost::bind(&MessageStoreImpl::journalDeleted, this, _1));
        {
            qpid::sys::Mutex::ScopedLock sl(journalListLock);
            journalList[queueName] = jQueue;
        }
        queue->setExternalQueueStore(dynamic_cast<qpid::broker::ExternalQueueStore*>(jQueue));

        try
        {
            long rcnt = 0L;     // recovered msg count
            long idcnt = 0L;    // in-doubt msg count
            uint64_t thisHighestRid = 0ULL;
            jQueue->recover(boost::dynamic_pointer_cast<qpid::qls_jrnl::EmptyFilePoolManager>(efpMgr), wCacheNumPages, wCachePgSizeSblks, &prepared, thisHighestRid, key.id);

            // Check for changes to queue store settings qpid.file_count and qpid.file_size resulting
            // from recovery of a store that has had its size changed externally by the resize utility.
            // If so, update the queue store settings so that QMF queries will reflect the new values.
            // TODO: Update this for new settings, as qpid.file_count and qpid.file_size no longer apply
/*
            const qpid::framing::FieldTable& storeargs = queue->getSettings().storeSettings;
            qpid::framing::FieldTable::ValuePtr value;
            value = storeargs.get("qpid.file_count");
            if (value.get() != 0 && !value->empty() && value->convertsTo<int>() && (uint16_t)value->get<int>() != jQueue->num_jfiles()) {
                queue->addArgument("qpid.file_count", jQueue->num_jfiles());
            }
            value = storeargs.get("qpid.file_size");
            if (value.get() != 0 && !value->empty() && value->convertsTo<int>() && (uint32_t)value->get<int>() != jQueue->jfsize_sblks()/JRNL_RMGR_PAGE_SIZE) {
                queue->addArgument("qpid.file_size", jQueue->jfsize_sblks()/JRNL_RMGR_PAGE_SIZE);
            }
*/

            if (highestRid == 0ULL)
                highestRid = thisHighestRid;
            else if (thisHighestRid - highestRid < 0x8000000000000000ULL) // RFC 1982 comparison for unsigned 64-bit
                highestRid = thisHighestRid;
            recoverMessages(txn, registry, queue, prepared, messages, rcnt, idcnt);
            QLS_LOG(info, "Recovered queue \"" << queueName << "\": " << rcnt << " messages recovered; " << idcnt << " messages in-doubt.");
            jQueue->recover_complete(); // start journal.
        } catch (const qpid::qls_jrnl::jexception& e) {
            THROW_STORE_EXCEPTION(std::string("Queue ") + queueName + ": recoverQueues() failed: " + e.what());
        }
        //read all messages: done on a per queue basis if using Journal

        queue_index[key.id] = queue;
        maxQueueId = std::max(key.id, maxQueueId);
    }

    // NOTE: highestRid is set by both recoverQueues() and recoverTplStore() as
    // the messageIdSequence is used for both queue journals and the tpl journal.
    messageIdSequence.reset(highestRid + 1);
    QLS_LOG(info, "Most recent persistence id found: 0x" << std::hex << highestRid << std::dec);

    queueIdSequence.reset(maxQueueId + 1);
}


void MessageStoreImpl::recoverExchanges(TxnCtxt& txn_,
                                        qpid::broker::RecoveryManager& registry_,
                                        exchange_index& index_)
{
    //TODO: this is a copy&paste from recoverQueues - refactor!
    Cursor exchanges;
    exchanges.open(exchangeDb, txn_.get());

    uint64_t maxExchangeId(1);
    IdDbt key;
    Dbt value;
    //read all exchanges
    while (exchanges.next(key, value)) {
        qpid::framing::Buffer buffer(reinterpret_cast<char*>(value.get_data()), value.get_size());
        //create a Exchange instance
        qpid::broker::RecoverableExchange::shared_ptr exchange = registry_.recoverExchange(buffer);
        if (exchange) {
            //set the persistenceId and update max as required
            exchange->setPersistenceId(key.id);
            index_[key.id] = exchange;
            QLS_LOG(info, "Recovered exchange \"" << exchange->getName() << '"');
        }
        maxExchangeId = std::max(key.id, maxExchangeId);
    }
    exchangeIdSequence.reset(maxExchangeId + 1);
}

void MessageStoreImpl::recoverBindings(TxnCtxt& txn_,
                                       exchange_index& exchanges_,
                                       queue_index& queues_)
{
    Cursor bindings;
    bindings.open(bindingDb, txn_.get());

    IdDbt key;
    Dbt value;
    while (bindings.next(key, value)) {
        qpid::framing::Buffer buffer(reinterpret_cast<char*>(value.get_data()), value.get_size());
        if (buffer.available() < 8) {
            QLS_LOG(error, "Not enough data for binding: " << buffer.available());
            THROW_STORE_EXCEPTION("Not enough data for binding");
        }
        uint64_t queueId = buffer.getLongLong();
        std::string queueName;
        std::string routingkey;
        qpid::framing::FieldTable args;
        buffer.getShortString(queueName);
        buffer.getShortString(routingkey);
        buffer.get(args);
        exchange_index::iterator exchange = exchanges_.find(key.id);
        queue_index::iterator queue = queues_.find(queueId);
        if (exchange != exchanges_.end() && queue != queues_.end()) {
            //could use the recoverable queue here rather than the name...
            exchange->second->bind(queueName, routingkey, args);
            QLS_LOG(info, "Recovered binding exchange=" << exchange->second->getName()
                     << " key=" << routingkey
                     << " queue=" << queueName);
        } else {
            //stale binding, delete it
            QLS_LOG(warning, "Deleting stale binding");
            bindings->del(0);
        }
    }
}

void MessageStoreImpl::recoverGeneral(TxnCtxt& txn_,
                                      qpid::broker::RecoveryManager& registry_)
{
    Cursor items;
    items.open(generalDb, txn_.get());

    uint64_t maxGeneralId(1);
    IdDbt key;
    Dbt value;
    //read all items
    while (items.next(key, value)) {
        qpid::framing::Buffer buffer(reinterpret_cast<char*>(value.get_data()), value.get_size());
        //create instance
        qpid::broker::RecoverableConfig::shared_ptr config = registry_.recoverConfig(buffer);
        //set the persistenceId and update max as required
        config->setPersistenceId(key.id);
        maxGeneralId = std::max(key.id, maxGeneralId);
    }
    generalIdSequence.reset(maxGeneralId + 1);
}

void MessageStoreImpl::recoverMessages(TxnCtxt& /*txn*/,
                                       qpid::broker::RecoveryManager& recovery,
                                       qpid::broker::RecoverableQueue::shared_ptr& queue,
                                       txn_list& prepared,
                                       message_index& messages,
                                       long& rcnt,
                                       long& idcnt)
{
    QLS_LOG(info,   "*** MessageStoreImpl::recoverMessages() queue=\"" << queue->getName() << "\"");
    size_t preambleLength = sizeof(uint32_t)/*header size*/;

    JournalImpl* jc = static_cast<JournalImpl*>(queue->getExternalQueueStore());
    unsigned msg_count = 0;

    // TODO: This optimization to skip reading if there are no enqueued messages to read
    // breaks the python system test in phase 6 with "Exception: Cannot write lock file"
    // Figure out what is breaking.
    //bool read = jc->get_enq_cnt() > 0;
    bool read = true;

    void* dbuff = NULL;
    size_t dbuffSize = 0;
    void* xidbuff = NULL;
    size_t xidbuffSize = 0;
    bool transientFlag = false;
    bool externalFlag = false;
    DataTokenImpl dtok;
    dtok.set_wstate(DataTokenImpl::NONE);

    // Read the message from the Journal.
    try {
        unsigned aio_sleep_cnt = 0;
        while (read) {
            qpid::qls_jrnl::iores res = jc->read_data_record(&dbuff, dbuffSize, &xidbuff, xidbuffSize, transientFlag, externalFlag, &dtok);

            switch (res)
            {
              case qpid::qls_jrnl::RHM_IORES_SUCCESS: {
                msg_count++;
                qpid::broker::RecoverableMessage::shared_ptr msg;
                char* data = (char*)dbuff;

                unsigned headerSize;
                if (externalFlag) {
                    msg = getExternMessage(recovery, dtok.rid(), headerSize); // large message external to jrnl
                } else {
                    headerSize = qpid::framing::Buffer(data, preambleLength).getLong();
                    qpid::framing::Buffer headerBuff(data+ preambleLength, headerSize); /// do we want read size or header size ????
                    msg = recovery.recoverMessage(headerBuff);
                }
                msg->setPersistenceId(dtok.rid());
                // At some future point if delivery attempts are stored, then this call would
                // become optional depending on that information.
                msg->setRedelivered();
                // Reset the TTL for the recovered message
                msg->computeExpiration(broker->getExpiryPolicy());

                uint32_t contentOffset = headerSize + preambleLength;
                uint64_t contentSize = dbuffSize - contentOffset;
                if (msg->loadContent(contentSize) && !externalFlag) {
                    //now read the content
                    qpid::framing::Buffer contentBuff(data + contentOffset, contentSize);
                    msg->decodeContent(contentBuff);
                }

                PreparedTransaction::list::iterator i = PreparedTransaction::getLockedPreparedTransaction(prepared, queue->getPersistenceId(), dtok.rid());
                if (i == prepared.end()) { // not in prepared list
                    rcnt++;
                    queue->recover(msg);
                } else {
                    uint64_t rid = dtok.rid();
                    std::string xid(i->xid);
                    TplRecoverMapCitr citr = tplRecoverMap.find(xid);
                    if (citr == tplRecoverMap.end()) THROW_STORE_EXCEPTION("XID not found in tplRecoverMap");

                    // deq present in prepared list: this xid is part of incomplete txn commit/abort
                    // or this is a 1PC txn that must be rolled forward
                    if (citr->second.deq_flag || !citr->second.tpc_flag) {
                        if (jc->is_enqueued(rid, true)) {
                            // Enqueue is non-tx, dequeue tx
                            assert(jc->is_locked(rid)); // This record MUST be locked by a txn dequeue
                            if (!citr->second.commit_flag) {
                                rcnt++;
                                queue->recover(msg); // recover message in abort case only
                            }
                        } else {
                            // Enqueue and/or dequeue tx
                            qpid::qls_jrnl::txn_map& tmap = jc->get_txn_map();
                            qpid::qls_jrnl::txn_data_list txnList = tmap.get_tdata_list(xid); // txnList will be empty if xid not found
                            bool enq = false;
                            bool deq = false;
                            for (qpid::qls_jrnl::tdl_itr j = txnList.begin(); j<txnList.end(); j++) {
                                if (j->enq_flag_ && j->rid_ == rid) enq = true;
                                else if (!j->enq_flag_ && j->drid_ == rid) deq = true;
                            }
                            if (enq && !deq && citr->second.commit_flag) {
                                rcnt++;
                                queue->recover(msg); // recover txn message in commit case only
                            }
                        }
                    } else {
                        idcnt++;
                        messages[rid] = msg;
                    }
                }

                dtok.reset();
                dtok.set_wstate(DataTokenImpl::NONE);

                if (xidbuff)
                    ::free(xidbuff);
                else if (dbuff)
                    ::free(dbuff);
                aio_sleep_cnt = 0;
                break;
              }
              case qpid::qls_jrnl::RHM_IORES_PAGE_AIOWAIT:
                if (++aio_sleep_cnt > MAX_AIO_SLEEPS)
                    THROW_STORE_EXCEPTION("Timeout waiting for AIO in MessageStoreImpl::recoverMessages()");
                ::usleep(AIO_SLEEP_TIME_US);
                break;
              case qpid::qls_jrnl::RHM_IORES_EMPTY:
                read = false;
                break; // done with all messages. (add call in jrnl to test that _emap is empty.)
              default:
                std::ostringstream oss;
                oss << "recoverMessages(): Queue: " << queue->getName() << ": Unexpected return from journal read: " << qpid::qls_jrnl::iores_str(res);
                THROW_STORE_EXCEPTION(oss.str());
            } // switch
        } // while
    } catch (const qpid::qls_jrnl::jexception& e) {
        THROW_STORE_EXCEPTION(std::string("Queue ") + queue->getName() + ": recoverMessages() failed: " + e.what());
    }
}

qpid::broker::RecoverableMessage::shared_ptr MessageStoreImpl::getExternMessage(qpid::broker::RecoveryManager& /*recovery*/,
                                                                                uint64_t /*messageId*/,
                                                                                unsigned& /*headerSize*/)
{
    throw qpid::qls_jrnl::jexception(qpid::qls_jrnl::jerrno::JERR__NOTIMPL, "MessageStoreImpl", "getExternMessage");
}

int MessageStoreImpl::enqueueMessage(TxnCtxt& txn_,
                                     IdDbt& msgId_,
                                     qpid::broker::RecoverableMessage::shared_ptr& msg_,
                                     queue_index& index_,
                                     txn_list& prepared_,
                                     message_index& messages_)
{
    Cursor mappings;
    mappings.open(mappingDb, txn_.get());

    IdDbt value;

    int count(0);
    for (int status = mappings->get(&msgId_, &value, DB_SET); status == 0; status = mappings->get(&msgId_, &value, DB_NEXT_DUP)) {
        if (index_.find(value.id) == index_.end()) {
            QLS_LOG(warning, "Recovered message for queue that no longer exists");
            mappings->del(0);
        } else {
            qpid::broker::RecoverableQueue::shared_ptr queue = index_[value.id];
            if (PreparedTransaction::isLocked(prepared_, value.id, msgId_.id)) {
                messages_[msgId_.id] = msg_;
            } else {
                queue->recover(msg_);
            }
            count++;
        }
    }
    mappings.close();
    return count;
}

void MessageStoreImpl::readTplStore()
{
    QLS_LOG(info,   "*** MessageStoreImpl::readTplStore()");
    tplRecoverMap.clear();
    qpid::qls_jrnl::txn_map& tmap = tplStorePtr->get_txn_map();
    DataTokenImpl dtok;
    void* dbuff = NULL; size_t dbuffSize = 0;
    void* xidbuff = NULL; size_t xidbuffSize = 0;
    bool transientFlag = false;
    bool externalFlag = false;
    bool done = false;
    try {
        unsigned aio_sleep_cnt = 0;
        while (!done) {
            dtok.reset();
            dtok.set_wstate(DataTokenImpl::ENQ);
            qpid::qls_jrnl::iores res = tplStorePtr->read_data_record(&dbuff, dbuffSize, &xidbuff, xidbuffSize, transientFlag, externalFlag, &dtok);
            switch (res) {
              case qpid::qls_jrnl::RHM_IORES_SUCCESS: {
                // Every TPL record contains both data and an XID
                assert(dbuffSize>0);
                assert(xidbuffSize>0);
                std::string xid(static_cast<const char*>(xidbuff), xidbuffSize);
                bool is2PC = *(static_cast<char*>(dbuff)) != 0;

                // Check transaction details; add to recover map
                qpid::qls_jrnl::txn_data_list txnList = tmap.get_tdata_list(xid); //  txnList will be empty if xid not found
                if (!txnList.empty()) { // xid found in tmap
                    unsigned enqCnt = 0;
                    unsigned deqCnt = 0;
                    uint64_t rid = 0;

                    // Assume commit (roll forward) in cases where only prepare has been called - ie only enqueue record exists.
                    // Note: will apply to both 1PC and 2PC transactions.
                    bool commitFlag = true;

                    for (qpid::qls_jrnl::tdl_itr j = txnList.begin(); j<txnList.end(); j++) {
                        if (j->enq_flag_) {
                            rid = j->rid_;
                            enqCnt++;
                        } else {
                            commitFlag = j->commit_flag_;
                            deqCnt++;
                        }
                    }
                    assert(enqCnt == 1);
                    assert(deqCnt <= 1);
                    tplRecoverMap.insert(TplRecoverMapPair(xid, TplRecoverStruct(rid, deqCnt == 1, commitFlag, is2PC)));
                }

                ::free(xidbuff);
                aio_sleep_cnt = 0;
                break;
                }
              case qpid::qls_jrnl::RHM_IORES_PAGE_AIOWAIT:
                if (++aio_sleep_cnt > MAX_AIO_SLEEPS)
                    THROW_STORE_EXCEPTION("Timeout waiting for AIO in MessageStoreImpl::recoverTplStore()");
                ::usleep(AIO_SLEEP_TIME_US);
                break;
              case qpid::qls_jrnl::RHM_IORES_EMPTY:
                done = true;
                break; // done with all messages. (add call in jrnl to test that _emap is empty.)
              default:
                std::ostringstream oss;
                oss << "readTplStore(): Unexpected result from journal read: " << qpid::qls_jrnl::iores_str(res);
                THROW_STORE_EXCEPTION(oss.str());
            } // switch
        }
    } catch (const qpid::qls_jrnl::jexception& e) {
        THROW_STORE_EXCEPTION(std::string("TPL recoverTplStore() failed: ") + e.what());
    }
}

void MessageStoreImpl::recoverTplStore()
{
    QLS_LOG(info,   "*** MessageStoreImpl::recoverTplStore()");
    if (qpid::qls_jrnl::jdir::exists(tplStorePtr->jrnl_dir())) {
        uint64_t thisHighestRid = 0ULL;
        tplStorePtr->recover(boost::dynamic_pointer_cast<qpid::qls_jrnl::EmptyFilePoolManager>(efpMgr), tplWCacheNumPages, tplWCachePgSizeSblks, 0, thisHighestRid, 0);
        if (highestRid == 0ULL)
            highestRid = thisHighestRid;
        else if (thisHighestRid - highestRid  < 0x8000000000000000ULL) // RFC 1982 comparison for unsigned 64-bit
            highestRid = thisHighestRid;

        // Load tplRecoverMap by reading the TPL store
        readTplStore();

        tplStorePtr->recover_complete(); // start journal.
    }
}

void MessageStoreImpl::recoverLockedMappings(txn_list& txns)
{
    QLS_LOG(info,   "*** MessageStoreImpl::recoverLockedMappings()");
    if (!tplStorePtr->is_ready())
        recoverTplStore();

    // Abort unprepared xids and populate the locked maps
    for (TplRecoverMapCitr i = tplRecoverMap.begin(); i != tplRecoverMap.end(); i++) {
        LockedMappings::shared_ptr enq_ptr;
        enq_ptr.reset(new LockedMappings);
        LockedMappings::shared_ptr deq_ptr;
        deq_ptr.reset(new LockedMappings);
        txns.push_back(new PreparedTransaction(i->first, enq_ptr, deq_ptr));
    }
}

void MessageStoreImpl::collectPreparedXids(std::set<std::string>& xids)
{
    QLS_LOG(info,   "*** MessageStoreImpl::collectPreparedXids()");
    if (tplStorePtr->is_ready()) {
        readTplStore();
    } else {
        recoverTplStore();
    }
    for (TplRecoverMapCitr i = tplRecoverMap.begin(); i != tplRecoverMap.end(); i++) {
        // Discard all txns that are to be rolled forward/back and 1PC transactions
        if (!i->second.deq_flag && i->second.tpc_flag)
            xids.insert(i->first);
    }
}

void MessageStoreImpl::stage(const boost::intrusive_ptr<qpid::broker::PersistableMessage>& /*msg*/)
{
    throw qpid::qls_jrnl::jexception(qpid::qls_jrnl::jerrno::JERR__NOTIMPL, "MessageStoreImpl", "stage");
}

void MessageStoreImpl::destroy(qpid::broker::PersistableMessage& /*msg*/)
{
    throw qpid::qls_jrnl::jexception(qpid::qls_jrnl::jerrno::JERR__NOTIMPL, "MessageStoreImpl", "destroy");
}

void MessageStoreImpl::appendContent(const boost::intrusive_ptr<const qpid::broker::PersistableMessage>& /*msg*/,
                                     const std::string& /*data*/)
{
    throw qpid::qls_jrnl::jexception(qpid::qls_jrnl::jerrno::JERR__NOTIMPL, "MessageStoreImpl", "appendContent");
}

void MessageStoreImpl::loadContent(const qpid::broker::PersistableQueue& /*queue*/,
                                   const boost::intrusive_ptr<const qpid::broker::PersistableMessage>& /*msg*/,
                                   std::string& /*data*/,
                                   uint64_t /*offset*/,
                                   uint32_t /*length*/)
{
    throw qpid::qls_jrnl::jexception(qpid::qls_jrnl::jerrno::JERR__NOTIMPL, "MessageStoreImpl", "loadContent");
}

void MessageStoreImpl::flush(const qpid::broker::PersistableQueue& queue_)
{
//    QLS_LOG(info,   "*** MessageStoreImpl::flush() queue=\"" << queue_.getName() << "\"");
    if (queue_.getExternalQueueStore() == 0) return;
    checkInit();
    std::string qn = queue_.getName();
    try {
        JournalImpl* jc = static_cast<JournalImpl*>(queue_.getExternalQueueStore());
        if (jc) {
            // TODO: check if this result should be used...
            /*mrg::journal::iores res =*/ jc->flush();
        }
    } catch (const qpid::qls_jrnl::jexception& e) {
        THROW_STORE_EXCEPTION(std::string("Queue ") + qn + ": flush() failed: " + e.what() );
    }
}

void MessageStoreImpl::enqueue(qpid::broker::TransactionContext* ctxt_,
                               const boost::intrusive_ptr<qpid::broker::PersistableMessage>& msg_,
                               const qpid::broker::PersistableQueue& queue_)
{
    //QLS_LOG(info,   "*** MessageStoreImpl::enqueue() queue=\"" << queue_.getName() << "\"");
    checkInit();
    uint64_t queueId (queue_.getPersistenceId());
    if (queueId == 0) {
        THROW_STORE_EXCEPTION("Queue not created: " + queue_.getName());
    }

    TxnCtxt implicit;
    TxnCtxt* txn = 0;
    if (ctxt_) {
        txn = check(ctxt_);
    } else {
        txn = &implicit;
    }

    if (msg_->getPersistenceId() == 0) {
        msg_->setPersistenceId(messageIdSequence.next());
    }
    store(&queue_, txn, msg_);

    // add queue* to the txn map..
    if (ctxt_) txn->addXidRecord(queue_.getExternalQueueStore());
}

uint64_t MessageStoreImpl::msgEncode(std::vector<char>& buff_,
                                     const boost::intrusive_ptr<qpid::broker::PersistableMessage>& message_)
{
    uint32_t headerSize = message_->encodedHeaderSize();
    uint64_t size = message_->encodedSize() + sizeof(uint32_t);
    try { buff_ = std::vector<char>(size); } // long + headers + content
    catch (const std::exception& e) {
        std::ostringstream oss;
        oss << "Unable to allocate memory for encoding message; requested size: " << size << "; error: " << e.what();
        THROW_STORE_EXCEPTION(oss.str());
    }
    qpid::framing::Buffer buffer(&buff_[0],size);
    buffer.putLong(headerSize);
    message_->encode(buffer);
    return size;
}

void MessageStoreImpl::store(const qpid::broker::PersistableQueue* queue_,
                             TxnCtxt* txn_,
                             const boost::intrusive_ptr<qpid::broker::PersistableMessage>& message_)
{
    //QLS_LOG(info,   "*** MessageStoreImpl::store() queue=\"" << queue_->getName() << "\"");
    std::vector<char> buff;
    uint64_t size = msgEncode(buff, message_);

    try {
        if (queue_) {
            boost::intrusive_ptr<DataTokenImpl> dtokp(new DataTokenImpl);
            dtokp->addRef();
            dtokp->setSourceMessage(message_);
            dtokp->set_external_rid(true);
            dtokp->set_rid(message_->getPersistenceId()); // set the messageID into the Journal header (record-id)

            JournalImpl* jc = static_cast<JournalImpl*>(queue_->getExternalQueueStore());
            if (txn_->getXid().empty()) {
                jc->enqueue_data_record(&buff[0], size, size, dtokp.get(), !message_->isPersistent());
            } else {
                jc->enqueue_txn_data_record(&buff[0], size, size, dtokp.get(), txn_->getXid(), !message_->isPersistent());
            }
        } else {
            THROW_STORE_EXCEPTION(std::string("MessageStoreImpl::store() failed: queue NULL."));
       }
    } catch (const qpid::qls_jrnl::jexception& e) {
        THROW_STORE_EXCEPTION(std::string("Queue ") + queue_->getName() + ": MessageStoreImpl::store() failed: " +
                              e.what());
    }
}

void MessageStoreImpl::dequeue(qpid::broker::TransactionContext* ctxt_,
                               const boost::intrusive_ptr<qpid::broker::PersistableMessage>& msg_,
                               const qpid::broker::PersistableQueue& queue_)
{
    //QLS_LOG(info,   "*** MessageStoreImpl::dequeue() queue=\"" << queue_.getName() << "\"");
    checkInit();
    uint64_t queueId (queue_.getPersistenceId());
    uint64_t messageId (msg_->getPersistenceId());
    if (queueId == 0) {
        THROW_STORE_EXCEPTION("Queue \"" + queue_.getName() + "\" has null queue Id (has not been created)");
    }
    if (messageId == 0) {
        THROW_STORE_EXCEPTION("Queue \"" + queue_.getName() + "\": Dequeuing message with null persistence Id.");
    }

    TxnCtxt implicit;
    TxnCtxt* txn = 0;
    if (ctxt_) {
        txn = check(ctxt_);
    } else {
        txn = &implicit;
    }

    // add queue* to the txn map..
    if (ctxt_) txn->addXidRecord(queue_.getExternalQueueStore());
    async_dequeue(ctxt_, msg_, queue_);
    msg_->dequeueComplete();
}

void MessageStoreImpl::async_dequeue(qpid::broker::TransactionContext* ctxt_,
                                     const boost::intrusive_ptr<qpid::broker::PersistableMessage>& msg_,
                                     const qpid::broker::PersistableQueue& queue_)
{
    //QLS_LOG(info,   "*** MessageStoreImpl::async_dequeue() queue=\"" << queue_.getName() << "\"");
    boost::intrusive_ptr<DataTokenImpl> ddtokp(new DataTokenImpl);
    ddtokp->setSourceMessage(msg_);
    ddtokp->set_external_rid(true);
    ddtokp->set_rid(messageIdSequence.next());
    ddtokp->set_dequeue_rid(msg_->getPersistenceId());
    ddtokp->set_wstate(DataTokenImpl::ENQ);
    std::string tid;
    if (ctxt_) {
        TxnCtxt* txn = check(ctxt_);
        tid = txn->getXid();
    }
    // Manually increase the ref count, as raw pointers are used beyond this point
    ddtokp->addRef();
    try {
        JournalImpl* jc = static_cast<JournalImpl*>(queue_.getExternalQueueStore());
        if (tid.empty()) {
            jc->dequeue_data_record(ddtokp.get());
        } else {
            jc->dequeue_txn_data_record(ddtokp.get(), tid);
        }
    } catch (const qpid::qls_jrnl::jexception& e) {
        ddtokp->release();
        THROW_STORE_EXCEPTION(std::string("Queue ") + queue_.getName() + ": async_dequeue() failed: " + e.what());
    }
}

void MessageStoreImpl::completed(TxnCtxt& txn_,
                                 bool commit_)
{
    try {
        chkTplStoreInit(); // Late initialize (if needed)

        // Nothing to do if not prepared
        if (txn_.getDtok()->is_enqueued()) {
            txn_.incrDtokRef();
            DataTokenImpl* dtokp = txn_.getDtok();
            dtokp->set_dequeue_rid(dtokp->rid());
            dtokp->set_rid(messageIdSequence.next());
            tplStorePtr->dequeue_txn_data_record(txn_.getDtok(), txn_.getXid(), commit_);
        }
        txn_.complete(commit_);
        if (mgmtObject.get() != 0) {
            mgmtObject->dec_tplTransactionDepth();
            if (commit_)
                mgmtObject->inc_tplTxnCommits();
            else
                mgmtObject->inc_tplTxnAborts();
        }
    } catch (const std::exception& e) {
        QLS_LOG(error, "Error completing xid " << txn_.getXid() << ": " << e.what());
        throw;
    }
}

std::auto_ptr<qpid::broker::TransactionContext> MessageStoreImpl::begin()
{
    checkInit();
    // pass sequence number for c/a
    return std::auto_ptr<qpid::broker::TransactionContext>(new TxnCtxt(&messageIdSequence));
}

std::auto_ptr<qpid::broker::TPCTransactionContext> MessageStoreImpl::begin(const std::string& xid_)
{
    checkInit();
    IdSequence* jtx = &messageIdSequence;
    // pass sequence number for c/a
    return std::auto_ptr<qpid::broker::TPCTransactionContext>(new TPCTxnCtxt(xid_, jtx));
}

void MessageStoreImpl::prepare(qpid::broker::TPCTransactionContext& ctxt_)
{
    checkInit();
    TxnCtxt* txn = dynamic_cast<TxnCtxt*>(&ctxt_);
    if(!txn) throw qpid::broker::InvalidTransactionContextException();
    localPrepare(txn);
}

void MessageStoreImpl::localPrepare(TxnCtxt* ctxt_)
{
    try {
        chkTplStoreInit(); // Late initialize (if needed)

        // This sync is required to ensure multi-queue atomicity - ie all txn data
        // must hit the disk on *all* queues before the TPL prepare (enq) is written.
        ctxt_->sync();

        ctxt_->incrDtokRef();
        DataTokenImpl* dtokp = ctxt_->getDtok();
        dtokp->set_external_rid(true);
        dtokp->set_rid(messageIdSequence.next());
        char tpcFlag = static_cast<char>(ctxt_->isTPC());
        tplStorePtr->enqueue_txn_data_record(&tpcFlag, sizeof(char), sizeof(char), dtokp, ctxt_->getXid(), false);
        ctxt_->prepare(tplStorePtr.get());
        // make sure all the data is written to disk before returning
        ctxt_->sync();
        if (mgmtObject.get() != 0) {
            mgmtObject->inc_tplTransactionDepth();
            mgmtObject->inc_tplTxnPrepares();
        }
    } catch (const std::exception& e) {
        QLS_LOG(error, "Error preparing xid " << ctxt_->getXid() << ": " << e.what());
        throw;
    }
}

void MessageStoreImpl::commit(qpid::broker::TransactionContext& ctxt_)
{
    checkInit();
    TxnCtxt* txn(check(&ctxt_));
    if (!txn->isTPC()) {
        if (txn->impactedQueuesEmpty()) return;
        localPrepare(dynamic_cast<TxnCtxt*>(txn));
    }
    completed(*dynamic_cast<TxnCtxt*>(txn), true);
}

void MessageStoreImpl::abort(qpid::broker::TransactionContext& ctxt_)
{
    checkInit();
    TxnCtxt* txn(check(&ctxt_));
    if (!txn->isTPC()) {
        if (txn->impactedQueuesEmpty()) return;
        localPrepare(dynamic_cast<TxnCtxt*>(txn));
    }
    completed(*dynamic_cast<TxnCtxt*>(txn), false);
}

TxnCtxt* MessageStoreImpl::check(qpid::broker::TransactionContext* ctxt_)
{
    TxnCtxt* txn = dynamic_cast<TxnCtxt*>(ctxt_);
    if(!txn) throw qpid::broker::InvalidTransactionContextException();
    return txn;
}

void MessageStoreImpl::put(db_ptr db_,
                           DbTxn* txn_,
                           Dbt& key_,
                           Dbt& value_)
{
    try {
        int status = db_->put(txn_, &key_, &value_, DB_NODUPDATA);
        if (status == DB_KEYEXIST) {
            THROW_STORE_EXCEPTION("duplicate data");
        } else if (status) {
            THROW_STORE_EXCEPTION(DbEnv::strerror(status));
        }
    } catch (const DbException& e) {
        THROW_STORE_EXCEPTION(e.what());
    }
}

void MessageStoreImpl::deleteBindingsForQueue(const qpid::broker::PersistableQueue& queue_)
{
    TxnCtxt txn;
    txn.begin(dbenv.get(), true);
    try {
        {
            Cursor bindings;
            bindings.open(bindingDb, txn.get());

            IdDbt key;
            Dbt value;
            while (bindings.next(key, value)) {
                qpid::framing::Buffer buffer(reinterpret_cast<char*>(value.get_data()), value.get_size());
                if (buffer.available() < 8) {
                    THROW_STORE_EXCEPTION("Not enough data for binding");
                }
                uint64_t queueId = buffer.getLongLong();
                if (queue_.getPersistenceId() == queueId) {
                    bindings->del(0);
                    QLS_LOG(debug, "Deleting binding for " << queue_.getName() << " " << key.id << "->" << queueId);
                }
            }
        }
        txn.commit();
    } catch (const std::exception& e) {
        txn.abort();
        THROW_STORE_EXCEPTION_2("Error deleting bindings", e.what());
    } catch (...) {
        txn.abort();
        throw;
    }
    QLS_LOG(debug, "Deleted all bindings for " << queue_.getName() << ":" << queue_.getPersistenceId());
}

void MessageStoreImpl::deleteBinding(const qpid::broker::PersistableExchange& exchange_,
                                     const qpid::broker::PersistableQueue& queue_,
                                     const std::string& bkey_)
{
    TxnCtxt txn;
    txn.begin(dbenv.get(), true);
    try {
        {
            Cursor bindings;
            bindings.open(bindingDb, txn.get());

            IdDbt key(exchange_.getPersistenceId());
            Dbt value;

            for (int status = bindings->get(&key, &value, DB_SET); status == 0; status = bindings->get(&key, &value, DB_NEXT_DUP)) {
                qpid::framing::Buffer buffer(reinterpret_cast<char*>(value.get_data()), value.get_size());
                if (buffer.available() < 8) {
                    THROW_STORE_EXCEPTION("Not enough data for binding");
                }
                uint64_t queueId = buffer.getLongLong();
                if (queue_.getPersistenceId() == queueId) {
                    std::string q;
                    std::string k;
                    buffer.getShortString(q);
                    buffer.getShortString(k);
                    if (bkey_ == k) {
                        bindings->del(0);
                        QLS_LOG(debug, "Deleting binding for " << queue_.getName() << " " << key.id << "->" << queueId);
                    }
                }
            }
        }
        txn.commit();
    } catch (const std::exception& e) {
        txn.abort();
        THROW_STORE_EXCEPTION_2("Error deleting bindings", e.what());
    } catch (...) {
        txn.abort();
        throw;
    }
}

std::string MessageStoreImpl::getStoreTopLevelDir() {
    std::ostringstream dir;
    dir << storeDir << "/" << storeTopLevelDir;
    return dir.str();
}


std::string MessageStoreImpl::getJrnlBaseDir()
{
    std::ostringstream dir;
    dir << storeDir << "/" << storeTopLevelDir << "/jrnl/" ;
    return dir.str();
}

std::string MessageStoreImpl::getBdbBaseDir()
{
    std::ostringstream dir;
    dir << storeDir << "/" << storeTopLevelDir << "/dat/" ;
    return dir.str();
}

std::string MessageStoreImpl::getTplBaseDir()
{
    std::ostringstream dir;
    dir << storeDir << "/" << storeTopLevelDir << "/tpl/" ;
    return dir.str();
}

std::string MessageStoreImpl::getJrnlDir(const std::string& queueName_)
{
    std::ostringstream oss;
    oss << getJrnlBaseDir() << queueName_;
    return oss.str();
}

std::string MessageStoreImpl::getStoreDir() const { return storeDir; }

void MessageStoreImpl::journalDeleted(JournalImpl& j_) {
    qpid::sys::Mutex::ScopedLock sl(journalListLock);
    journalList.erase(j_.id());
}

MessageStoreImpl::StoreOptions::StoreOptions(const std::string& name_) :
                                             qpid::Options(name_),
                                             truncateFlag(defTruncateFlag),
                                             wCachePageSizeKib(defWCachePageSizeKib),
                                             tplWCachePageSizeKib(defTplWCachePageSizeKib),
                                             efpPartition(defEfpPartition),
                                             efpFileSizeKib(defEfpFileSizeKib)
{
    addOptions()
        ("store-dir", qpid::optValue(storeDir, "DIR"),
                "Store directory location for persistence (instead of using --data-dir value). "
                "Required if --no-data-dir is also used.")
        ("truncate", qpid::optValue(truncateFlag, "yes|no"),
                "If yes|true|1, will truncate the store (discard any existing records). If no|false|0, will preserve "
                "the existing store files for recovery.")
        ("wcache-page-size", qpid::optValue(wCachePageSizeKib, "N"),
                "Size of the pages in the write page cache in KiB. "
                "Allowable values - powers of 2: 1, 2, 4, ... , 128. "
                "Lower values decrease latency at the expense of throughput.")
        ("tpl-wcache-page-size", qpid::optValue(tplWCachePageSizeKib, "N"),
                "Size of the pages in the transaction prepared list write page cache in KiB. "
                "Allowable values - powers of 2: 1, 2, 4, ... , 128. "
                "Lower values decrease latency at the expense of throughput.")
        ("efp-partition", qpid::optValue(efpPartition, "N"),
                "Empty File Pool partition to use for finding empty journal files")
        ("efp-file-size", qpid::optValue(efpFileSizeKib, "N"),
                "Empty File Pool file size in KiB to use for journal files. Must be a multiple of 4 KiB.")
        ;
}

}}
