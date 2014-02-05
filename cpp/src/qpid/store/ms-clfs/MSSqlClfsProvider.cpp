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

#include <list>
#include <map>
#include <set>
#include <stdlib.h>
#include <string>
#include <windows.h>
#include <clfsw32.h>
#include <qpid/broker/Broker.h>
#include <qpid/broker/RecoverableQueue.h>
#include <qpid/log/Statement.h>
#include <qpid/store/MessageStorePlugin.h>
#include <qpid/store/StoreException.h>
#include <qpid/store/StorageProvider.h>
#include <qpid/sys/Mutex.h>
#include <boost/foreach.hpp>

// From ms-sql...
#include "BlobAdapter.h"
#include "BlobRecordset.h"
#include "BindingRecordset.h"
#include "DatabaseConnection.h"
#include "Exception.h"
#include "State.h"
#include "VariantHelper.h"
using qpid::store::ms_sql::BlobAdapter;
using qpid::store::ms_sql::BlobRecordset;
using qpid::store::ms_sql::BindingRecordset;
using qpid::store::ms_sql::DatabaseConnection;
using qpid::store::ms_sql::ADOException;
using qpid::store::ms_sql::State;
using qpid::store::ms_sql::VariantHelper;

#include "Log.h"
#include "Messages.h"
#include "Transaction.h"
#include "TransactionLog.h"

// Bring in ADO 2.8 (yes, I know it says "15", but that's it...)
#import "C:\Program Files\Common Files\System\ado\msado15.dll" \
        no_namespace rename("EOF", "EndOfFile")
#include <comdef.h>
namespace {
inline void TESTHR(HRESULT x) {if FAILED(x) _com_issue_error(x);};

// Table names
const std::string TblBinding("tblBinding");
const std::string TblConfig("tblConfig");
const std::string TblExchange("tblExchange");
const std::string TblQueue("tblQueue");

}

namespace qpid {
namespace store {
namespace ms_clfs {

/**
 * @class MSSqlClfsProvider
 *
 * Implements a qpid::store::StorageProvider that uses a hybrid Microsoft
 * SQL Server and Windows CLFS approach as the backend data store for Qpid.
 */
class MSSqlClfsProvider : public qpid::store::StorageProvider
{
protected:
    void finalizeMe();

    void dump();

public:
    MSSqlClfsProvider();
    ~MSSqlClfsProvider();

    virtual qpid::Options* getOptions() { return &options; }

    virtual void earlyInitialize (Plugin::Target& target);
    virtual void initialize(Plugin::Target& target);

    /**
     * Receive notification that this provider is the one that will actively
     * handle provider storage for the target. If the provider is to be used,
     * this method will be called after earlyInitialize() and before any
     * recovery operations (recovery, in turn, precedes call to initialize()).
     */
    virtual void activate(MessageStorePlugin &store);

    /**
     * @name Methods inherited from qpid::broker::MessageStore
     */

    /**
     * Record the existence of a durable queue
     */
    virtual void create(PersistableQueue& queue,
                        const qpid::framing::FieldTable& args);
    /**
     * Destroy a durable queue
     */
    virtual void destroy(PersistableQueue& queue);

    /**
     * Record the existence of a durable exchange
     */
    virtual void create(const PersistableExchange& exchange,
                        const qpid::framing::FieldTable& args);
    /**
     * Destroy a durable exchange
     */
    virtual void destroy(const PersistableExchange& exchange);

    /**
     * Record a binding
     */
    virtual void bind(const PersistableExchange& exchange,
                      const PersistableQueue& queue,
                      const std::string& key,
                      const qpid::framing::FieldTable& args);

    /**
     * Forget a binding
     */
    virtual void unbind(const PersistableExchange& exchange,
                        const PersistableQueue& queue,
                        const std::string& key,
                        const qpid::framing::FieldTable& args);

    /**
     * Record generic durable configuration
     */
    virtual void create(const PersistableConfig& config);

    /**
     * Destroy generic durable configuration
     */
    virtual void destroy(const PersistableConfig& config);

    /**
     * Stores a messages before it has been enqueued
     * (enqueueing automatically stores the message so this is
     * only required if storage is required prior to that
     * point). If the message has not yet been stored it will
     * store the headers as well as any content passed in. A
     * persistence id will be set on the message which can be
     * used to load the content or to append to it.
     */
    virtual void stage(const boost::intrusive_ptr<PersistableMessage>& msg);

    /**
     * Destroys a previously staged message. This only needs
     * to be called if the message is never enqueued. (Once
     * enqueued, deletion will be automatic when the message
     * is dequeued from all queues it was enqueued onto).
     */
    virtual void destroy(PersistableMessage& msg);

    /**
     * Appends content to a previously staged message
     */
    virtual void appendContent(const boost::intrusive_ptr<const PersistableMessage>& msg,
                               const std::string& data);

    /**
     * Loads (a section) of content data for the specified
     * message (previously stored through a call to stage or
     * enqueue) into data. The offset refers to the content
     * only (i.e. an offset of 0 implies that the start of the
     * content should be loaded, not the headers or related
     * meta-data).
     */
    virtual void loadContent(const qpid::broker::PersistableQueue& queue,
                             const boost::intrusive_ptr<const PersistableMessage>& msg,
                             std::string& data,
                             uint64_t offset,
                             uint32_t length);

    /**
     * Enqueues a message, storing the message if it has not
     * been previously stored and recording that the given
     * message is on the given queue.
     *
     * Note: that this is async so the return of the function does
     * not mean the opperation is complete.
     *
     * @param msg the message to enqueue
     * @param queue the name of the queue onto which it is to be enqueued
     * @param xid (a pointer to) an identifier of the
     * distributed transaction in which the operation takes
     * place or null for 'local' transactions
     */
    virtual void enqueue(qpid::broker::TransactionContext* ctxt,
                         const boost::intrusive_ptr<PersistableMessage>& msg,
                         const PersistableQueue& queue);

    /**
     * Dequeues a message, recording that the given message is
     * no longer on the given queue and deleting the message
     * if it is no longer on any other queue.
     *
     * Note: that this is async so the return of the function does
     * not mean the opperation is complete.
     *
     * @param msg the message to dequeue
     * @param queue the name of the queue from which it is to be dequeued
     * @param xid (a pointer to) an identifier of the
     * distributed transaction in which the operation takes
     * place or null for 'local' transactions
     */
    virtual void dequeue(qpid::broker::TransactionContext* ctxt,
                         const boost::intrusive_ptr<PersistableMessage>& msg,
                         const PersistableQueue& queue);

    /**
     * Flushes all async messages to disk for the specified queue
     *
     * Note: this is a no-op for this provider.
     *
     * @param queue the name of the queue from which it is to be dequeued
     */
    virtual void flush(const PersistableQueue& queue) {};

    /**
     * Returns the number of outstanding AIO's for a given queue
     *
     * If 0, than all the enqueue / dequeues have been stored
     * to disk
     *
     * @param queue the name of the queue to check for outstanding AIO
     */
    virtual uint32_t outstandingQueueAIO(const PersistableQueue& queue)
        {return 0;}
    //@}

    /**
     * @name Methods inherited from qpid::broker::TransactionalStore
     */
    //@{
    virtual std::auto_ptr<qpid::broker::TransactionContext> begin();
    virtual std::auto_ptr<qpid::broker::TPCTransactionContext> begin(const std::string& xid);
    virtual void prepare(qpid::broker::TPCTransactionContext& txn);
    virtual void commit(qpid::broker::TransactionContext& txn);
    virtual void abort(qpid::broker::TransactionContext& txn);
    virtual void collectPreparedXids(std::set<std::string>& xids);
    //@}

    virtual void recoverConfigs(qpid::broker::RecoveryManager& recoverer);
    virtual void recoverExchanges(qpid::broker::RecoveryManager& recoverer,
                                  ExchangeMap& exchangeMap);
    virtual void recoverQueues(qpid::broker::RecoveryManager& recoverer,
                               QueueMap& queueMap);
    virtual void recoverBindings(qpid::broker::RecoveryManager& recoverer,
                                 const ExchangeMap& exchangeMap,
                                 const QueueMap& queueMap);
    virtual void recoverMessages(qpid::broker::RecoveryManager& recoverer,
                                 MessageMap& messageMap,
                                 MessageQueueMap& messageQueueMap);
    virtual void recoverTransactions(qpid::broker::RecoveryManager& recoverer,
                                     PreparedTransactionMap& dtxMap);

private:
    struct ProviderOptions : public qpid::Options
    {
        std::string connectString;
        std::string catalogName;
        std::string storeDir;
        size_t containerSize;
        unsigned short initialContainers;
        uint32_t maxWriteBuffers;

        ProviderOptions(const std::string &name)
            : qpid::Options(name),
              catalogName("QpidStore"),
              containerSize(1024 * 1024),
              initialContainers(2),
              maxWriteBuffers(10)
        {
            const enum { NAMELEN = MAX_COMPUTERNAME_LENGTH + 1 };
            TCHAR myName[NAMELEN];
            DWORD myNameLen = NAMELEN;
            GetComputerName(myName, &myNameLen);
            connectString = "Data Source=";
            connectString += myName;
            connectString += "\\SQLEXPRESS;Integrated Security=SSPI";
            addOptions()
                ("connect",
                 qpid::optValue(connectString, "STRING"),
                 "Connection string for the database to use. Will prepend "
                 "Provider=SQLOLEDB;")
                ("catalog",
                 qpid::optValue(catalogName, "DB NAME"),
                 "Catalog (database) name")
                ("store-dir",
                 qpid::optValue(storeDir, "DIR"),
                 "Location to store message and transaction data "
                 "(default uses data-dir if available)")
                ("container-size",
                 qpid::optValue(containerSize, "VALUE"),
                 "Bytes per container; min 512K. Only used when creating "
                 "a new log")
                ("initial-containers",
                 qpid::optValue(initialContainers, "VALUE"),
                 "Number of containers to add if creating a new log")
                ("max-write-buffers",
                 qpid::optValue(maxWriteBuffers, "VALUE"),
                 "Maximum write buffers outstanding before log is flushed "
                 "(0 means no limit)")
                ;
        }
    };
    ProviderOptions options;
    std::string brokerDataDir;
    Messages messages;
    // TransactionLog requires itself to have a shared_ptr reference to start.
    TransactionLog::shared_ptr transactions;

    // Each thread has a separate connection to the database and also needs
    // to manage its COM initialize/finalize individually. This is done by
    // keeping a thread-specific State.
    boost::thread_specific_ptr<State> dbState;

    State *initState();
    DatabaseConnection *initConnection(void);
    void createDb(DatabaseConnection *db, const std::string &name);
    void createLogs();
};

static MSSqlClfsProvider static_instance_registers_plugin;

void
MSSqlClfsProvider::finalizeMe()
{
    dbState.reset();
}

MSSqlClfsProvider::MSSqlClfsProvider()
    : options("MS SQL/CLFS Provider options")
{
    transactions.reset(new TransactionLog());
}

MSSqlClfsProvider::~MSSqlClfsProvider()
{
}

void
MSSqlClfsProvider::earlyInitialize(Plugin::Target &target)
{
    MessageStorePlugin *store = dynamic_cast<MessageStorePlugin *>(&target);
    if (store) {
        // Check the store dir option; if not specified, need to
        // grab the broker's data dir.
        if (options.storeDir.empty()) {
            const DataDir& dir = store->getBroker()->getDataDir();
            if (dir.isEnabled()) {
                options.storeDir = dir.getPath();
            }
            else {
                QPID_LOG(error,
                         "MSSQL-CLFS: --store-dir required if --no-data-dir specified");
                return;
            }
        }

        // If CLFS is not available on this system, give up now.
        try {
            Log::TuningParameters params;
            params.containerSize = options.containerSize;
            params.containers = options.initialContainers;
            params.shrinkPct = 50;
            params.maxWriteBuffers = options.maxWriteBuffers;
            std::string msgPath = options.storeDir + "\\" + "messages";
            messages.openLog(msgPath, params);
            std::string transPath = options.storeDir + "\\" + "transactions";
            transactions->open(transPath, params);
        }
        catch (std::exception &e) {
            QPID_LOG(error, e.what());
            return;
        }

        // If the database init fails, report it and don't register; give
        // the rest of the broker a chance to run.
        //
        // Don't try to initConnection() since that will fail if the
        // database doesn't exist. Instead, try to open a connection without
        // a database name, then search for the database. There's still a
        // chance this provider won't be selected for the store too, so be
        // be sure to close the database connection before return to avoid
        // leaving a connection up that will not be used.
        try {
            initState();     // This initializes COM
            std::auto_ptr<DatabaseConnection> db(new DatabaseConnection());
            db->open(options.connectString, "");
            _ConnectionPtr conn(*db);
            _RecordsetPtr pCatalogs = NULL;
            VariantHelper<std::string> catalogName(options.catalogName);
            pCatalogs = conn->OpenSchema(adSchemaCatalogs, catalogName);
            if (pCatalogs->EndOfFile) {
                // Database doesn't exist; create it
                QPID_LOG(notice,
                         "MSSQL-CLFS: Creating database " + options.catalogName);
                createDb(db.get(), options.catalogName);
            }
            else {
                QPID_LOG(notice,
                         "MSSQL-CLFS: Database located: " + options.catalogName);
            }
            if (pCatalogs) {
                if (pCatalogs->State == adStateOpen)
                    pCatalogs->Close();
                pCatalogs = 0;
            }
            db->close();
            store->providerAvailable("MSSQL-CLFS", this);
        }
        catch (qpid::Exception &e) {
            QPID_LOG(error, e.what());
            return;
        }
        store->addFinalizer(boost::bind(&MSSqlClfsProvider::finalizeMe, this));
    }
}

void
MSSqlClfsProvider::initialize(Plugin::Target& target)
{
}

void
MSSqlClfsProvider::activate(MessageStorePlugin &store)
{
    QPID_LOG(info, "MS SQL/CLFS Provider is up");
}

void
MSSqlClfsProvider::create(PersistableQueue& queue,
                          const qpid::framing::FieldTable& /*args needed for jrnl*/)
{
    DatabaseConnection *db = initConnection();
    BlobRecordset rsQueues;
    try {
        db->beginTransaction();
        rsQueues.open(db, TblQueue);
        rsQueues.add(queue);
        db->commitTransaction();
    }
    catch(_com_error &e) {
        std::string errs = db->getErrors();
        db->rollbackTransaction();
        throw ADOException("Error creating queue " + queue.getName(), e, errs);
    }
    catch(std::exception& e) {
        db->rollbackTransaction();
        THROW_STORE_EXCEPTION(e.what());
    }
}

/**
 * Destroy a durable queue
 */
void
MSSqlClfsProvider::destroy(PersistableQueue& queue)
{
    DatabaseConnection *db = initConnection();
    BlobRecordset rsQueues;
    BindingRecordset rsBindings;
    try {
        db->beginTransaction();
        rsQueues.open(db, TblQueue);
        rsBindings.open(db, TblBinding);
        // Remove bindings first; the queue IDs can't be ripped out from
        // under the references in the bindings table.
        rsBindings.removeForQueue(queue.getPersistenceId());
        rsQueues.remove(queue);
        db->commitTransaction();
    }
    catch(_com_error &e) {
        std::string errs = db->getErrors();
        db->rollbackTransaction();
        throw ADOException("Error deleting queue " + queue.getName(), e, errs);
    }

    /*
     * Now that the SQL stuff has recorded the queue deletion, expunge
     * all record of the queue from the messages set. Any errors logging
     * these removals are swallowed because during a recovery the queue
     * Id won't be present (the SQL stuff already committed) so any references
     * to it in message operations will be removed.
     */
    messages.expunge(queue.getPersistenceId());
}

/**
 * Record the existence of a durable exchange
 */
void
MSSqlClfsProvider::create(const PersistableExchange& exchange,
                          const qpid::framing::FieldTable& args)
{
    DatabaseConnection *db = initConnection();
    BlobRecordset rsExchanges;
    try {
        db->beginTransaction();
        rsExchanges.open(db, TblExchange);
        rsExchanges.add(exchange);
        db->commitTransaction();
    }
    catch(_com_error &e) {
        std::string errs = db->getErrors();
        db->rollbackTransaction();
        throw ADOException("Error creating exchange " + exchange.getName(),
                           e,
                           errs);
    }
}

/**
 * Destroy a durable exchange
 */
void
MSSqlClfsProvider::destroy(const PersistableExchange& exchange)
{
    DatabaseConnection *db = initConnection();
    BlobRecordset rsExchanges;
    BindingRecordset rsBindings;
    try {
        db->beginTransaction();
        rsExchanges.open(db, TblExchange);
        rsBindings.open(db, TblBinding);
        // Remove bindings first; the exchange IDs can't be ripped out from
        // under the references in the bindings table.
        rsBindings.removeForExchange(exchange.getPersistenceId());
        rsExchanges.remove(exchange);
        db->commitTransaction();
    }
    catch(_com_error &e) {
        std::string errs = db->getErrors();
        db->rollbackTransaction();
        throw ADOException("Error deleting exchange " + exchange.getName(),
                           e,
                           errs);
    }
}

/**
 * Record a binding
 */
void
MSSqlClfsProvider::bind(const PersistableExchange& exchange,
                        const PersistableQueue& queue,
                        const std::string& key,
                        const qpid::framing::FieldTable& args)
{
    DatabaseConnection *db = initConnection();
    BindingRecordset rsBindings;
    try {
        db->beginTransaction();
        rsBindings.open(db, TblBinding);
        rsBindings.add(exchange.getPersistenceId(),
                       queue.getPersistenceId(),
                       key,
                       args);
        db->commitTransaction();
    }
    catch(_com_error &e) {
        std::string errs = db->getErrors();
        db->rollbackTransaction();
        throw ADOException("Error binding exchange " + exchange.getName() +
                           " to queue " + queue.getName(),
                           e,
                           errs);
    }
}

/**
 * Forget a binding
 */
void
MSSqlClfsProvider::unbind(const PersistableExchange& exchange,
                          const PersistableQueue& queue,
                          const std::string& key,
                          const qpid::framing::FieldTable& args)
{
    DatabaseConnection *db = initConnection();
    BindingRecordset rsBindings;
    try {
        db->beginTransaction();
        rsBindings.open(db, TblBinding);
        rsBindings.remove(exchange.getPersistenceId(),
                          queue.getPersistenceId(),
                          key,
                          args);
        db->commitTransaction();
    }
    catch(_com_error &e) {
        std::string errs = db->getErrors();
        db->rollbackTransaction();
        throw ADOException("Error unbinding exchange " + exchange.getName() +
                           " from queue " + queue.getName(),
                           e,
                           errs);
    }
}

/**
 * Record generic durable configuration
 */
void
MSSqlClfsProvider::create(const PersistableConfig& config)
{
    DatabaseConnection *db = initConnection();
    BlobRecordset rsConfigs;
    try {
        db->beginTransaction();
        rsConfigs.open(db, TblConfig);
        rsConfigs.add(config);
        db->commitTransaction();
    }
    catch(_com_error &e) {
        std::string errs = db->getErrors();
        db->rollbackTransaction();
        throw ADOException("Error creating config " + config.getName(), e, errs);
    }
}

/**
 * Destroy generic durable configuration
 */
void
MSSqlClfsProvider::destroy(const PersistableConfig& config)
{
    DatabaseConnection *db = initConnection();
    BlobRecordset rsConfigs;
    try {
        db->beginTransaction();
        rsConfigs.open(db, TblConfig);
        rsConfigs.remove(config);
        db->commitTransaction();
    }
    catch(_com_error &e) {
        std::string errs = db->getErrors();
        db->rollbackTransaction();
        throw ADOException("Error deleting config " + config.getName(), e, errs);
    }
}

/**
 * Stores a messages before it has been enqueued
 * (enqueueing automatically stores the message so this is
 * only required if storage is required prior to that
 * point). If the message has not yet been stored it will
 * store the headers as well as any content passed in. A
 * persistence id will be set on the message which can be
 * used to load the content or to append to it.
 */
void
MSSqlClfsProvider::stage(const boost::intrusive_ptr<PersistableMessage>& msg)
{
#if 0
    DatabaseConnection *db = initConnection();
    MessageRecordset rsMessages;
    try {
        db->beginTransaction();
        rsMessages.open(db, TblMessage);
        rsMessages.add(msg);
        db->commitTransaction();
    }
    catch(_com_error &e) {
        std::string errs = db->getErrors();
        db->rollbackTransaction();
        throw ADOException("Error staging message", e, errs);
    }
#endif
}

/**
 * Destroys a previously staged message. This only needs
 * to be called if the message is never enqueued. (Once
 * enqueued, deletion will be automatic when the message
 * is dequeued from all queues it was enqueued onto).
 */
void
MSSqlClfsProvider::destroy(PersistableMessage& msg)
{
#if 0
    DatabaseConnection *db = initConnection();
    BlobRecordset rsMessages;
    try {
        db->beginTransaction();
        rsMessages.open(db, TblMessage);
        rsMessages.remove(msg);
        db->commitTransaction();
    }
    catch(_com_error &e) {
        std::string errs = db->getErrors();
        db->rollbackTransaction();
        throw ADOException("Error deleting message", e, errs);
    }
#endif
}

/**
 * Appends content to a previously staged message
 */
void
MSSqlClfsProvider::appendContent(const boost::intrusive_ptr<const PersistableMessage>& msg,
                                 const std::string& data)
{
#if 0
    DatabaseConnection *db = initConnection();
    MessageRecordset rsMessages;
    try {
        db->beginTransaction();
        rsMessages.open(db, TblMessage);
        rsMessages.append(msg, data);
        db->commitTransaction();
    }
    catch(_com_error &e) {
        std::string errs = db->getErrors();
        db->rollbackTransaction();
        throw ADOException("Error appending to message", e, errs);
    }  
#endif
}

/**
 * Loads (a section) of content data for the specified
 * message (previously stored through a call to stage or
 * enqueue) into data. The offset refers to the content
 * only (i.e. an offset of 0 implies that the start of the
 * content should be loaded, not the headers or related
 * meta-data).
 */
void
MSSqlClfsProvider::loadContent(const qpid::broker::PersistableQueue& /*queue*/,
                               const boost::intrusive_ptr<const PersistableMessage>& msg,
                               std::string& data,
                               uint64_t offset,
                               uint32_t length)
{
    // Message log keeps all messages in one log, so we don't need the
    // queue reference.
    messages.loadContent(msg->getPersistenceId(), data, offset, length);
}

/**
 * Enqueues a message, storing the message if it has not
 * been previously stored and recording that the given
 * message is on the given queue.
 *
 * @param ctxt The transaction context under which this enqueue happens.
 * @param msg The message to enqueue
 * @param queue the name of the queue onto which it is to be enqueued
 */
void
MSSqlClfsProvider::enqueue(qpid::broker::TransactionContext* ctxt,
                           const boost::intrusive_ptr<PersistableMessage>& msg,
                           const PersistableQueue& queue)
{
    Transaction::shared_ptr t;
    TransactionContext *ctx = dynamic_cast<TransactionContext*>(ctxt);
    if (ctx)
        t = ctx->getTransaction();
    else {
        TPCTransactionContext *tctx;
        tctx = dynamic_cast<TPCTransactionContext*>(ctxt);
        if (tctx)
            t = tctx->getTransaction();
    }
    uint64_t msgId = msg->getPersistenceId();
    if (msgId == 0) {
        messages.add(msg);
        msgId = msg->getPersistenceId();
    }
    messages.enqueue(msgId, queue.getPersistenceId(), t);
    msg->enqueueComplete();
}

/**
 * Dequeues a message, recording that the given message is
 * no longer on the given queue and deleting the message
 * if it is no longer on any other queue.
 *
 * @param ctxt The transaction context under which this dequeue happens.
 * @param msg The message to dequeue
 * @param queue The queue from which it is to be dequeued
 */
void
MSSqlClfsProvider::dequeue(qpid::broker::TransactionContext* ctxt,
                           const boost::intrusive_ptr<PersistableMessage>& msg,
                           const PersistableQueue& queue)
{
    Transaction::shared_ptr t;
    TransactionContext *ctx = dynamic_cast<TransactionContext*>(ctxt);
    if (ctx)
        t = ctx->getTransaction();
    else {
        TPCTransactionContext *tctx;
        tctx = dynamic_cast<TPCTransactionContext*>(ctxt);
        if (tctx)
            t = tctx->getTransaction();
    }
    messages.dequeue(msg->getPersistenceId(), queue.getPersistenceId(), t);
    msg->dequeueComplete();
}

std::auto_ptr<qpid::broker::TransactionContext>
MSSqlClfsProvider::begin()
{
    Transaction::shared_ptr t = transactions->begin();
    std::auto_ptr<qpid::broker::TransactionContext> tc(new TransactionContext(t));
    return tc;
}

std::auto_ptr<qpid::broker::TPCTransactionContext>
MSSqlClfsProvider::begin(const std::string& xid)
{
    TPCTransaction::shared_ptr t = transactions->begin(xid);
    std::auto_ptr<qpid::broker::TPCTransactionContext> tc(new TPCTransactionContext(t));
    return tc;
}

void
MSSqlClfsProvider::prepare(qpid::broker::TPCTransactionContext& txn)
{
    TPCTransactionContext *ctx = dynamic_cast<TPCTransactionContext*> (&txn);
    if (ctx == 0)
        throw qpid::broker::InvalidTransactionContextException();
    ctx->getTransaction()->prepare();
}

void
MSSqlClfsProvider::commit(qpid::broker::TransactionContext& txn)
{
    Transaction::shared_ptr t;
    TransactionContext *ctx = dynamic_cast<TransactionContext*>(&txn);
    if (ctx)
        t = ctx->getTransaction();
    else {
        TPCTransactionContext *tctx;
        tctx = dynamic_cast<TPCTransactionContext*>(&txn);
        if (tctx == 0)
            throw qpid::broker::InvalidTransactionContextException();
        t = tctx->getTransaction();
    }
    t->commit(messages);
}

void
MSSqlClfsProvider::abort(qpid::broker::TransactionContext& txn)
{
    Transaction::shared_ptr t;
    TransactionContext *ctx = dynamic_cast<TransactionContext*>(&txn);
    if (ctx)
        t = ctx->getTransaction();
    else {
        TPCTransactionContext *tctx;
        tctx = dynamic_cast<TPCTransactionContext*>(&txn);
        if (tctx == 0)
            throw qpid::broker::InvalidTransactionContextException();
        t = tctx->getTransaction();
    }
    t->abort(messages);
}

void
MSSqlClfsProvider::collectPreparedXids(std::set<std::string>& xids)
{
    std::map<std::string, TPCTransaction::shared_ptr> preparedMap;
    transactions->collectPreparedXids(preparedMap);
    std::map<std::string, TPCTransaction::shared_ptr>::const_iterator i;
    for (i = preparedMap.begin(); i != preparedMap.end(); ++i) {
        xids.insert(i->first);
    }
}

// @TODO Much of this recovery code is way too similar... refactor to
// a recover template method on BlobRecordset.

void
MSSqlClfsProvider::recoverConfigs(qpid::broker::RecoveryManager& recoverer)
{
    DatabaseConnection *db = initConnection();
    BlobRecordset rsConfigs;
    rsConfigs.open(db, TblConfig);
    _RecordsetPtr p = (_RecordsetPtr)rsConfigs;
    if (p->BOF && p->EndOfFile)
        return;   // Nothing to do
    p->MoveFirst();
    while (!p->EndOfFile) {
        uint64_t id = p->Fields->Item["persistenceId"]->Value;
        long blobSize = p->Fields->Item["fieldTableBlob"]->ActualSize;
        BlobAdapter blob(blobSize);
        blob = p->Fields->Item["fieldTableBlob"]->GetChunk(blobSize);
        // Recreate the Config instance and reset its ID.
        broker::RecoverableConfig::shared_ptr config =
            recoverer.recoverConfig(blob);
        config->setPersistenceId(id);
        p->MoveNext();
    }
}

void
MSSqlClfsProvider::recoverExchanges(qpid::broker::RecoveryManager& recoverer,
                                    ExchangeMap& exchangeMap)
{
    DatabaseConnection *db = initConnection();
    BlobRecordset rsExchanges;
    rsExchanges.open(db, TblExchange);
    _RecordsetPtr p = (_RecordsetPtr)rsExchanges;
    if (p->BOF && p->EndOfFile)
        return;   // Nothing to do
    p->MoveFirst();
    while (!p->EndOfFile) {
        uint64_t id = p->Fields->Item["persistenceId"]->Value;
        long blobSize = p->Fields->Item["fieldTableBlob"]->ActualSize;
        BlobAdapter blob(blobSize);
        blob = p->Fields->Item["fieldTableBlob"]->GetChunk(blobSize);
        // Recreate the Exchange instance, reset its ID, and remember the
        // ones restored for matching up when recovering bindings.
        broker::RecoverableExchange::shared_ptr exchange =
            recoverer.recoverExchange(blob);
        exchange->setPersistenceId(id);
        exchangeMap[id] = exchange;
        p->MoveNext();
    }
}

void
MSSqlClfsProvider::recoverQueues(qpid::broker::RecoveryManager& recoverer,
                                 QueueMap& queueMap)
{
    DatabaseConnection *db = initConnection();
    BlobRecordset rsQueues;
    rsQueues.open(db, TblQueue);
    _RecordsetPtr p = (_RecordsetPtr)rsQueues;
    if (p->BOF && p->EndOfFile)
        return;   // Nothing to do
    p->MoveFirst();
    while (!p->EndOfFile) {
        uint64_t id = p->Fields->Item["persistenceId"]->Value;
        long blobSize = p->Fields->Item["fieldTableBlob"]->ActualSize;
        BlobAdapter blob(blobSize);
        blob = p->Fields->Item["fieldTableBlob"]->GetChunk(blobSize);
        // Recreate the Queue instance and reset its ID.
        broker::RecoverableQueue::shared_ptr queue =
            recoverer.recoverQueue(blob);
        queue->setPersistenceId(id);
        queueMap[id] = queue;
        p->MoveNext();
    }
}

void
MSSqlClfsProvider::recoverBindings(qpid::broker::RecoveryManager& recoverer,
                                   const ExchangeMap& exchangeMap,
                                   const QueueMap& queueMap)
{
    DatabaseConnection *db = initConnection();
    BindingRecordset rsBindings;
    rsBindings.open(db, TblBinding);
    rsBindings.recover(recoverer, exchangeMap, queueMap);
}

void
MSSqlClfsProvider::recoverMessages(qpid::broker::RecoveryManager& recoverer,
                                   MessageMap& messageMap,
                                   MessageQueueMap& messageQueueMap)
{
    // Read the list of valid queue Ids to ensure that no broken msg->queue
    // refs get restored.
    DatabaseConnection *db = initConnection();
    BlobRecordset rsQueues;
    rsQueues.open(db, TblQueue);
    _RecordsetPtr p = (_RecordsetPtr)rsQueues;
    std::set<uint64_t> validQueues;
    if (!(p->BOF && p->EndOfFile)) {
        p->MoveFirst();
        while (!p->EndOfFile) {
            uint64_t id = p->Fields->Item["persistenceId"]->Value;
            validQueues.insert(id);
            p->MoveNext();
        }
    }
    std::map<uint64_t, Transaction::shared_ptr> transMap;
    transactions->recover(transMap);
    messages.recover(recoverer,
                     validQueues,
                     transMap,
                     messageMap,
                     messageQueueMap);
}

void
MSSqlClfsProvider::recoverTransactions(qpid::broker::RecoveryManager& recoverer,
                                       PreparedTransactionMap& dtxMap)
{
    std::map<std::string, TPCTransaction::shared_ptr> preparedMap;
    transactions->collectPreparedXids(preparedMap);
    std::map<std::string, TPCTransaction::shared_ptr>::const_iterator i;
    for (i = preparedMap.begin(); i != preparedMap.end(); ++i) {
        std::auto_ptr<TPCTransactionContext> ctx(new TPCTransactionContext(i->second));
        std::auto_ptr<qpid::broker::TPCTransactionContext> brokerCtx(ctx);
        dtxMap[i->first] = recoverer.recoverTransaction(i->first, brokerCtx);
    }
}

////////////// Internal Methods

State *
MSSqlClfsProvider::initState()
{
    State *state = dbState.get();   // See if thread has initialized
    if (!state) {
        state = new State;
        dbState.reset(state);
    }
    return state;
}
  
DatabaseConnection *
MSSqlClfsProvider::initConnection(void)
{
    State *state = initState();
    if (state->dbConn != 0)
        return state->dbConn;    // And the DatabaseConnection is set up too
    std::auto_ptr<DatabaseConnection> db(new DatabaseConnection);
    db->open(options.connectString, options.catalogName);
    state->dbConn = db.release();
    return state->dbConn;
}

void
MSSqlClfsProvider::createDb(DatabaseConnection *db, const std::string &name)
{
    const std::string dbCmd = "CREATE DATABASE " + name;
    const std::string useCmd = "USE " + name;
    const std::string tableCmd = "CREATE TABLE ";
    const std::string colSpecs =
        " (persistenceId bigint PRIMARY KEY NOT NULL IDENTITY(1,1),"
        "  fieldTableBlob varbinary(MAX) NOT NULL)";
    const std::string bindingSpecs =
        " (exchangeId bigint REFERENCES tblExchange(persistenceId) NOT NULL,"
        "  queueId bigint REFERENCES tblQueue(persistenceId) NOT NULL,"
        "  routingKey varchar(255),"
        "  fieldTableBlob varbinary(MAX))";

    _variant_t unused;
    _bstr_t dbStr = dbCmd.c_str();
    _ConnectionPtr conn(*db);
    try {
        conn->Execute(dbStr, &unused, adExecuteNoRecords);
        _bstr_t useStr = useCmd.c_str();
        conn->Execute(useStr, &unused, adExecuteNoRecords);
        std::string makeTable = tableCmd + TblQueue + colSpecs;
        _bstr_t makeTableStr = makeTable.c_str();
        conn->Execute(makeTableStr, &unused, adExecuteNoRecords);
        makeTable = tableCmd + TblExchange + colSpecs;
        makeTableStr = makeTable.c_str();
        conn->Execute(makeTableStr, &unused, adExecuteNoRecords);
        makeTable = tableCmd + TblConfig + colSpecs;
        makeTableStr = makeTable.c_str();
        conn->Execute(makeTableStr, &unused, adExecuteNoRecords);
        makeTable = tableCmd + TblBinding + bindingSpecs;
        makeTableStr = makeTable.c_str();
        conn->Execute(makeTableStr, &unused, adExecuteNoRecords);
    }
    catch(_com_error &e) {
        throw ADOException("MSSQL can't create " + name, e, db->getErrors());
    }
}

void
MSSqlClfsProvider::dump()
{
  // dump all db records to qpid_log
  QPID_LOG(notice, "DB Dump: (not dumping anything)");
  //  rsQueues.dump();
}


}}} // namespace qpid::store::ms_sql
