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

#ifndef QPID_LINEARSTORE_JOURNAL_AIO_H
#define QPID_LINEARSTORE_JOURNAL_AIO_H

#include <libaio.h>
#include <cstring>
#include <stdint.h>

namespace qpid {
namespace linearstore {
namespace journal {

typedef iocb aio_cb;
typedef io_event aio_event;

/**
 * \brief This class is a C++ wrapper class for the libaio functions used by the journal. Note that only those
 * functions used by the journal are included here. This is not a complete implementation of all libaio functions.
 */
class aio
{
public:
    /*
     * \brief Initialize an AIO context. Causes kernel resources to be initialized for
     * AIO operations.
     *
     * \param maxevents The maximum number of events to be handled
     * \param ctxp Pointer to context struct to be initialized
     */
    static inline int queue_init(int maxevents, io_context_t* ctxp)
    {
        return ::io_queue_init(maxevents, ctxp);
    }

    /*
     * \brief Release an AIO context. Causes kernel resources previously initialized to
     * be released.
     *
     * \param ctx AIO context struct to be released
     */
    static inline int queue_release(io_context_t ctx)
    {
        return ::io_queue_release(ctx);
    }

    /*
     * \brief Submit asynchronous I/O blocks for processing
     *
     * The io_submit() system call queues nr I/O request blocks for processing in the AIO context ctx.
     * The iocbpp argument should be an array of nr AIO control blocks, which will be submitted to context ctx.
     *
     * \param ctx AIO context
     * \param nr Number of AIO operations
     * \param aios Array of nr pointers to AIO control blocks, one for each AIO operation
     * \return On success, io_submit() returns the number of iocbs submitted (which may be 0 if nr is zero).
     *         A negative number indicates an error:
     *         - -EAGAIN Insufficient resources are available to queue any iocbs.
     *         - -EBADF  The file descriptor specified in the first iocb is invalid.
     *         - -EFAULT One of the data structures points to invalid data.
     *         - -EINVAL The AIO context specified by ctx_id is invalid.  nr is less than 0. The iocb at *iocbpp[0]
     *                   is not properly initialized, or the operation specified is invalid for the file descriptor
     *                   in the iocb.
     */
    static inline int submit(io_context_t ctx, long nr, aio_cb* aios[])
    {
        return ::io_submit(ctx, nr, aios);
    }

    /*
     * \brief Get list of completed AIO operations
     *
     * The io_getevents() system call attempts to read at least min_nr events and up to nr events from the
     * completion queue of the AIO context specified by ctx_id.  The timeout argument specifies the amount of time
     * to wait for events, where a NULL timeout waits until at least min_nr events have been seen.  Note that timeout
     * is relative.
     *
     * \param ctx AIO context
     * \param min_nr Minimum number of events to return, will wait until min_nr events are accumulated or until timeout
     * \param nr Number of events to return
     * \param events Pointer to array of aio_event structs, one for each completed event
     * \param timeout Time to wait for min_nr events; 0 will cause an indefinite wait for min_nr events
     * \return On success, number of events read: 0 if no events are available, or less than min_nr
     *         if the timeout has elapsed. A negative number indicates an error:
     *         - -EFAULT Either events or timeout is an invalid pointer.
     *         - -EINVAL ctx_id is invalid.  min_nr is out of range or nr is out of range.
     *         - -EINTR  Interrupted by a signal handler; see signal(7).
     */
    static inline int getevents(io_context_t ctx, long min_nr, long nr, aio_event* events, timespec* const timeout)
    {
        return ::io_getevents(ctx, min_nr, nr, events, timeout);
    }

    /**
     * \brief This function allows iocbs to be initialized with a pointer that can be re-used. This prepares an
     * aio_cb struct for read use. (This is a wrapper for libaio's ::io_prep_pread() function.)
     *
     * \param aiocbp Pointer to the aio_cb struct to be prepared.
     * \param fd File descriptor to be used for read.
     * \param buf Pointer to buffer in which read data is to be placed. MUST BE PAGE_ALIGNED.
     * \param count Number of bytes to read - buffer must be large enough.
     * \param offset Offset within file from which data will be read.
     */
    static inline void prep_pread(aio_cb* aiocbp, int fd, void* buf, std::size_t count, int64_t offset)
    {
        ::io_prep_pread(aiocbp, fd, buf, count, offset);
    }

    /**
     * \brief Special version of libaio's io_prep_pread() which preserves the value of the data pointer. This allows
     * iocbs to be initialized with a pointer that can be re-used. This prepares a aio_cb struct for read use.
     *
     * \param aiocbp Pointer to the aio_cb struct to be prepared.
     * \param fd File descriptor to be used for read.
     * \param buf Pointer to buffer in which read data is to be placed. MUST BE PAGE_ALIGNED.
     * \param count Number of bytes to read - buffer must be large enough.
     * \param offset Offset within file from which data will be read.
     */
    static inline void prep_pread_2(aio_cb* aiocbp, int fd, void* buf, std::size_t count, int64_t offset)
    {
        std::memset((void*) ((char*) aiocbp + sizeof(void*)), 0, sizeof(aio_cb) - sizeof(void*));
        aiocbp->aio_fildes = fd;
        aiocbp->aio_lio_opcode = IO_CMD_PREAD;
        aiocbp->aio_reqprio = 0;
        aiocbp->u.c.buf = buf;
        aiocbp->u.c.nbytes = count;
        aiocbp->u.c.offset = offset;
    }

    /**
     * \brief This function allows iocbs to be initialized with a pointer that can be re-used. This function prepares
     * an aio_cb struct for write use. (This is a wrapper for libaio's ::io_prep_pwrite() function.)
     *
     * \param aiocbp Pointer to the aio_cb struct to be prepared.
     * \param fd File descriptor to be used for write.
     * \param buf Pointer to buffer in which data to be written is located. MUST BE PAGE_ALIGNED.
     * \param count Number of bytes to write.
     * \param offset Offset within file to which data will be written.
     */
    static inline void prep_pwrite(aio_cb* aiocbp, int fd, void* buf, std::size_t count, int64_t offset)
    {
        ::io_prep_pwrite(aiocbp, fd, buf, count, offset);
    }

    /**
     * \brief Special version of libaio's io_prep_pwrite() which preserves the value of the data pointer. This allows
     * iocbs to be initialized with a pointer that can be re-used. This function prepares an aio_cb struct for write
     * use.
     *
     * \param aiocbp Pointer to the aio_cb struct to be prepared.
     * \param fd File descriptor to be used for write.
     * \param buf Pointer to buffer in which data to be written is located. MUST BE PAGE_ALIGNED.
     * \param count Number of bytes to write.
     * \param offset Offset within file to which data will be written.
     */
    static inline void prep_pwrite_2(aio_cb* aiocbp, int fd, void* buf, std::size_t count, int64_t offset)
    {
        std::memset((void*) ((char*) aiocbp + sizeof(void*)), 0, sizeof(aio_cb) - sizeof(void*));
        aiocbp->aio_fildes = fd;
        aiocbp->aio_lio_opcode = IO_CMD_PWRITE;
        aiocbp->aio_reqprio = 0;
        aiocbp->u.c.buf = buf;
        aiocbp->u.c.nbytes = count;
        aiocbp->u.c.offset = offset;
    }

    /**
     * \brief Function to check the alignment of memory.
     *
     * \param ptr Pointer to be checked
     * \param byte_count Alignment count (or boundary)
     * \returns true if ptr is aligned with byte_count, false otherwise
     */
    static inline bool is_aligned(const void* ptr, uint64_t byte_count)
    {
        return ((uintptr_t)(ptr)) % (byte_count) == 0;
    }
};

}}}

#endif // ifndef QPID_LINEARSTORE_JOURNAL_AIO_H
