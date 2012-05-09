/*
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
 */

/**
 * \file AsyncJournal.h
 */

#ifndef qpid_asyncStore_jrnl2_AsyncJournal_h_
#define qpid_asyncStore_jrnl2_AsyncJournal_h_

#include "JournalDirectory.h"
#include "JournalRunState.h"
#include "ScopedLock.h"

#include <string>
#include <stdint.h> // uint64_t, uint32_t, etc.

// --- temp code ---
#include <vector>
// --- end temp code ---

namespace qpid {        ///< Namespace for top-level qpid domain
namespace asyncStore {  ///< Namespace for AsyncStore code
namespace jrnl2 {       ///< Namespace for AsyncStore journal v.2 code.

class AioCallback;
class DataToken;
class JournalParameters;

/**
 * \brief Type to return results from journal operations.
 * \todo TODO - decide if this is the right place to expose these codes and flags. Also express ioRes as flags.
 */
typedef uint64_t jrnlOpRes;

const jrnlOpRes RHM_IORES_ENQCAPTHRESH = 0x1;   ///< Error flag indicating an enqueue capacity threshold was reached.
const jrnlOpRes RHM_IORES_BUSY = 0x2;           ///< Error flag indicating that a call could not be completed because the Store is busy.

/**
 * \brief Global function to convert a return code into a textual representation.
 */
std::string g_ioResAsString(const jrnlOpRes /*res*/);

/**
 * \brief Top level of a single journal instance, which is usually deployed on a per-queue basis.
 *
 * Each journal has its own set of journal files and when recovered will result in restored data (messages) being
 * sent to the queue to which this instance is connected.
 */
class AsyncJournal
{
public:
    /**
     * \brief Constructor, which creates a single instance of a journal.
     *
     * \param jrnlId Journal identifier (JID), typically the name of the queue with which this journal
     * is associated.
     * \param jrnlDir Absolute path to the directory in which the journal will be placed. If the path does not exist,
     * it will be created.
     * \param baseFileName The base name of all files in the journal directory.
     */
    AsyncJournal(const std::string& jrnlId,
                 const std::string& jrnlDir,
                 const std::string& baseFileName);

    // Get functions

    /**
     * \brief Get the journal identifier (JID).
     *
     * \returns Journal identifier (JID) of this journal instance.
     */
    std::string getId() const;

    /**
     * \brief Get the URI or directory where this journal is deployed (writes its files).
     *
     * \returns JournalDirectory instance controlling where this journal instance is deployed.
     */
    JournalDirectory getJournalDir() const;

    /**
     * \brief Get the URI or directory where this journal is deployed (writes its files).
     *
     * \returns URI or directory (as a std::string) where this journal instance is deployed.
     */
    std::string getJournalDirName() const;

    /**
     * \brief Get the base file name used for all journal and metadata files associated with this journal instance.
     *
     * \returns String containing the base file name used for all files associated with this journal instance.
     */
    std::string getBaseFileName() const;

    /**
     * \brief Get the journal state object, which can be queried for the journal state.
     *
     * \returns \c const reference to the JournalSate instance associated with this journal instance.
     */
    const JournalRunState& getState() const;

    /**
     * \brief Return the Journal parameter object which contains the journal options and settings. It may be
     * queried directly to obtain the options and settings.
     *
     * \returns Pointer to the JournalParameters instance associated with this journal instance.
     */
    const JournalParameters* getParameters() const;

    // Data ops

    /**
     * \brief Initialize the journal and its files, making it ready for use.
     *
     * \param jpPtr Pointer to an instance of JournalParameters containing the journal options and settings.
     * \param aiocbPtr Pointer to an instance of AioCallback, whcih sets the broker AIO callback functions.
     *
     * \todo TODO: Make this call async for large/slow ops
     */
    void initialize(const JournalParameters* jpPtr,
                    AioCallback* const aiocbPtr);

    /**
     * \brief Enqueue data of size dataLen and pointed to by dataPtr. If transactional, then tidPtr points to
     * the transaction id and tidLen indicates its size. The DataToken instance must be kept in the service of this
     * data record until it has been fully dequeued.
     *
     * \param dtokPtr Pointer to the DataToken object of a previouisly enqueued data record.
     * \param dataPtr Pointer to data to be stored (enqueued). If \b NULL, when parameter \a dataLen > 0, then this
     * is assumed to be an externally stored data record.
     * \param dataLen Length of the data pointed to
     * \param tidPtr Pointer to the transaction ID for this operation. If \b NULL together with \a tidLen, then
     * the operation is non-transactional.
     * \param tidLen Size of the transaction ID pointed to in parameter \a tidPtr. Must be 0 when \a tidPtr is
     * \b NULL to make non-transactional.
     * \param transientFlag If \b true, sets a flag indicating that on recover, this record is to be ignored and
     * treated as if transient (rather than durable).
     *
     * \returns Return code for this operation. A zero value (0x0) indicates success, a non-zero value indicates
     * an error has occurred or an issue is present.
     */
    jrnlOpRes enqueue(DataToken* dtokPtr,
                      const void* dataPtr,       // if null and dataLen > 0, extern assumed
                      const std::size_t dataLen,
                      const void* tidPtr,        // if null and tidLen == 0, non transactional
                      const std::size_t tidLen,
                      const bool transientFlag);

    /**
     * \brief Dequeue the data record previously enqueued using the DataToken object dtokPtr.
     *
     * \param dtokPtr Pointer to the DataToken object of a previouisly enqueued data record.
     * \param tidPtr Pointer to the transaction ID for this operation. If \b NULL together with \a tidLen, then
     * the operation is non-transactional.
     * \param tidLen Size of the transaction ID pointed to in parameter \a tidPtr. Must be 0 when \a tidPtr is
     * \b NULL to make non-transactional.
     * \returns Return code for this operation. A zero value (0x0) indicates success, a non-zero value indicates
     * an error has occurred or an issue is present.
     */
    jrnlOpRes dequeue(DataToken* const dtokPtr,
                      const void* tidPtr,        // if null and tidLen == 0, non transactional
                      const std::size_t tidLen);

    /**
     * \brief Commit the transaction Id (XID) used for previoius enqueue(s) and/or dequeue(s).
     *
     * \return Return code for this operation. A zero value (0x0) indicates success, a non-zero value indicates
     * an error has occurred or an issue is present.
     *
     * \todo TODO: Create and add an XID type as a parameter to this call.
     */
    jrnlOpRes commit();

    /**
     * \brief Abort (roll back) the transaction Id (XID) used for previoius enqueue(s) and/or dequeue(s).
     *
     * \return Return code for this operation. A zero value (0x0) indicates success, a non-zero value indicates
     * an error has occurred or an issue is present.
     *
     * \todo TODO: Create and add an XID type as a parameter to this call.
     */
    jrnlOpRes abort();

    // AIO ops and status

    /**
     * \brief Flush all unwritten buffered records for this journal.
     */
    jrnlOpRes flush();

    /**
     * \brief Wait until all AIOs outstanding at the last flush() have returned for this journal.
     *
     * It is assumed that flush() will have been previously called. This call by definition blocks until \a timeout
     * has elapsed or all AIO operations outstanding at the last flush() call have returned. A zero timeout implies
     * an indefinite block.
     */
    jrnlOpRes sync(const double timeout = 0.0);

    /**
     * \brief Search for and process completed AIO write events.
     *
     * \param timeout Maximum time to wait for completion, otherwise return without performing any work.
     *
     * \todo TODO: This may become obsolete if epoll is used instead.
     * \todo TODO: Should this return the number of completed AIO events?
     */
    void processCompletedAioWriteEvents(const double timeout = 0.0);

protected:
    std::string m_jrnlId;                       ///< Identifier for this journal instance (JID), typically queue name.
    JournalDirectory m_jrnlDir;                 ///< Directory in which this journal is deployed.
    std::string m_baseFileName;                 ///< Base file name used for all journal files belonging to this instance.
    JournalRunState m_jrnlState;                ///< Journal state manager, controls the state of this journal.
    const JournalParameters* m_jrnlParamsPtr;   ///< Journal options and parameters associated with this journal.
    AioCallback* m_aioCallbackPtr;              ///< Pointers to the broker's callback functions for AIO completion callbacks.

    // --- temp code ---
    static const uint32_t s_listSizeThreshold = 250;    ///< [TEMP CODE] Number of data records at which a flush will occur.
    std::vector<DataToken*> m_writeDataTokens;          ///< [TEMP CODE] List of data tokens held before a flush.
    std::vector<DataToken*> m_callbackDataTokens;       ///< [TEMP CODE] List of data tokens ready for callbacks.
    ScopedMutex m_writeDataTokensLock;                  ///< [TEMP CODE] Lock to protect the write token list.
    ScopedMutex m_callbackDataTokensLock;               ///< [TEMP CODE] Lock to protect the callback token list.
    // --- end temp code ---

    /**
     * \brief Internal-use flush call which operates without taking a lock.
     */
    jrnlOpRes flushNoLock();

    /**
     * \brief Internal-use sync call which operates without taking a lock.
     */
    jrnlOpRes syncNoLock(const double timeout);

};

}}} // namespace qpid::asyncStore::jrnl2

#endif // qpid_asyncStore_jrnl2_AsyncJournal_h_

