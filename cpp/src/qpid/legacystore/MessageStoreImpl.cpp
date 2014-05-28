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

#include "qpid/legacystore/MessageStoreImpl.h"

#include "db-inc.h"
#include "qpid/broker/QueueSettings.h"
#include "qpid/legacystore/BindingDbt.h"
#include "qpid/legacystore/BufferValue.h"
#include "qpid/legacystore/IdDbt.h"
#include "qpid/legacystore/jrnl/txn_map.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/log/Statement.h"
#include "qmf/org/apache/qpid/legacystore/Package.h"
#include "qpid/legacystore/StoreException.h"
#include <dirent.h>

#define MAX_AIO_SLEEPS 100000 // tot: ~1 sec
#define AIO_SLEEP_TIME_US  10 // 0.01 ms

namespace _qmf = qmf::org::apache::qpid::legacystore;

namespace mrg {
namespace msgstore {


const std::string MessageStoreImpl::storeTopLevelDir("rhm"); // Sets the top-level store dir name
// FIXME aconway 2010-03-09: was 10
qpid::sys::Duration MessageStoreImpl::defJournalGetEventsTimeout(1 * qpid::sys::TIME_MSEC); // 10ms
qpid::sys::Duration MessageStoreImpl::defJournalFlushTimeout(500 * qpid::sys::TIME_MSEC); // 0.5s
qpid::sys::Mutex TxnCtxt::globalSerialiser;

MessageStoreImpl::TplRecoverStruct::TplRecoverStruct(const u_int64_t _rid,
                                                     const bool _deq_flag,
                                                     const bool _commit_flag,
                                                     const bool _tpc_flag) :
                                                     rid(_rid),
                                                     deq_flag(_deq_flag),
                                                     commit_flag(_commit_flag),
                                                     tpc_flag(_tpc_flag)
{}

MessageStoreImpl::MessageStoreImpl(qpid::broker::Broker* broker_, const char* envpath) :
                                   numJrnlFiles(0),
                                   autoJrnlExpand(false),
                                   autoJrnlExpandMaxFiles(0),
                                   jrnlFsizeSblks(0),
                                   truncateFlag(false),
                                   wCachePgSizeSblks(0),
                                   wCacheNumPages(0),
                                   tplNumJrnlFiles(0),
                                   tplJrnlFsizeSblks(0),
                                   tplWCachePgSizeSblks(0),
                                   tplWCacheNumPages(0),
                                   highestRid(0),
                                   isInit(false),
                                   envPath(envpath),
                                   broker(broker_),
                                   mgmtObject(),
                                   agent(0)
{}

u_int16_t MessageStoreImpl::chkJrnlNumFilesParam(const u_int16_t param, const std::string paramName)
{
    if (param < JRNL_MIN_NUM_FILES || param > JRNL_MAX_NUM_FILES) {
        std::ostringstream oss;
        oss << "Parameter " << paramName << ": Illegal number of store journal files (" << param << "), must be " << JRNL_MIN_NUM_FILES << " to " << JRNL_MAX_NUM_FILES << " inclusive.";
        THROW_STORE_EXCEPTION(oss.str());
    }
    return param;
}

u_int32_t MessageStoreImpl::chkJrnlFileSizeParam(const u_int32_t param, const std::string paramName, const u_int32_t wCachePgSizeSblks)
{
    if (param < (JRNL_MIN_FILE_SIZE / JRNL_RMGR_PAGE_SIZE) || (param > JRNL_MAX_FILE_SIZE / JRNL_RMGR_PAGE_SIZE)) {
        std::ostringstream oss;
        oss << "Parameter " << paramName << ": Illegal store journal file size (" << param << "), must be " << JRNL_MIN_FILE_SIZE / JRNL_RMGR_PAGE_SIZE << " to " << JRNL_MAX_FILE_SIZE / JRNL_RMGR_PAGE_SIZE << " inclusive.";
        THROW_STORE_EXCEPTION(oss.str());
    }
    if (wCachePgSizeSblks > param * JRNL_RMGR_PAGE_SIZE) {
        std::ostringstream oss;
        oss << "Cannot create store with file size less than write page cache size. [file size = " << param << " (" << (param * JRNL_RMGR_PAGE_SIZE / 2) << " kB); write page cache = " << (wCachePgSizeSblks / 2) << " kB]";
        THROW_STORE_EXCEPTION(oss.str());
    }
    return param;
}

u_int32_t MessageStoreImpl::chkJrnlWrPageCacheSize(const u_int32_t param, const std::string paramName, const u_int16_t jrnlFsizePgs)
{
    u_int32_t p = param;

    if (jrnlFsizePgs == 1 && p > 64 ) {
        p =  64;
        QPID_LOG(warning, "parameter " << paramName << " (" << param << ") cannot set a page size greater than the journal file size; changing this parameter to the journal file size (" << p << ")");
    }
    else if (p == 0) {
        // For zero value, use default
        p = JRNL_WMGR_DEF_PAGE_SIZE * JRNL_DBLK_SIZE * JRNL_SBLK_SIZE / 1024;
        QPID_LOG(warning, "parameter " << paramName << " (" << param << ") must be a power of 2 between 1 and 128; changing this parameter to default value (" << p << ")");
    } else if ( p > 128 || (p & (p-1)) ) {
        // For any positive value that is not a power of 2, use closest value
        if      (p <   6)   p =   4;
        else if (p <  12)   p =   8;
        else if (p <  24)   p =  16;
        else if (p <  48)   p =  32;
        else if (p <  96)   p =  64;
        else                p = 128;
        QPID_LOG(warning, "parameter " << paramName << " (" << param << ") must be a power of 2 between 1 and 128; changing this parameter to closest allowable value (" << p << ")");
    }
    return p;
}

u_int16_t MessageStoreImpl::getJrnlWrNumPages(const u_int32_t wrPageSizeKib)
{
    u_int32_t wrPageSizeSblks = wrPageSizeKib * 1024 / JRNL_DBLK_SIZE / JRNL_SBLK_SIZE; // convert from KiB to number sblks
    u_int32_t defTotWCacheSize = JRNL_WMGR_DEF_PAGE_SIZE * JRNL_WMGR_DEF_PAGES; // in sblks. Currently 2014 sblks (1 MiB).
    switch (wrPageSizeKib)
    {
      case 1:
      case 2:
      case 4:
        // 256 KiB total cache
        return defTotWCacheSize / wrPageSizeSblks / 4;
      case 8:
      case 16:
        // 512 KiB total cache
        return defTotWCacheSize / wrPageSizeSblks / 2;
      default: // 32, 64, 128
        // 1 MiB total cache
        return defTotWCacheSize / wrPageSizeSblks;
    }
}

void MessageStoreImpl::chkJrnlAutoExpandOptions(const StoreOptions* opts,
                                                bool& autoJrnlExpand,
                                                u_int16_t& autoJrnlExpandMaxFiles,
                                                const std::string& autoJrnlExpandMaxFilesParamName,
                                                const u_int16_t numJrnlFiles,
                                                const std::string& numJrnlFilesParamName)
{
    if (!opts->autoJrnlExpand) {
        // auto-expand disabled
        autoJrnlExpand = false;
        autoJrnlExpandMaxFiles = 0;
        return;
    }
    u_int16_t p = opts->autoJrnlExpandMaxFiles;
    if (numJrnlFiles == JRNL_MAX_NUM_FILES) {
        // num-jfiles at max; disable auto-expand
        autoJrnlExpand = false;
        autoJrnlExpandMaxFiles = 0;
        QPID_LOG(warning, "parameter " << autoJrnlExpandMaxFilesParamName << " (" << p << ") must be higher than parameter "
                << numJrnlFilesParamName << " (" << numJrnlFiles << ") which is at the maximum allowable value; disabling auto-expand.");
        return;
    }
    if (p > JRNL_MAX_NUM_FILES) {
        // auto-expand-max-jfiles higher than max allowable, adjust
        autoJrnlExpand = true;
        autoJrnlExpandMaxFiles = JRNL_MAX_NUM_FILES;
        QPID_LOG(warning, "parameter " << autoJrnlExpandMaxFilesParamName << " (" << p << ") is above allowable maximum ("
                << JRNL_MAX_NUM_FILES << "); changing this parameter to maximum value.");
        return;
    }
    if (p && p == defAutoJrnlExpandMaxFiles && numJrnlFiles != defTplNumJrnlFiles) {
        // num-jfiles is different from the default AND max-auto-expand-jfiles is still at default
        // change value of max-auto-expand-jfiles
        autoJrnlExpand = true;
        if (2 * numJrnlFiles <= JRNL_MAX_NUM_FILES) {
            autoJrnlExpandMaxFiles = 2 * numJrnlFiles <= JRNL_MAX_NUM_FILES ? 2 * numJrnlFiles : JRNL_MAX_NUM_FILES;
            QPID_LOG(warning, "parameter " << autoJrnlExpandMaxFilesParamName << " adjusted from its default value ("
                    << defAutoJrnlExpandMaxFiles << ") to twice that of parameter " << numJrnlFilesParamName << " (" << autoJrnlExpandMaxFiles << ").");
        } else {
            autoJrnlExpandMaxFiles = 2 * numJrnlFiles <= JRNL_MAX_NUM_FILES ? 2 * numJrnlFiles : JRNL_MAX_NUM_FILES;
            QPID_LOG(warning, "parameter " << autoJrnlExpandMaxFilesParamName << " adjusted from its default to maximum allowable value ("
                    << JRNL_MAX_NUM_FILES << ") because of the value of " << numJrnlFilesParamName << " (" << numJrnlFiles << ").");
        }
        return;
    }
    // No adjustments req'd, set values
    autoJrnlExpand = true;
    autoJrnlExpandMaxFiles = p;
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
            mgmtObject->set_defaultInitialFileCount(numJrnlFiles);
            mgmtObject->set_defaultDataFileSize(jrnlFsizeSblks / JRNL_RMGR_PAGE_SIZE);
            mgmtObject->set_tplIsInitialized(false);
            mgmtObject->set_tplDirectory(getTplBaseDir());
            mgmtObject->set_tplWritePageSize(tplWCachePgSizeSblks * JRNL_SBLK_SIZE * JRNL_DBLK_SIZE);
            mgmtObject->set_tplWritePages(tplWCacheNumPages);
            mgmtObject->set_tplInitialFileCount(tplNumJrnlFiles);
            mgmtObject->set_tplDataFileSize(tplJrnlFsizeSblks * JRNL_SBLK_SIZE * JRNL_DBLK_SIZE);
            mgmtObject->set_tplCurrentFileCount(tplNumJrnlFiles);

            agent->addObject(mgmtObject, 0, true);

            // Initialize all existing queues (ie those recovered before management was initialized)
            for (JournalListMapItr i=journalList.begin(); i!=journalList.end(); i++) {
                i->second->initManagement(agent);
            }
        }
    }
}

bool MessageStoreImpl::init(const qpid::Options* options)
{
    // Extract and check options
    const StoreOptions* opts = static_cast<const StoreOptions*>(options);
    u_int16_t numJrnlFiles = chkJrnlNumFilesParam(opts->numJrnlFiles, "num-jfiles");
    u_int32_t jrnlFsizePgs = chkJrnlFileSizeParam(opts->jrnlFsizePgs, "jfile-size-pgs");
    u_int32_t jrnlWrCachePageSizeKib = chkJrnlWrPageCacheSize(opts->wCachePageSizeKib, "wcache-page-size", jrnlFsizePgs);
    u_int16_t tplNumJrnlFiles = chkJrnlNumFilesParam(opts->tplNumJrnlFiles, "tpl-num-jfiles");
    u_int32_t tplJrnlFSizePgs = chkJrnlFileSizeParam(opts->tplJrnlFsizePgs, "tpl-jfile-size-pgs");
    u_int32_t tplJrnlWrCachePageSizeKib = chkJrnlWrPageCacheSize(opts->tplWCachePageSizeKib, "tpl-wcache-page-size", tplJrnlFSizePgs);
    bool      autoJrnlExpand;
    u_int16_t autoJrnlExpandMaxFiles;
    chkJrnlAutoExpandOptions(opts, autoJrnlExpand, autoJrnlExpandMaxFiles, "auto-expand-max-jfiles", numJrnlFiles, "num-jfiles");

    // Pass option values to init(...)
    return init(opts->storeDir, numJrnlFiles, jrnlFsizePgs, opts->truncateFlag, jrnlWrCachePageSizeKib, tplNumJrnlFiles, tplJrnlFSizePgs, tplJrnlWrCachePageSizeKib, autoJrnlExpand, autoJrnlExpandMaxFiles);
}

// These params, taken from options, are assumed to be correct and verified
bool MessageStoreImpl::init(const std::string& dir,
                           u_int16_t jfiles,
                           u_int32_t jfileSizePgs,
                           const bool truncateFlag,
                           u_int32_t wCachePageSizeKib,
                           u_int16_t tplJfiles,
                           u_int32_t tplJfileSizePgs,
                           u_int32_t tplWCachePageSizeKib,
                           bool      autoJExpand,
                           u_int16_t autoJExpandMaxFiles)
{
    if (isInit) return true;

    // Set geometry members (converting to correct units where req'd)
    numJrnlFiles = jfiles;
    jrnlFsizeSblks = jfileSizePgs * JRNL_RMGR_PAGE_SIZE;
    wCachePgSizeSblks = wCachePageSizeKib * 1024 / JRNL_DBLK_SIZE / JRNL_SBLK_SIZE; // convert from KiB to number sblks
    wCacheNumPages = getJrnlWrNumPages(wCachePageSizeKib);
    tplNumJrnlFiles = tplJfiles;
    tplJrnlFsizeSblks = tplJfileSizePgs * JRNL_RMGR_PAGE_SIZE;
    tplWCachePgSizeSblks = tplWCachePageSizeKib * 1024 / JRNL_DBLK_SIZE / JRNL_SBLK_SIZE; // convert from KiB to number sblks
    tplWCacheNumPages = getJrnlWrNumPages(tplWCachePageSizeKib);
    autoJrnlExpand = autoJExpand;
    autoJrnlExpandMaxFiles = autoJExpandMaxFiles;
    if (dir.size()>0) storeDir = dir;

    if (truncateFlag)
        truncateInit(false);
    else
        init();

    QPID_LOG(notice, "Store module initialized; store-dir=" << dir);
    QPID_LOG(info,   "> Default files per journal: " << jfiles);
// TODO: Uncomment these lines when auto-expand is enabled.
//     QPID_LOG(info,   "> Auto-expand " << (autoJrnlExpand ? "enabled" : "disabled"));
//     if (autoJrnlExpand) QPID_LOG(info,   "> Max auto-expand journal files: " << autoJrnlExpandMaxFiles);
    QPID_LOG(info,   "> Default journal file size: " << jfileSizePgs << " (wpgs)");
    QPID_LOG(info,   "> Default write cache page size: " << wCachePageSizeKib << " (KiB)");
    QPID_LOG(info,   "> Default number of write cache pages: " << wCacheNumPages);
    QPID_LOG(info,   "> TPL files per journal: " << tplNumJrnlFiles);
    QPID_LOG(info,   "> TPL journal file size: " << tplJfileSizePgs << " (wpgs)");
    QPID_LOG(info,   "> TPL write cache page size: " << tplWCachePageSizeKib << " (KiB)");
    QPID_LOG(info,   "> TPL number of write cache pages: " << tplWCacheNumPages);

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
            QPID_LOG(error, "Previoius BDB store initialization failed, retrying (" << bdbRetryCnt << " of " << retryMax << ")...");
        }

        try {
            journal::jdir::create_dir(getBdbBaseDir());

            dbenv.reset(new DbEnv(0));
            dbenv->set_errpfx("msgstore");
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
            tplStorePtr.reset(new TplJournalImpl(broker->getTimer(), "TplStore", getTplBaseDir(), "tpl", defJournalGetEventsTimeout, defJournalFlushTimeout, 0));
            isInit = true;
        } catch (const DbException& e) {
            if (e.get_errno() == DB_VERSION_MISMATCH)
            {
                QPID_LOG(error, "Database environment mismatch: This version of db4 does not match that which created the store database.: " << e.what());
                THROW_STORE_EXCEPTION_2("Database environment mismatch: This version of db4 does not match that which created the store database. "
                                        "(If recovery is not important, delete the contents of the store directory. Otherwise, try upgrading the database using "
                                        "db_upgrade or using db_recover - but the db4-utils package must also be installed to use these utilities.)", e);
            }
            QPID_LOG(error, "BDB exception occurred while initializing store: " << e.what());
            if (bdbRetryCnt >= retryMax)
                THROW_STORE_EXCEPTION_2("BDB exception occurred while initializing store", e);
        } catch (const StoreException&) {
            throw;
        } catch (const journal::jexception& e) {
            QPID_LOG(error, "Journal Exception occurred while initializing store: " << e);
            THROW_STORE_EXCEPTION_2("Journal Exception occurred while initializing store", e.what());
        } catch (...) {
            QPID_LOG(error, "Unknown exception occurred while initializing store.");
            throw;
        }
    } while (!isInit);
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

void MessageStoreImpl::truncateInit(const bool saveStoreContent)
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
    std::ostringstream oss;
    oss << storeDir << "/" << storeTopLevelDir;
    if (saveStoreContent) {
        std::string dir = mrg::journal::jdir::push_down(storeDir, storeTopLevelDir, "cluster");
        QPID_LOG(notice, "Store directory " << oss.str() << " was pushed down (saved) into directory " << dir << ".");
    } else {
        mrg::journal::jdir::delete_dir(oss.str().c_str());
        QPID_LOG(notice, "Store directory " << oss.str() << " was truncated.");
    }
    init();
}

void MessageStoreImpl::chkTplStoreInit()
{
    // Prevent multiple threads from late-initializing the TPL
    qpid::sys::Mutex::ScopedLock sl(tplInitLock);
    if (!tplStorePtr->is_ready()) {
        journal::jdir::create_dir(getTplBaseDir());
        tplStorePtr->initialize(tplNumJrnlFiles, false, 0, tplJrnlFsizeSblks, tplWCacheNumPages, tplWCachePgSizeSblks);
        if (mgmtObject.get() != 0) mgmtObject->set_tplIsInitialized(true);
    }
}

void MessageStoreImpl::open(db_ptr db,
                           DbTxn* txn,
                           const char* file,
                           bool dupKey)
{
    if(dupKey) db->set_flags(DB_DUPSORT);
    db->open(txn, file, 0, DB_BTREE, DB_CREATE | DB_THREAD, 0);
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
    if (mgmtObject.get() != 0)
        mgmtObject->debugStats("destroying");
    finalize();
    try {
        closeDbs();
    } catch (const DbException& e) {
        QPID_LOG(error, "Error closing BDB databases: " <<  e.what());
    } catch (const journal::jexception& e) {
        QPID_LOG(error, "Error: " << e.what());
    } catch (const std::exception& e) {
        QPID_LOG(error, "Error: " << e.what());
    } catch (...) {
        QPID_LOG(error, "Unknown error in MessageStoreImpl::~MessageStoreImpl()");
    }

    if (mgmtObject.get() != 0) {
        mgmtObject->resourceDestroy();
	mgmtObject.reset();
    }
}

void MessageStoreImpl::create(qpid::broker::PersistableQueue& queue,
                             const qpid::framing::FieldTable& args)
{
    checkInit();
    if (queue.getPersistenceId()) {
        THROW_STORE_EXCEPTION("Queue already created: " + queue.getName());
    }
    JournalImpl* jQueue = 0;
    qpid::framing::FieldTable::ValuePtr value;

    u_int16_t localFileCount = numJrnlFiles;
    bool      localAutoExpandFlag = autoJrnlExpand;
    u_int16_t localAutoExpandMaxFileCount = autoJrnlExpandMaxFiles;
    u_int32_t localFileSizeSblks  = jrnlFsizeSblks;

    value = args.get("qpid.file_count");
    if (value.get() != 0 && !value->empty() && value->convertsTo<int>())
        localFileCount = chkJrnlNumFilesParam((u_int16_t) value->get<int>(), "qpid.file_count");

    value = args.get("qpid.file_size");
    if (value.get() != 0 && !value->empty() && value->convertsTo<int>())
        localFileSizeSblks = chkJrnlFileSizeParam((u_int32_t) value->get<int>(), "qpid.file_size", wCachePgSizeSblks) * JRNL_RMGR_PAGE_SIZE;

    if (queue.getName().size() == 0)
    {
        QPID_LOG(error, "Cannot create store for empty (null) queue name - ignoring and attempting to continue.");
        return;
    }

    jQueue = new JournalImpl(broker->getTimer(), queue.getName(), getJrnlDir(queue),  std::string("JournalData"),
                             defJournalGetEventsTimeout, defJournalFlushTimeout, agent,
                             boost::bind(&MessageStoreImpl::journalDeleted, this, _1));
    {
        qpid::sys::Mutex::ScopedLock sl(journalListLock);
        journalList[queue.getName()]=jQueue;
    }

    value = args.get("qpid.auto_expand");
    if (value.get() != 0 && !value->empty() && value->convertsTo<bool>())
        localAutoExpandFlag = (bool) value->get<bool>();

    value = args.get("qpid.auto_expand_max_jfiles");
    if (value.get() != 0 && !value->empty() && value->convertsTo<int>())
        localAutoExpandMaxFileCount = (u_int16_t) value->get<int>();

    queue.setExternalQueueStore(dynamic_cast<qpid::broker::ExternalQueueStore*>(jQueue));
    try {
        // init will create the deque's for the init...
        jQueue->initialize(localFileCount, localAutoExpandFlag, localAutoExpandMaxFileCount, localFileSizeSblks, wCacheNumPages, wCachePgSizeSblks);
    } catch (const journal::jexception& e) {
        THROW_STORE_EXCEPTION(std::string("Queue ") + queue.getName() + ": create() failed: " + e.what());
    }
    try {
        if (!create(queueDb, queueIdSequence, queue)) {
            THROW_STORE_EXCEPTION("Queue already exists: " + queue.getName());
        }
    } catch (const DbException& e) {
        THROW_STORE_EXCEPTION_2("Error creating queue named  " + queue.getName(), e);
    }
}

void MessageStoreImpl::destroy(qpid::broker::PersistableQueue& queue)
{
    checkInit();
    destroy(queueDb, queue);
    deleteBindingsForQueue(queue);
    qpid::broker::ExternalQueueStore* eqs = queue.getExternalQueueStore();
    if (eqs) {
        JournalImpl* jQueue = static_cast<JournalImpl*>(eqs);
        jQueue->delete_jrnl_files();
        queue.setExternalQueueStore(0); // will delete the journal if exists
        {
            qpid::sys::Mutex::ScopedLock sl(journalListLock);
            journalList.erase(queue.getName());
        }
    }
}

void MessageStoreImpl::create(const qpid::broker::PersistableExchange& exchange,
                             const qpid::framing::FieldTable& /*args*/)
{
    checkInit();
    if (exchange.getPersistenceId()) {
        THROW_STORE_EXCEPTION("Exchange already created: " + exchange.getName());
    }
    try {
        if (!create(exchangeDb, exchangeIdSequence, exchange)) {
            THROW_STORE_EXCEPTION("Exchange already exists: " + exchange.getName());
        }
    } catch (const DbException& e) {
        THROW_STORE_EXCEPTION_2("Error creating exchange named " + exchange.getName(), e);
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

void MessageStoreImpl::create(const qpid::broker::PersistableConfig& general)
{
    checkInit();
    if (general.getPersistenceId()) {
        THROW_STORE_EXCEPTION("General configuration item already created");
    }
    try {
        if (!create(generalDb, generalIdSequence, general)) {
            THROW_STORE_EXCEPTION("General configuration already exists");
        }
    } catch (const DbException& e) {
        THROW_STORE_EXCEPTION_2("Error creating general configuration", e);
    }
}

void MessageStoreImpl::destroy(const qpid::broker::PersistableConfig& general)
{
    checkInit();
    destroy(generalDb, general);
}

bool MessageStoreImpl::create(db_ptr db,
                             IdSequence& seq,
                             const qpid::broker::Persistable& p)
{
    u_int64_t id (seq.next());
    Dbt key(&id, sizeof(id));
    BufferValue value (p);

    int status;
    TxnCtxt txn;
    txn.begin(dbenv.get(), true);
    try {
        status = db->put(txn.get(), &key, &value, DB_NOOVERWRITE);
        txn.commit();
    } catch (...) {
        txn.abort();
        throw;
    }
    if (status == DB_KEYEXIST) {
        return false;
    } else {
        p.setPersistenceId(id);
        return true;
    }
}

void MessageStoreImpl::destroy(db_ptr db, const qpid::broker::Persistable& p)
{
    qpid::sys::Mutex::ScopedLock sl(bdbLock);
    IdDbt key(p.getPersistenceId());
    db->del(0, &key, DB_AUTO_COMMIT);
}


void MessageStoreImpl::bind(const qpid::broker::PersistableExchange& e,
                           const qpid::broker::PersistableQueue& q,
                           const std::string& k,
                           const qpid::framing::FieldTable& a)
{
    checkInit();
    IdDbt key(e.getPersistenceId());
    BindingDbt value(e, q, k, a);
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

void MessageStoreImpl::unbind(const qpid::broker::PersistableExchange& e,
                             const qpid::broker::PersistableQueue& q,
                             const std::string& k,
                             const qpid::framing::FieldTable&)
{
    checkInit();
    deleteBinding(e, q, k);
}

void MessageStoreImpl::recover(qpid::broker::RecoveryManager& registry)
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
        recoverQueues(txn, registry, queues, prepared, messages);

        //recover exchange & bindings:
        recoverExchanges(txn, registry, exchanges);
        recoverBindings(txn, exchanges, queues);

        //recover general-purpose configuration
        recoverGeneral(txn, registry);

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
            if (!incomplTplTxnFlag) dtx = registry.recoverTransaction(xid, txn);
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
    registry.recoveryComplete();
}

void MessageStoreImpl::recoverQueues(TxnCtxt& txn,
                                    qpid::broker::RecoveryManager& registry,
                                    queue_index& queue_index,
                                    txn_list& prepared,
                                    message_index& messages)
{
    Cursor queues;
    queues.open(queueDb, txn.get());

    u_int64_t maxQueueId(1);

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
            QPID_LOG(error, "Cannot recover empty (null) queue name - ignoring and attempting to continue.");
            break;
        }
        jQueue = new JournalImpl(broker->getTimer(), queueName, getJrnlHashDir(queueName), std::string("JournalData"),
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
            u_int64_t thisHighestRid = 0ULL;
            jQueue->recover(numJrnlFiles, autoJrnlExpand, autoJrnlExpandMaxFiles, jrnlFsizeSblks, wCacheNumPages, wCachePgSizeSblks, &prepared, thisHighestRid, key.id); // start recovery

            // Check for changes to queue store settings qpid.file_count and qpid.file_size resulting
            // from recovery of a store that has had its size changed externally by the resize utility.
            // If so, update the queue store settings so that QMF queries will reflect the new values.
            const qpid::framing::FieldTable& storeargs = queue->getSettings().storeSettings;
            qpid::framing::FieldTable::ValuePtr value;
            value = storeargs.get("qpid.file_count");
            if (value.get() != 0 && !value->empty() && value->convertsTo<int>() && (u_int16_t)value->get<int>() != jQueue->num_jfiles()) {
                queue->addArgument("qpid.file_count", jQueue->num_jfiles());
            }
            value = storeargs.get("qpid.file_size");
            if (value.get() != 0 && !value->empty() && value->convertsTo<int>() && (u_int32_t)value->get<int>() != jQueue->jfsize_sblks()/JRNL_RMGR_PAGE_SIZE) {
                queue->addArgument("qpid.file_size", jQueue->jfsize_sblks()/JRNL_RMGR_PAGE_SIZE);
            }

            if (highestRid == 0ULL)
                highestRid = thisHighestRid;
            else if (thisHighestRid - highestRid < 0x8000000000000000ULL) // RFC 1982 comparison for unsigned 64-bit
                highestRid = thisHighestRid;
            recoverMessages(txn, registry, queue, prepared, messages, rcnt, idcnt);
            QPID_LOG(info, "Recovered queue \"" << queueName << "\": " << rcnt << " messages recovered; " << idcnt << " messages in-doubt.");
            jQueue->recover_complete(); // start journal.
        } catch (const journal::jexception& e) {
            THROW_STORE_EXCEPTION(std::string("Queue ") + queueName + ": recoverQueues() failed: " + e.what());
        }
        //read all messages: done on a per queue basis if using Journal

        queue_index[key.id] = queue;
        maxQueueId = std::max(key.id, maxQueueId);
    }

    // NOTE: highestRid is set by both recoverQueues() and recoverTplStore() as
    // the messageIdSequence is used for both queue journals and the tpl journal.
    messageIdSequence.reset(highestRid + 1);
    QPID_LOG(info, "Most recent persistence id found: 0x" << std::hex << highestRid << std::dec);

    queueIdSequence.reset(maxQueueId + 1);
}


void MessageStoreImpl::recoverExchanges(TxnCtxt& txn,
                                       qpid::broker::RecoveryManager& registry,
                                       exchange_index& index)
{
    //TODO: this is a copy&paste from recoverQueues - refactor!
    Cursor exchanges;
    exchanges.open(exchangeDb, txn.get());

    u_int64_t maxExchangeId(1);
    IdDbt key;
    Dbt value;
    //read all exchanges
    while (exchanges.next(key, value)) {
        qpid::framing::Buffer buffer(reinterpret_cast<char*>(value.get_data()), value.get_size());
        //create a Exchange instance
        qpid::broker::RecoverableExchange::shared_ptr exchange = registry.recoverExchange(buffer);
        if (exchange) {
            //set the persistenceId and update max as required
            exchange->setPersistenceId(key.id);
            index[key.id] = exchange;
            QPID_LOG(info, "Recovered exchange \"" << exchange->getName() << '"');
        }
        maxExchangeId = std::max(key.id, maxExchangeId);
    }
    exchangeIdSequence.reset(maxExchangeId + 1);
}

void MessageStoreImpl::recoverBindings(TxnCtxt& txn,
                                      exchange_index& exchanges,
                                      queue_index& queues)
{
    Cursor bindings;
    bindings.open(bindingDb, txn.get());

    IdDbt key;
    Dbt value;
    while (bindings.next(key, value)) {
        qpid::framing::Buffer buffer(reinterpret_cast<char*>(value.get_data()), value.get_size());
        if (buffer.available() < 8) {
            QPID_LOG(error, "Not enough data for binding: " << buffer.available());
            THROW_STORE_EXCEPTION("Not enough data for binding");
        }
        uint64_t queueId = buffer.getLongLong();
        std::string queueName;
        std::string routingkey;
        qpid::framing::FieldTable args;
        buffer.getShortString(queueName);
        buffer.getShortString(routingkey);
        buffer.get(args);
        exchange_index::iterator exchange = exchanges.find(key.id);
        queue_index::iterator queue = queues.find(queueId);
        if (exchange != exchanges.end() && queue != queues.end()) {
            //could use the recoverable queue here rather than the name...
            exchange->second->bind(queueName, routingkey, args);
            QPID_LOG(info, "Recovered binding exchange=" << exchange->second->getName()
                     << " key=" << routingkey
                     << " queue=" << queueName);
        } else {
            //stale binding, delete it
            QPID_LOG(warning, "Deleting stale binding");
            bindings->del(0);
        }
    }
}

void MessageStoreImpl::recoverGeneral(TxnCtxt& txn,
                                     qpid::broker::RecoveryManager& registry)
{
    Cursor items;
    items.open(generalDb, txn.get());

    u_int64_t maxGeneralId(1);
    IdDbt key;
    Dbt value;
    //read all items
    while (items.next(key, value)) {
        qpid::framing::Buffer buffer(reinterpret_cast<char*>(value.get_data()), value.get_size());
        //create instance
        qpid::broker::RecoverableConfig::shared_ptr config = registry.recoverConfig(buffer);
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
    size_t preambleLength = sizeof(u_int32_t)/*header size*/;

    JournalImpl* jc = static_cast<JournalImpl*>(queue->getExternalQueueStore());
    DataTokenImpl dtok;
    size_t readSize = 0;
    unsigned msg_count = 0;

    // TODO: This optimization to skip reading if there are no enqueued messages to read
    // breaks the python system test in phase 6 with "Exception: Cannot write lock file"
    // Figure out what is breaking.
    //bool read = jc->get_enq_cnt() > 0;
    bool read = true;

    void* dbuff = NULL; size_t dbuffSize = 0;
    void* xidbuff = NULL; size_t xidbuffSize = 0;
    bool transientFlag = false;
    bool externalFlag = false;

    dtok.set_wstate(DataTokenImpl::ENQ);

    // Read the message from the Journal.
    try {
        unsigned aio_sleep_cnt = 0;
        while (read) {
            mrg::journal::iores res = jc->read_data_record(&dbuff, dbuffSize, &xidbuff, xidbuffSize, transientFlag, externalFlag, &dtok);
            readSize = dtok.dsize();

            switch (res)
            {
              case mrg::journal::RHM_IORES_SUCCESS: {
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
		msg->computeExpiration();

                u_int32_t contentOffset = headerSize + preambleLength;
                u_int64_t contentSize = readSize - contentOffset;
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
                    u_int64_t rid = dtok.rid();
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
                            journal::txn_map& tmap = jc->get_txn_map();
                            journal::txn_data_list txnList = tmap.get_tdata_list(xid); // txnList will be empty if xid not found
                            bool enq = false;
                            bool deq = false;
                            for (journal::tdl_itr j = txnList.begin(); j<txnList.end(); j++) {
                                if (j->_enq_flag && j->_rid == rid) enq = true;
                                else if (!j->_enq_flag && j->_drid == rid) deq = true;
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
                dtok.set_wstate(DataTokenImpl::ENQ);

                if (xidbuff)
                    ::free(xidbuff);
                else if (dbuff)
                    ::free(dbuff);
                aio_sleep_cnt = 0;
                break;
              }
              case mrg::journal::RHM_IORES_PAGE_AIOWAIT:
                if (++aio_sleep_cnt > MAX_AIO_SLEEPS)
                    THROW_STORE_EXCEPTION("Timeout waiting for AIO in MessageStoreImpl::recoverMessages()");
                ::usleep(AIO_SLEEP_TIME_US);
                break;
              case mrg::journal::RHM_IORES_EMPTY:
                read = false;
                break; // done with all messages. (add call in jrnl to test that _emap is empty.)
              default:
                std::ostringstream oss;
                oss << "recoverMessages(): Queue: " << queue->getName() << ": Unexpected return from journal read: " << mrg::journal::iores_str(res);
                THROW_STORE_EXCEPTION(oss.str());
            } // switch
        } // while
    } catch (const journal::jexception& e) {
        THROW_STORE_EXCEPTION(std::string("Queue ") + queue->getName() + ": recoverMessages() failed: " + e.what());
    }
}

qpid::broker::RecoverableMessage::shared_ptr MessageStoreImpl::getExternMessage(qpid::broker::RecoveryManager& /*recovery*/,
                                                                 uint64_t /*messageId*/,
                                                                 unsigned& /*headerSize*/)
{
    throw mrg::journal::jexception(mrg::journal::jerrno::JERR__NOTIMPL, "MessageStoreImpl", "getExternMessage");
}

int MessageStoreImpl::enqueueMessage(TxnCtxt& txn,
                                    IdDbt& msgId,
                                    qpid::broker::RecoverableMessage::shared_ptr& msg,
                                    queue_index& index,
                                    txn_list& prepared,
                                    message_index& messages)
{
    Cursor mappings;
    mappings.open(mappingDb, txn.get());

    IdDbt value;

    int count(0);
    for (int status = mappings->get(&msgId, &value, DB_SET); status == 0; status = mappings->get(&msgId, &value, DB_NEXT_DUP)) {
        if (index.find(value.id) == index.end()) {
            QPID_LOG(warning, "Recovered message for queue that no longer exists");
            mappings->del(0);
        } else {
            qpid::broker::RecoverableQueue::shared_ptr queue = index[value.id];
            if (PreparedTransaction::isLocked(prepared, value.id, msgId.id)) {
                messages[msgId.id] = msg;
            } else {
                queue->recover(msg);
            }
            count++;
        }
    }
    mappings.close();
    return count;
}

void MessageStoreImpl::readTplStore()
{
    tplRecoverMap.clear();
    journal::txn_map& tmap = tplStorePtr->get_txn_map();
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
            mrg::journal::iores res = tplStorePtr->read_data_record(&dbuff, dbuffSize, &xidbuff, xidbuffSize, transientFlag, externalFlag, &dtok);
            switch (res) {
              case mrg::journal::RHM_IORES_SUCCESS: {
                // Every TPL record contains both data and an XID
                assert(dbuffSize>0);
                assert(xidbuffSize>0);
                std::string xid(static_cast<const char*>(xidbuff), xidbuffSize);
                bool is2PC = *(static_cast<char*>(dbuff)) != 0;

                // Check transaction details; add to recover map
                journal::txn_data_list txnList = tmap.get_tdata_list(xid); //  txnList will be empty if xid not found
                if (!txnList.empty()) { // xid found in tmap
                    unsigned enqCnt = 0;
                    unsigned deqCnt = 0;
                    u_int64_t rid = 0;

                    // Assume commit (roll forward) in cases where only prepare has been called - ie only enqueue record exists.
                    // Note: will apply to both 1PC and 2PC transactions.
                    bool commitFlag = true;

                    for (journal::tdl_itr j = txnList.begin(); j<txnList.end(); j++) {
                        if (j->_enq_flag) {
                            rid = j->_rid;
                            enqCnt++;
                        } else {
                            commitFlag = j->_commit_flag;
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
              case mrg::journal::RHM_IORES_PAGE_AIOWAIT:
                if (++aio_sleep_cnt > MAX_AIO_SLEEPS)
                    THROW_STORE_EXCEPTION("Timeout waiting for AIO in MessageStoreImpl::recoverTplStore()");
                ::usleep(AIO_SLEEP_TIME_US);
                break;
              case mrg::journal::RHM_IORES_EMPTY:
                done = true;
                break; // done with all messages. (add call in jrnl to test that _emap is empty.)
              default:
                std::ostringstream oss;
                oss << "readTplStore(): Unexpected result from journal read: " << mrg::journal::iores_str(res);
                THROW_STORE_EXCEPTION(oss.str());
            } // switch
        }
    } catch (const journal::jexception& e) {
        THROW_STORE_EXCEPTION(std::string("TPL recoverTplStore() failed: ") + e.what());
    }
}

void MessageStoreImpl::recoverTplStore()
{
    if (journal::jdir::exists(tplStorePtr->jrnl_dir() + tplStorePtr->base_filename() + ".jinf")) {
        u_int64_t thisHighestRid = 0ULL;
        tplStorePtr->recover(tplNumJrnlFiles, false, 0, tplJrnlFsizeSblks, tplWCachePgSizeSblks, tplWCacheNumPages, 0, thisHighestRid, 0);
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
    if (tplStorePtr->is_ready()) {
        tplStorePtr->read_reset();
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
    throw mrg::journal::jexception(mrg::journal::jerrno::JERR__NOTIMPL, "MessageStoreImpl", "stage");
}

void MessageStoreImpl::destroy(qpid::broker::PersistableMessage& /*msg*/)
{
    throw mrg::journal::jexception(mrg::journal::jerrno::JERR__NOTIMPL, "MessageStoreImpl", "destroy");
}

void MessageStoreImpl::appendContent(const boost::intrusive_ptr<const qpid::broker::PersistableMessage>& /*msg*/,
                                    const std::string& /*data*/)
{
    throw mrg::journal::jexception(mrg::journal::jerrno::JERR__NOTIMPL, "MessageStoreImpl", "appendContent");
}

void MessageStoreImpl::loadContent(const qpid::broker::PersistableQueue& queue,
                                  const boost::intrusive_ptr<const qpid::broker::PersistableMessage>& msg,
                                  std::string& data,
                                  u_int64_t offset,
                                  u_int32_t length)
{
    checkInit();
    u_int64_t messageId (msg->getPersistenceId());

    if (messageId != 0) {
        try {
            JournalImpl* jc = static_cast<JournalImpl*>(queue.getExternalQueueStore());
            if (jc && jc->is_enqueued(messageId) ) {
                if (!jc->loadMsgContent(messageId, data, length, offset)) {
                    std::ostringstream oss;
                    oss << "Queue " << queue.getName() << ": loadContent() failed: Message " << messageId << " is extern";
                    THROW_STORE_EXCEPTION(oss.str());
                }
            } else {
                std::ostringstream oss;
                oss << "Queue " << queue.getName() << ": loadContent() failed: Message " << messageId << " not enqueued";
                THROW_STORE_EXCEPTION(oss.str());
            }
        } catch (const journal::jexception& e) {
            THROW_STORE_EXCEPTION(std::string("Queue ") + queue.getName() + ": loadContent() failed: " + e.what());
        }
    } else {
        THROW_STORE_EXCEPTION("Cannot load content. Message not known to store!");
    }
}

void MessageStoreImpl::flush(const qpid::broker::PersistableQueue& queue)
{
    if (queue.getExternalQueueStore() == 0) return;
    checkInit();
    std::string qn = queue.getName();
    try {
        JournalImpl* jc = static_cast<JournalImpl*>(queue.getExternalQueueStore());
        if (jc) {
            // TODO: check if this result should be used...
            /*mrg::journal::iores res =*/ jc->flush();
        }
    } catch (const journal::jexception& e) {
        THROW_STORE_EXCEPTION(std::string("Queue ") + qn + ": flush() failed: " + e.what() );
    }
}

void MessageStoreImpl::enqueue(qpid::broker::TransactionContext* ctxt,
                              const boost::intrusive_ptr<qpid::broker::PersistableMessage>& msg,
                              const qpid::broker::PersistableQueue& queue)
{
    checkInit();
    u_int64_t queueId (queue.getPersistenceId());
    u_int64_t messageId (msg->getPersistenceId());
    if (queueId == 0) {
        THROW_STORE_EXCEPTION("Queue not created: " + queue.getName());
    }

    TxnCtxt implicit;
    TxnCtxt* txn = 0;
    if (ctxt) {
        txn = check(ctxt);
    } else {
        txn = &implicit;
    }

    bool newId = false;
    if (messageId == 0) {
        messageId = messageIdSequence.next();
        msg->setPersistenceId(messageId);
        newId = true;
    }
    store(&queue, txn, msg, newId);

    // add queue* to the txn map..
    if (ctxt) txn->addXidRecord(queue.getExternalQueueStore());
}

u_int64_t MessageStoreImpl::msgEncode(std::vector<char>& buff, const boost::intrusive_ptr<qpid::broker::PersistableMessage>& message)
{
    u_int32_t headerSize = message->encodedHeaderSize();
    u_int64_t size = message->encodedSize() + sizeof(u_int32_t);
    try { buff = std::vector<char>(size); } // long + headers + content
    catch (const std::exception& e) {
        std::ostringstream oss;
        oss << "Unable to allocate memory for encoding message; requested size: " << size << "; error: " << e.what();
        THROW_STORE_EXCEPTION(oss.str());
    }
    qpid::framing::Buffer buffer(&buff[0],size);
    buffer.putLong(headerSize);
    message->encode(buffer);
    return size;
}

void MessageStoreImpl::store(const qpid::broker::PersistableQueue* queue,
                            TxnCtxt* txn,
                            const boost::intrusive_ptr<qpid::broker::PersistableMessage>& message,
                            bool /*newId*/)
{
    std::vector<char> buff;
    u_int64_t size = msgEncode(buff, message);

    try {
        if (queue) {
            boost::intrusive_ptr<DataTokenImpl> dtokp(new DataTokenImpl);
            dtokp->addRef();
            dtokp->setSourceMessage(message);
            dtokp->set_external_rid(true);
            dtokp->set_rid(message->getPersistenceId()); // set the messageID into the Journal header (record-id)

            JournalImpl* jc = static_cast<JournalImpl*>(queue->getExternalQueueStore());
            if (txn->getXid().empty()) {
                jc->enqueue_data_record(&buff[0], size, size, dtokp.get(), !message->isPersistent());
            } else {
                jc->enqueue_txn_data_record(&buff[0], size, size, dtokp.get(), txn->getXid(), !message->isPersistent());
            }
        } else {
            THROW_STORE_EXCEPTION(std::string("MessageStoreImpl::store() failed: queue NULL."));
       }
    } catch (const journal::jexception& e) {
        THROW_STORE_EXCEPTION(std::string("Queue ") + queue->getName() + ": MessageStoreImpl::store() failed: " +
                              e.what());
    }
}

void MessageStoreImpl::dequeue(qpid::broker::TransactionContext* ctxt,
                              const boost::intrusive_ptr<qpid::broker::PersistableMessage>& msg,
                              const qpid::broker::PersistableQueue& queue)
{
    checkInit();
    u_int64_t queueId (queue.getPersistenceId());
    u_int64_t messageId (msg->getPersistenceId());
    if (queueId == 0) {
        THROW_STORE_EXCEPTION("Queue \"" + queue.getName() + "\" has null queue Id (has not been created)");
    }
    if (messageId == 0) {
        THROW_STORE_EXCEPTION("Queue \"" + queue.getName() + "\": Dequeuing message with null persistence Id.");
    }

    TxnCtxt implicit;
    TxnCtxt* txn = 0;
    if (ctxt) {
        txn = check(ctxt);
    } else {
        txn = &implicit;
    }

    // add queue* to the txn map..
    if (ctxt) txn->addXidRecord(queue.getExternalQueueStore());
    async_dequeue(ctxt, msg, queue);

    msg->dequeueComplete();
}

void MessageStoreImpl::async_dequeue(qpid::broker::TransactionContext* ctxt,
                                    const boost::intrusive_ptr<qpid::broker::PersistableMessage>& msg,
                                    const qpid::broker::PersistableQueue& queue)
{
    boost::intrusive_ptr<DataTokenImpl> ddtokp(new DataTokenImpl);
    ddtokp->setSourceMessage(msg);
    ddtokp->set_external_rid(true);
    ddtokp->set_rid(messageIdSequence.next());
    ddtokp->set_dequeue_rid(msg->getPersistenceId());
    ddtokp->set_wstate(DataTokenImpl::ENQ);
    std::string tid;
    if (ctxt) {
        TxnCtxt* txn = check(ctxt);
        tid = txn->getXid();
    }
    // Manually increase the ref count, as raw pointers are used beyond this point
    ddtokp->addRef();
    try {
        JournalImpl* jc = static_cast<JournalImpl*>(queue.getExternalQueueStore());
        if (tid.empty()) {
            jc->dequeue_data_record(ddtokp.get());
        } else {
            jc->dequeue_txn_data_record(ddtokp.get(), tid);
        }
    } catch (const journal::jexception& e) {
        ddtokp->release();
        THROW_STORE_EXCEPTION(std::string("Queue ") + queue.getName() + ": async_dequeue() failed: " + e.what());
    }
}

u_int32_t MessageStoreImpl::outstandingQueueAIO(const qpid::broker::PersistableQueue& /*queue*/)
{
    checkInit();
    return 0;
}

void MessageStoreImpl::completed(TxnCtxt& txn,
                                bool commit)
{
    try {
        chkTplStoreInit(); // Late initialize (if needed)

        // Nothing to do if not prepared
        if (txn.getDtok()->is_enqueued()) {
            txn.incrDtokRef();
            DataTokenImpl* dtokp = txn.getDtok();
            dtokp->set_dequeue_rid(dtokp->rid());
            dtokp->set_rid(messageIdSequence.next());
            tplStorePtr->dequeue_txn_data_record(txn.getDtok(), txn.getXid(), commit);
        }
        txn.complete(commit);
        if (mgmtObject.get() != 0) {
            mgmtObject->dec_tplTransactionDepth();
            if (commit)
                mgmtObject->inc_tplTxnCommits();
            else
                mgmtObject->inc_tplTxnAborts();
        }
    } catch (const std::exception& e) {
        QPID_LOG(error, "Error completing xid " << txn.getXid() << ": " << e.what());
        throw;
    }
}

std::auto_ptr<qpid::broker::TransactionContext> MessageStoreImpl::begin()
{
    checkInit();
    // pass sequence number for c/a
    return std::auto_ptr<qpid::broker::TransactionContext>(new TxnCtxt(&messageIdSequence));
}

std::auto_ptr<qpid::broker::TPCTransactionContext> MessageStoreImpl::begin(const std::string& xid)
{
    checkInit();
    IdSequence* jtx = &messageIdSequence;
    // pass sequence number for c/a
    return std::auto_ptr<qpid::broker::TPCTransactionContext>(new TPCTxnCtxt(xid, jtx));
}

void MessageStoreImpl::prepare(qpid::broker::TPCTransactionContext& ctxt)
{
    checkInit();
    TxnCtxt* txn = dynamic_cast<TxnCtxt*>(&ctxt);
    if(!txn) throw qpid::broker::InvalidTransactionContextException();
    localPrepare(txn);
}

void MessageStoreImpl::localPrepare(TxnCtxt* ctxt)
{
    try {
        chkTplStoreInit(); // Late initialize (if needed)

        // This sync is required to ensure multi-queue atomicity - ie all txn data
        // must hit the disk on *all* queues before the TPL prepare (enq) is written.
        ctxt->sync();

        ctxt->incrDtokRef();
        DataTokenImpl* dtokp = ctxt->getDtok();
        dtokp->set_external_rid(true);
        dtokp->set_rid(messageIdSequence.next());
        char tpcFlag = static_cast<char>(ctxt->isTPC());
        tplStorePtr->enqueue_txn_data_record(&tpcFlag, sizeof(char), sizeof(char), dtokp, ctxt->getXid(), false);
        ctxt->prepare(tplStorePtr.get());
        // make sure all the data is written to disk before returning
        ctxt->sync();
        if (mgmtObject.get() != 0) {
            mgmtObject->inc_tplTransactionDepth();
            mgmtObject->inc_tplTxnPrepares();
        }
    } catch (const std::exception& e) {
        QPID_LOG(error, "Error preparing xid " << ctxt->getXid() << ": " << e.what());
        throw;
    }
}

void MessageStoreImpl::commit(qpid::broker::TransactionContext& ctxt)
{
    checkInit();
    TxnCtxt* txn(check(&ctxt));
    if (!txn->isTPC()) {
        if (txn->impactedQueuesEmpty()) return;
        localPrepare(dynamic_cast<TxnCtxt*>(txn));
    }
    completed(*dynamic_cast<TxnCtxt*>(txn), true);
}

void MessageStoreImpl::abort(qpid::broker::TransactionContext& ctxt)
{
    checkInit();
    TxnCtxt* txn(check(&ctxt));
    if (!txn->isTPC()) {
        if (txn->impactedQueuesEmpty()) return;
        localPrepare(dynamic_cast<TxnCtxt*>(txn));
    }
    completed(*dynamic_cast<TxnCtxt*>(txn), false);
}

TxnCtxt* MessageStoreImpl::check(qpid::broker::TransactionContext* ctxt)
{
    TxnCtxt* txn = dynamic_cast<TxnCtxt*>(ctxt);
    if(!txn) throw qpid::broker::InvalidTransactionContextException();
    return txn;
}

void MessageStoreImpl::put(db_ptr db,
                          DbTxn* txn,
                          Dbt& key,
                          Dbt& value)
{
    try {
        int status = db->put(txn, &key, &value, DB_NODUPDATA);
        if (status == DB_KEYEXIST) {
            THROW_STORE_EXCEPTION("duplicate data");
        } else if (status) {
            THROW_STORE_EXCEPTION(DbEnv::strerror(status));
        }
    } catch (const DbException& e) {
        THROW_STORE_EXCEPTION(e.what());
    }
}

void MessageStoreImpl::deleteBindingsForQueue(const qpid::broker::PersistableQueue& queue)
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
                if (queue.getPersistenceId() == queueId) {
                    bindings->del(0);
                    QPID_LOG(debug, "Deleting binding for " << queue.getName() << " " << key.id << "->" << queueId);
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
    QPID_LOG(debug, "Deleted all bindings for " << queue.getName() << ":" << queue.getPersistenceId());
}

void MessageStoreImpl::deleteBinding(const qpid::broker::PersistableExchange& exchange,
                                    const qpid::broker::PersistableQueue& queue,
                                    const std::string& bkey)
{
    TxnCtxt txn;
    txn.begin(dbenv.get(), true);
    try {
        {
            Cursor bindings;
            bindings.open(bindingDb, txn.get());

            IdDbt key(exchange.getPersistenceId());
            Dbt value;

            for (int status = bindings->get(&key, &value, DB_SET); status == 0; status = bindings->get(&key, &value, DB_NEXT_DUP)) {
                qpid::framing::Buffer buffer(reinterpret_cast<char*>(value.get_data()), value.get_size());
                if (buffer.available() < 8) {
                    THROW_STORE_EXCEPTION("Not enough data for binding");
                }
                uint64_t queueId = buffer.getLongLong();
                if (queue.getPersistenceId() == queueId) {
                    std::string q;
                    std::string k;
                    buffer.getShortString(q);
                    buffer.getShortString(k);
                    if (bkey == k) {
                        bindings->del(0);
                        QPID_LOG(debug, "Deleting binding for " << queue.getName() << " " << key.id << "->" << queueId);
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

std::string MessageStoreImpl::getJrnlDir(const qpid::broker::PersistableQueue& queue) //for exmaple /var/rhm/ + queueDir/
{
    return getJrnlHashDir(queue.getName().c_str());
}

u_int32_t MessageStoreImpl::bHash(const std::string str)
{
    // Daniel Bernstein hash fn
    u_int32_t h = 0;
    for (std::string::const_iterator i = str.begin(); i < str.end(); i++)
        h = 33*h + *i;
    return h;
}

std::string MessageStoreImpl::getJrnlHashDir(const std::string& queueName) //for exmaple /var/rhm/ + queueDir/
{
    std::stringstream dir;
    dir << getJrnlBaseDir() << std::hex << std::setfill('0') << std::setw(4);
    dir << (bHash(queueName.c_str()) % 29); // Use a prime number for better distribution across dirs
    dir << "/" << queueName << "/";
    return dir.str();
}

std::string MessageStoreImpl::getStoreDir() const { return storeDir; }

void MessageStoreImpl::journalDeleted(JournalImpl& j) {
    qpid::sys::Mutex::ScopedLock sl(journalListLock);
    journalList.erase(j.id());
}

MessageStoreImpl::StoreOptions::StoreOptions(const std::string& name) :
                                             qpid::Options(name),
                                             numJrnlFiles(defNumJrnlFiles),
                                             autoJrnlExpand(defAutoJrnlExpand),
                                             autoJrnlExpandMaxFiles(defAutoJrnlExpandMaxFiles),
                                             jrnlFsizePgs(defJrnlFileSizePgs),
                                             truncateFlag(defTruncateFlag),
                                             wCachePageSizeKib(defWCachePageSize),
                                             tplNumJrnlFiles(defTplNumJrnlFiles),
                                             tplJrnlFsizePgs(defTplJrnlFileSizePgs),
                                             tplWCachePageSizeKib(defTplWCachePageSize)
{
    std::ostringstream oss1;
    oss1 << "Default number of files for each journal instance (queue). [Allowable values: " <<
                    JRNL_MIN_NUM_FILES << " - " << JRNL_MAX_NUM_FILES << "]";
    std::ostringstream oss2;
    oss2 << "Default size for each journal file in multiples of read pages (1 read page = 64KiB). [Allowable values: " <<
                    JRNL_MIN_FILE_SIZE / JRNL_RMGR_PAGE_SIZE << " - " << JRNL_MAX_FILE_SIZE / JRNL_RMGR_PAGE_SIZE << "]";
    std::ostringstream oss3;
    oss3 << "Number of files for transaction prepared list journal instance. [Allowable values: " <<
                    JRNL_MIN_NUM_FILES << " - " << JRNL_MAX_NUM_FILES << "]";
    std::ostringstream oss4;
    oss4 << "Size of each transaction prepared list journal file in multiples of read pages (1 read page = 64KiB) [Allowable values: " <<
                    JRNL_MIN_FILE_SIZE / JRNL_RMGR_PAGE_SIZE << " - " << JRNL_MAX_FILE_SIZE / JRNL_RMGR_PAGE_SIZE << "]";
    addOptions()
        ("store-dir", qpid::optValue(storeDir, "DIR"),
                "Store directory location for persistence (instead of using --data-dir value). "
                "Required if --no-data-dir is also used.")
        ("num-jfiles", qpid::optValue(numJrnlFiles, "N"), oss1.str().c_str())
        ("jfile-size-pgs", qpid::optValue(jrnlFsizePgs, "N"), oss2.str().c_str())
// TODO: Uncomment these lines when auto-expand is enabled.
//         ("auto-expand", qpid::optValue(autoJrnlExpand, "yes|no"),
//                 "If yes|true|1, allows journal to auto-expand by adding additional journal files as needed. "
//                 "If no|false|0, the number of journal files will remain fixed (num-jfiles).")
//         ("max-auto-expand-jfiles", qpid::optValue(autoJrnlExpandMaxFiles, "N"),
//                 "Maximum number of journal files allowed from auto-expanding; must be greater than --num-jfiles parameter.")
        ("truncate", qpid::optValue(truncateFlag, "yes|no"),
                "If yes|true|1, will truncate the store (discard any existing records). If no|false|0, will preserve "
                "the existing store files for recovery.")
        ("wcache-page-size", qpid::optValue(wCachePageSizeKib, "N"),
                "Size of the pages in the write page cache in KiB. "
                "Allowable values - powers of 2: 1, 2, 4, ... , 128. "
                "Lower values decrease latency at the expense of throughput.")
        ("tpl-num-jfiles", qpid::optValue(tplNumJrnlFiles, "N"), oss3.str().c_str())
        ("tpl-jfile-size-pgs", qpid::optValue(tplJrnlFsizePgs, "N"), oss4.str().c_str())
        ("tpl-wcache-page-size", qpid::optValue(tplWCachePageSizeKib, "N"),
                "Size of the pages in the transaction prepared list write page cache in KiB. "
                "Allowable values - powers of 2: 1, 2, 4, ... , 128. "
                "Lower values decrease latency at the expense of throughput.")
        ;
}

}}
