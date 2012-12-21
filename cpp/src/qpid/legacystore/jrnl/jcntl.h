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

/**
 * \file jcntl.h
 *
 * Qpid asynchronous store plugin library
 *
 * Messaging journal top-level control and interface class
 * mrg::journal::jcntl. See class documentation for details.
 *
 * \author Kim van der Riet
 */

#ifndef QPID_LEGACYSTORE_JRNL_JCNTL_H
#define QPID_LEGACYSTORE_JRNL_JCNTL_H

namespace mrg
{
namespace journal
{
    class jcntl;
}
}

#include <cstddef>
#include <deque>
#include "qpid/legacystore/jrnl/jdir.h"
#include "qpid/legacystore/jrnl/fcntl.h"
#include "qpid/legacystore/jrnl/lpmgr.h"
#include "qpid/legacystore/jrnl/rcvdat.h"
#include "qpid/legacystore/jrnl/slock.h"
#include "qpid/legacystore/jrnl/smutex.h"
#include "qpid/legacystore/jrnl/rmgr.h"
#include "qpid/legacystore/jrnl/wmgr.h"
#include "qpid/legacystore/jrnl/wrfc.h"

namespace mrg
{
namespace journal
{

    /**
    * \brief Access and control interface for the journal. This is the top-level class for the
    *     journal.
    *
    * This is the top-level journal class; one instance of this class controls one instance of the
    * journal and all its files and associated control structures. Besides this class, the only
    * other class that needs to be used at a higher level is the data_tok class, one instance of
    * which is used per data block written to the journal, and is used to track its status through
    * the AIO enqueue, read and dequeue process.
    */
    class jcntl
    {
    protected:
        /**
        * \brief Journal ID
        *
        * This string uniquely identifies this journal instance. It will most likely be associated
        * with the identity of the message queue with which it is associated.
        */
        // TODO: This is not included in any files at present, add to file_hdr?
        std::string _jid;

        /**
        * \brief Journal directory
        *
        * This string stores the path to the journal directory. It may be absolute or relative, and
        * should not end in a file separator character. (e.g. "/fastdisk/jdata" is correct,
        * "/fastdisk/jdata/" is not.)
        */
        jdir _jdir;

        /**
        * \brief Base filename
        *
        * This string contains the base filename used for the journal files. The filenames will
        * start with this base, and have various sections added to it to derive the final file names
        * that will be written to disk. No file separator characters should be included here, but
        * all other legal filename characters are valid.
        */
        std::string _base_filename;

        /**
        * \brief Initialized flag
        *
        * This flag starts out set to false, is set to true once this object has been initialized,
        * either by calling initialize() or recover().
        */
        bool _init_flag;

        /**
        * \brief Stopped flag
        *
        * This flag starts out false, and is set to true when stop() is called. At this point, the
        * journal will no longer accept messages until either initialize() or recover() is called.
        * There is no way other than through initialization to reset this flag.
        */
        // TODO: It would be helpful to distinguish between states stopping and stopped. If stop(true) is called,
        // then we are stopping, but must wait for all outstanding aios to return before being finally stopped. During
        // this period, however, no new enqueue/dequeue/read requests may be accepted.
        bool _stop_flag;

        /**
        * \brief Read-only state flag used during recover.
        *
        * When true, this flag prevents journal write operations (enqueue and dequeue), but
        * allows read to occur. It is used during recovery, and is reset when recovered() is
        * called.
        */
        bool _readonly_flag;

        /**
        * \brief If set, calls stop() if the jouranl write pointer overruns dequeue low water
        *     marker. If not set, then attempts to write will throw exceptions until the journal
        *     file low water marker moves to the next journal file.
        */
        bool _autostop;             ///< Autostop flag - stops journal when overrun occurs

        // Journal control structures
        u_int32_t _jfsize_sblks;    ///< Journal file size in sblks
        lpmgr _lpmgr;               ///< LFID-PFID manager tracks inserted journal files
        enq_map _emap;              ///< Enqueue map for low water mark management
        txn_map _tmap;              ///< Transaction map open transactions
        rrfc _rrfc;                 ///< Read journal rotating file controller
        wrfc _wrfc;                 ///< Write journal rotating file controller
        rmgr _rmgr;                 ///< Read page manager which manages AIO
        wmgr _wmgr;                 ///< Write page manager which manages AIO
        rcvdat _rcvdat;             ///< Recovery data used for recovery
        smutex _wr_mutex;           ///< Mutex for journal writes

    public:
        static timespec _aio_cmpl_timeout; ///< Timeout for blocking libaio returns
        static timespec _final_aio_cmpl_timeout; ///< Timeout for blocking libaio returns when stopping or finalizing

        /**
        * \brief Journal constructor.
        *
        * Constructor which sets the physical file location and base name.
        *
        * \param jid A unique identifier for this journal instance.
        * \param jdir The directory which will contain the journal files.
        * \param base_filename The string which will be used to start all journal filenames.
        */
        jcntl(const std::string& jid, const std::string& jdir, const std::string& base_filename);

        /**
        * \brief Destructor.
        */
        virtual ~jcntl();

        inline const std::string& id() const { return _jid; }
        inline const std::string& jrnl_dir() const { return _jdir.dirname(); }

        /**
        * \brief Initialize the journal for storing data.
        *
        * Initialize the journal by creating new journal data files and initializing internal
        * control structures. When complete, the journal will be empty, and ready to store data.
        *
        * <b>NOTE: Any existing journal will be ignored by this operation.</b> To use recover
        * the data from an existing journal, use recover().
        *
        * <b>NOTE: If <i>NULL</i> is passed to the deque pointers, they will be internally created
        * and deleted.</b>
        *
        * <b>NOTE: If <i>NULL</i> is passed to the callbacks, internal default callbacks will be
        * used.</b>
        *
        * \param num_jfiles The number of journal files to be created.
        * \param auto_expand If true, allows journal file auto-expansion. In this mode, the journal will automatically
        *     add files to the journal if it runs out of space. No more than ae_max_jfiles may be added. If false, then
        *     no files are added and an exception will be thrown if the journal runs out of file space.
        * \param ae_max_jfiles Upper limit of journal files for auto-expand mode. When auto_expand is true, this is the
        *     maximum total number of files allowed in the journal (original plus those added by auto-expand mode). If
        *     this number of files exist and the journal runs out of space, an exception will be thrown. This number
        *     must be greater than the num_jfiles parameter value but cannot exceed the maximum number of files for a
        *     single journal; if num_jfiles is already at its maximum value, then auto-expand will be disabled.
        * \param jfsize_sblks The size of each journal file expressed in softblocks.
        * \param wcache_num_pages The number of write cache pages to create.
        * \param wcache_pgsize_sblks The size in sblks of each write cache page.
        * \param cbp Pointer to object containing callback functions for read and write operations. May be 0 (NULL).
        *
        * \exception TODO
        */
        void initialize(const u_int16_t num_jfiles, const bool auto_expand, const u_int16_t ae_max_jfiles,
                const u_int32_t jfsize_sblks, const u_int16_t wcache_num_pages, const u_int32_t wcache_pgsize_sblks,
                aio_callback* const cbp);

        /**
        * /brief Initialize journal by recovering state from previously written journal.
        *
        * Initialize journal by recovering state from previously written journal. The journal files
        * are analyzed, and all records that have not been dequeued and that remain in the journal
        * will be available for reading. The journal is placed in a read-only state until
        * recovered() is called; any calls to enqueue or dequeue will fail with an exception
        * in this state.
        *
        * <b>NOTE: If <i>NULL</i> is passed to the deque pointers, they will be internally created
        * and deleted.</b>
        *
        * <b>NOTE: If <i>NULL</i> is passed to the callbacks, internal default callbacks will be
        * used.</b>
        *
        * \param num_jfiles The number of journal files to be created.
        * \param auto_expand If true, allows journal file auto-expansion. In this mode, the journal will automatically
        *     add files to the journal if it runs out of space. No more than ae_max_jfiles may be added. If false, then
        *     no files are added and an exception will be thrown if the journal runs out of file space.
        * \param ae_max_jfiles Upper limit of journal files for auto-expand mode. When auto_expand is true, this is the
        *     maximum total number of files allowed in the journal (original plus those added by auto-expand mode). If
        *     this number of files exist and the journal runs out of space, an exception will be thrown. This number
        *     must be greater than the num_jfiles parameter value but cannot exceed the maximum number of files for a
        *     single journal; if num_jfiles is already at its maximum value, then auto-expand will be disabled.
        * \param jfsize_sblks The size of each journal file expressed in softblocks.
        * \param wcache_num_pages The number of write cache pages to create.
        * \param wcache_pgsize_sblks The size in sblks of each write cache page.
        * \param cbp Pointer to object containing callback functions for read and write operations. May be 0 (NULL).
        * \param prep_txn_list_ptr
        * \param highest_rid Returns the highest rid found in the journal during recover
        *
        * \exception TODO
        */
        void recover(const u_int16_t num_jfiles, const bool auto_expand, const u_int16_t ae_max_jfiles,
                const u_int32_t jfsize_sblks, const u_int16_t wcache_num_pages, const u_int32_t wcache_pgsize_sblks,
                aio_callback* const cbp, const std::vector<std::string>* prep_txn_list_ptr, u_int64_t& highest_rid);

        /**
        * \brief Notification to the journal that recovery is complete and that normal operation
        *     may resume.
        *
        * This call notifies the journal that recovery is complete and that normal operation
        * may resume. The read pointers are reset so that all records read as a part of recover
        * may  be re-read during normal operation. The read-only flag is then reset, allowing
        * enqueue and dequeue operations to resume.
        *
        * \exception TODO
        */
        void recover_complete();

        /**
        * \brief Stops journal and deletes all journal files.
        *
        * Clear the journal directory of all journal files matching the base filename.
        *
        * \exception TODO
        */
        void delete_jrnl_files();

        /**
        * \brief Enqueue data.
        *
        * Enqueue data or part thereof. If a large data block is being written, then it may be
        * enqueued in parts by setting this_data_len to the size of the data being written in this
        * call. The total data size must be known in advance, however, as this is written into the
        * record header on the first record write. The state of the write (i.e. how much has been
        * written so far) is maintained in the data token dtokp. Partial writes will return in state
        * ENQ_PART.
        *
        * Note that a return value of anything other than RHM_IORES_SUCCESS implies that this write
        * operation did not complete successfully or was partially completed. The action taken under
        * these conditions depends on the value of the return. For example, RHM_IORES_AIO_WAIT
        * implies that all pages in the write page cache are waiting for AIO operations to return,
        * and that the call should be remade after waiting a bit.
        *
        * Example: If a write of 99 kB is divided into three equal parts, then the following states
        * and returns would characterize a successful operation:
        * <pre>
        *                            dtok.    dtok.   dtok.
        * Pperation         Return   wstate() dsize() written() Comment
        * -----------------+--------+--------+-------+---------+------------------------------------
        *                            NONE     0       0         Value of dtok before op
        * edr(99000, 33000) SUCCESS  ENQ_PART 99000   33000     Enqueue part 1
        * edr(99000, 33000) AIO_WAIT ENQ_PART 99000   50000     Enqueue part 2, not completed
        * edr(99000, 33000) SUCCESS  ENQ_PART 99000   66000     Enqueue part 2 again
        * edr(99000, 33000) SUCCESS  ENQ      99000   99000     Enqueue part 3
        * </pre>
        *
        * \param data_buff Pointer to data to be enqueued for this enqueue operation.
        * \param tot_data_len Total data length.
        * \param this_data_len Amount to be written in this enqueue operation.
        * \param dtokp Pointer to data token which contains the details of the enqueue operation.
        * \param transient Flag indicating transient persistence (ie, ignored on recover).
        *
        * \exception TODO
        */
        iores enqueue_data_record(const void* const data_buff, const std::size_t tot_data_len,
                const std::size_t this_data_len, data_tok* dtokp, const bool transient = false);

        iores enqueue_extern_data_record(const std::size_t tot_data_len, data_tok* dtokp,
                const bool transient = false);

        /**
        * \brief Enqueue data.
        *
        * \param data_buff Pointer to data to be enqueued for this enqueue operation.
        * \param tot_data_len Total data length.
        * \param this_data_len Amount to be written in this enqueue operation.
        * \param dtokp Pointer to data token which contains the details of the enqueue operation.
        * \param xid String containing xid. An empty string (i.e. length=0) will be considered
        *     non-transactional.
        * \param transient Flag indicating transient persistence (ie, ignored on recover).
        *
        * \exception TODO
        */
        iores enqueue_txn_data_record(const void* const data_buff, const std::size_t tot_data_len,
                const std::size_t this_data_len, data_tok* dtokp, const std::string& xid,
                const bool transient = false);
        iores enqueue_extern_txn_data_record(const std::size_t tot_data_len, data_tok* dtokp,
                const std::string& xid, const bool transient = false);

        /* TODO
        **
        * \brief Retrieve details of next record to be read without consuming the record.
        *
        * Retrieve information about current read record. A pointer to the data is returned, along
        * with the data size and available data size. Data is considered "available" when the AIO
        * operations to fill page-cache pages from disk have returned, and is ready for consumption.
        *
        * If <i>dsize_avail</i> &lt; <i>dsize</i>, then not all of the data is available or part of
        * the data is in non-contiguous memory, and a subsequent call will update both the pointer
        * and <i>dsize_avail</i> if more pages have returned from AIO.
        *
        * The <i>dsize_avail</i> parameter will return the amount of data from this record that is
        * available in the page cache as contiguous memory, even if it spans page cache boundaries.
        * However, if a record spans the end of the page cache and continues at the beginning, even
        * if both parts are ready for consumption, then this must be divided into at least two
        * get_data_record() operations, as the data is contained in at least two non-contiguous
        * segments of the page cache.
        *
        * Once all the available data for a record is exposed, it can not be read again using
        * this function. It must be consumed prior to getting the next record. This can be done by
        * calling discard_data_record() or read_data_record(). However, if parameter
        * <i>auto_discard</i> is set to <b><i>true</i></b>, then this record will be automatically
        * consumed when the entire record has become available without having to explicitly call
        * discard_next_data_record() or read_data_record().
        *
        * If the current record is an open transactional record, then it cannot be read until it is
        * committed. If it is aborted, it can never be read. Under this condition, get_data_record()
        * will return RHM_IORES_TXPENDING, the data pointer will be set to NULL and all data
        * lengths will be set to 0.
        *
        * Example: Read a record of 30k. Assume a read page cache of 10 pages of size 10k starting
        * at address base_ptr (page0 = base_ptr, page1 = page_ptr+10k, etc.). The first 15k of
        * the record falls at the end of the page cache, the remaining 15k folded to the beginning.
        * The current page (page 8) containing 5k is available, the remaining pages which contain
        * this record are pending AIO return:
        * <pre>
        * call       dsize
        * no.  dsize avail data ptr     Return   Comment
        * ----+-----+-----+------------+--------+--------------------------------------------------
        * 1    30k   5k    base_ptr+85k SUCCESS  Initial call, read first 5k
        * 2    30k   0k    base_ptr+90k AIO_WAIT AIO still pending; no further pages avail
        * 3    30k   10k   base_ptr+90k SUCCESS  AIO now returned; now read till end of page cache
        * 4    30k   15k   base_ptr     SUCCESS  data_ptr now pointing to start of page cache
        * </pre>
        *
        * \param rid Reference that returns the record ID (rid)
        * \param dsize Reference that returns the total data size of the record data .
        * \param dsize_avail Reference that returns the amount of the data that is available for
        *     consumption.
        * \param data Pointer to data pointer which will point to the first byte of the next record
        *     data.
        * \param auto_discard If <b><i>true</i></b>, automatically discard the record being read if
        *     the entire record is available (i.e. dsize == dsize_avail). Otherwise
        *     discard_next_data_record() must be explicitly called.
        *
        * \exception TODO
        *
        // *** NOT YET IMPLEMENTED ***
        iores get_data_record(const u_int64_t& rid, const std::size_t& dsize,
                const std::size_t& dsize_avail, const void** const data, bool auto_discard = false);
        */

        /* TODO
        **
        * \brief Discard (skip) next record to be read without reading or retrieving it.
        *
        * \exception TODO
        *
        // *** NOT YET IMPLEMENTED ***
        iores discard_data_record(data_tok* const dtokp);
        */

        /**
        * \brief Reads data from the journal. It is the responsibility of the reader to free
        *     the memory that is allocated through this call - see below for details.
        *
        * Reads the next non-dequeued data record from the journal.
        *
        * <b>Note</b> that this call allocates memory into which the data and XID are copied. It
        * is the responsibility of the caller to free this memory. The memory for the data and
        * XID are allocated in a single call, and the XID precedes the data in the memory space.
        * Thus, where an XID exists, freeing the XID pointer will free both the XID and data memory.
        * However, if an XID does not exist for the message, the XID pointer xidpp is set to NULL,
        * and it is the data pointer datapp that must be freed. Should neither an XID nor data be
        * present (ie an empty record), then no memory is allocated, and both pointers will be NULL.
        * In this case, there is no need to free memory.
        *
        * TODO: Fix this lousy interface. The caller should NOT be required to clean up these
        * pointers! Rather use a struct, or better still, let the data token carry the data and
        * xid pointers and lengths, and have the data token both allocate and delete.
        *
        * \param datapp Pointer to pointer that will be set to point to memory allocated and
        *     containing the data. Will be set to NULL if the call fails or there is no data
        *     in the record.
        * \param dsize Ref that will be set to the size of the data. Will be set to 0 if the call
        *     fails or if there is no data in the record.
        * \param xidpp Pointer to pointer that will be set to point to memory allocated and
        *     containing the XID. Will be set to NULL if the call fails or there is no XID attached
        *     to this record.
        * \param xidsize Ref that will be set to the size of the XID.
        * \param transient Ref that will be set true if record is transient.
        * \param external Ref that will be set true if record is external. In this case, the data
        *     pointer datapp will be set to NULL, but dsize will contain the size of the data.
        *     NOTE: If there is an xid, then xidpp must be freed.
        * \param dtokp Pointer to data_tok instance for this data, used to track state of data
        *     through journal.
        * \param ignore_pending_txns When false (default), if the next record to be read is locked
        *     by a pending transaction, the read fails with RHM_IORES_TXPENDING. However, if set
        *     to true, then locks are ignored. This is required for reading of the Transaction
        *     Prepared List (TPL) which may have its entries locked, but may be read from
        *     time-to-time, and needs all its records (locked and unlocked) to be available.
        *
        * \exception TODO
        */
        iores read_data_record(void** const datapp, std::size_t& dsize, void** const xidpp,
                std::size_t& xidsize, bool& transient, bool& external, data_tok* const dtokp,
                bool ignore_pending_txns = false);

        /**
        * \brief Dequeues (marks as no longer needed) data record in journal.
        *
        * Dequeues (marks as no longer needed) data record in journal. Note that it is possible
        * to use the same data token instance used to enqueue this data; it contains the record ID
        * needed to correctly mark this data as dequeued in the journal. Otherwise the RID of the
        * record to be dequeued and the write state of ENQ must be manually set in a new or reset
        * instance of data_tok.
        *
        * \param dtokp Pointer to data_tok instance for this data, used to track state of data
        *     through journal.
        * \param txn_coml_commit Only used for preparedXID journal. When used for dequeueing
        *     prepared XID list items, sets whether the complete() was called in commit or abort
        *     mode.
        *
        * \exception TODO
        */
        iores dequeue_data_record(data_tok* const dtokp, const bool txn_coml_commit = false);

        /**
        * \brief Dequeues (marks as no longer needed) data record in journal.
        *
        * Dequeues (marks as no longer needed) data record in journal as part of a transaction.
        * Note that it is possible to use the same data token instance used to enqueue this data;
        * it contains the RID needed to correctly mark this data as dequeued in the journal.
        * Otherwise the RID of the record to be dequeued and the write state of ENQ must be
        * manually set in a new or reset instance of data_tok.
        *
        * \param dtokp Pointer to data_tok instance for this data, used to track state of data
        *     through journal.
        * \param xid String containing xid. An empty string (i.e. length=0) will be considered
        *     non-transactional.
        * \param txn_coml_commit Only used for preparedXID journal. When used for dequeueing
        *     prepared XID list items, sets whether the complete() was called in commit or abort
        *     mode.
        *
        * \exception TODO
        */
        iores dequeue_txn_data_record(data_tok* const dtokp, const std::string& xid, const bool txn_coml_commit = false);

        /**
        * \brief Abort the transaction for all records enqueued or dequeued with the matching xid.
        *
        * Abort the transaction for all records enqueued with the matching xid. All enqueued records
        * are effectively deleted from the journal, and can not be read. All dequeued records remain
        * as though they had never been dequeued.
        *
        * \param dtokp Pointer to data_tok instance for this data, used to track state of data
        *     through journal.
        * \param xid String containing xid.
        *
        * \exception TODO
        */
        iores txn_abort(data_tok* const dtokp, const std::string& xid);

        /**
        * \brief Commit the transaction for all records enqueued or dequeued with the matching xid.
        *
        * Commit the transaction for all records enqueued with the matching xid. All enqueued
        * records are effectively released for reading and dequeueing. All dequeued records are
        * removed and can no longer be accessed.
        *
        * \param dtokp Pointer to data_tok instance for this data, used to track state of data
        *     through journal.
        * \param xid String containing xid.
        *
        * \exception TODO
        */
        iores txn_commit(data_tok* const dtokp, const std::string& xid);

        /**
        * \brief Check whether all the enqueue records for the given xid have reached disk.
        *
        * \param xid String containing xid.
        *
        * \exception TODO
        */
        bool is_txn_synced(const std::string& xid);

        /**
        * \brief Forces a check for returned AIO write events.
        *
        * Forces a check for returned AIO write events. This is normally performed by enqueue() and
        * dequeue() operations, but if these operations cease, then this call needs to be made to
        * force the processing of any outstanding AIO operations.
        */
        int32_t get_wr_events(timespec* const timeout);

        /**
        * \brief Forces a check for returned AIO read events.
        *
        * Forces a check for returned AIO read events. This is normally performed by read_data()
        * operations, but if these operations cease, then this call needs to be made to force the
        * processing of any outstanding AIO operations.
        */
        int32_t get_rd_events(timespec* const timeout);

        /**
        * \brief Stop the journal from accepting any further requests to read or write data.
        *
        * This operation is used to stop the journal. This is the normal mechanism for bringing the
        * journal to an orderly stop. Any outstanding AIO operations or partially written pages in
        * the write page cache will by flushed and will complete.
        *
        * <b>Note:</b> The journal cannot be restarted without either initializing it or restoring
        *     it.
        *
        * \param block_till_aio_cmpl If true, will block the thread while waiting for all
        *     outstanding AIO operations to complete.
        */
        void stop(const bool block_till_aio_cmpl = false);

        /**
        * \brief Force a flush of the write page cache, creating a single AIO write operation.
        */
        iores flush(const bool block_till_aio_cmpl = false);

        inline u_int32_t get_enq_cnt() const { return _emap.size(); }

        inline u_int32_t get_wr_aio_evt_rem() const { slock l(_wr_mutex); return _wmgr.get_aio_evt_rem(); }

        inline u_int32_t get_rd_aio_evt_rem() const { return _rmgr.get_aio_evt_rem(); }

        inline u_int32_t get_wr_outstanding_aio_dblks() const
                { return _wrfc.aio_outstanding_dblks(); }

        inline u_int32_t get_wr_outstanding_aio_dblks(u_int16_t lfid) const
                { return _lpmgr.get_fcntlp(lfid)->wr_aio_outstanding_dblks(); }

        inline u_int32_t get_rd_outstanding_aio_dblks() const
                { return _rrfc.aio_outstanding_dblks(); }

        inline u_int32_t get_rd_outstanding_aio_dblks(u_int16_t lfid) const
                { return _lpmgr.get_fcntlp(lfid)->rd_aio_outstanding_dblks(); }

        inline u_int16_t get_rd_fid() const { return _rrfc.index(); }
        inline u_int16_t get_wr_fid() const { return _wrfc.index(); }
        u_int16_t get_earliest_fid();

        /**
        * \brief Check if a particular rid is enqueued. Note that this function will return
        *     false if the rid is transactionally enqueued and is not committed, or if it is
        *     locked (i.e. transactionally dequeued, but the dequeue has not been committed).
        */
        inline bool is_enqueued(const u_int64_t rid, bool ignore_lock = false)
                { return _emap.is_enqueued(rid, ignore_lock); }
        inline bool is_locked(const u_int64_t rid)
                { if (_emap.is_enqueued(rid, true) < enq_map::EMAP_OK) return false; return _emap.is_locked(rid) == enq_map::EMAP_TRUE; }
        inline void enq_rid_list(std::vector<u_int64_t>& rids) { _emap.rid_list(rids); }
        inline void enq_xid_list(std::vector<std::string>& xids) { _tmap.xid_list(xids); }
        inline u_int32_t get_open_txn_cnt() const { return _tmap.size(); }
        // TODO Make this a const, but txn_map must support const first.
        inline txn_map& get_txn_map() { return _tmap; }

        /**
        * \brief Check if the journal is stopped.
        *
        * \return <b><i>true</i></b> if the jouranl is stopped;
        *     <b><i>false</i></b> otherwise.
        */
        inline bool is_stopped() { return _stop_flag; }

        /**
        * \brief Check if the journal is ready to read and write data.
        *
        * Checks if the journal is ready to read and write data. This function will return
        * <b><i>true</i></b> if the journal has been either initialized or restored, and the stop()
        * function has not been called since the initialization.
        *
        * Note that the journal may also be stopped if an internal error occurs (such as running out
        * of data journal file space).
        *
        * \return <b><i>true</i></b> if the journal is ready to read and write data;
        *     <b><i>false</i></b> otherwise.
        */
        inline bool is_ready() const { return _init_flag && !_stop_flag; }

        inline bool is_read_only() const { return _readonly_flag; }

        /**
        * \brief Get the journal directory.
        *
        * This returns the journal directory as set during initialization. This is the directory
        * into which the journal files will be written.
        */
        inline const std::string& dirname() const { return _jdir.dirname(); }

        /**
        * \brief Get the journal base filename.
        *
        * Get the journal base filename as set during initialization. This is the prefix used in all
        * journal files of this instance. Note that if more than one instance of the journal shares
        * the same directory, their base filenames <b>MUST</b> be different or else the instances
        * will overwrite one another.
        */
        inline const std::string& base_filename() const { return _base_filename; }

        inline u_int16_t num_jfiles() const { return _lpmgr.num_jfiles(); }

        inline fcntl* get_fcntlp(const u_int16_t lfid) const { return _lpmgr.get_fcntlp(lfid); }

        inline u_int32_t jfsize_sblks() const { return _jfsize_sblks; }

        // Logging
        virtual void log(log_level level, const std::string& log_stmt) const;
        virtual void log(log_level level, const char* const log_stmt) const;

        // FIXME these are _rmgr to _wmgr interactions, remove when _rmgr contains ref to _wmgr:
        void chk_wr_frot();
        inline u_int32_t unflushed_dblks() { return _wmgr.unflushed_dblks(); }
        void fhdr_wr_sync(const u_int16_t lid);
        inline u_int32_t wr_subm_cnt_dblks(const u_int16_t lfid) const { return _lpmgr.get_fcntlp(lfid)->wr_subm_cnt_dblks(); }

        // Management instrumentation callbacks
        inline virtual void instr_incr_outstanding_aio_cnt() {}
        inline virtual void instr_decr_outstanding_aio_cnt() {}

        /**
        * /brief Static function for creating new fcntl objects for use with obj_arr.
        */
        static fcntl* new_fcntl(jcntl* const jcp, const u_int16_t lid, const u_int16_t fid, const rcvdat* const rdp);

    protected:
        static bool _init;
        static bool init_statics();

        /**
        * \brief Check status of journal before allowing write operations.
        */
        void check_wstatus(const char* fn_name) const;

        /**
        * \brief Check status of journal before allowing read operations.
        */
        void check_rstatus(const char* fn_name) const;

        /**
        * \brief Write info file &lt;basefilename&gt;.jinf to disk
        */
        void write_infofile() const;

        /**
        * \brief Call that blocks while waiting for all outstanding AIOs to complete
        */
        void aio_cmpl_wait();

        /**
        * \brief Call that blocks until at least one message returns; used to wait for
        *     AIO wait conditions to clear.
        */
        bool handle_aio_wait(const iores res, iores& resout, const data_tok* dtp);

        /**
        * \brief Analyze journal for recovery.
        */
        void rcvr_janalyze(rcvdat& rd, const std::vector<std::string>* prep_txn_list_ptr);

        bool rcvr_get_next_record(u_int16_t& fid, std::ifstream* ifsp, bool& lowi, rcvdat& rd);

        bool decode(jrec& rec, u_int16_t& fid, std::ifstream* ifsp, std::size_t& cum_size_read,
                rec_hdr& h, bool& lowi, rcvdat& rd, std::streampos& rec_offset);

        bool jfile_cycle(u_int16_t& fid, std::ifstream* ifsp, bool& lowi, rcvdat& rd,
                const bool jump_fro);

        bool check_owi(const u_int16_t fid, rec_hdr& h, bool& lowi, rcvdat& rd,
                std::streampos& read_pos);

        void check_journal_alignment(const u_int16_t fid, std::streampos& rec_offset, rcvdat& rd);
    };

} // namespace journal
} // namespace mrg

#endif // ifndef QPID_LEGACYSTORE_JRNL_JCNTL_H
