#ifndef _sys_windows_AsynchIO
#define _sys_windows_AsynchIO

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

#include "AsynchIoResult.h"
#include "qpid/sys/AsynchIO.h"
#include "qpid/sys/IntegerTypes.h"
#include "qpid/sys/Poller.h"
#include "qpid/CommonImportExport.h"
#include "qpid/sys/Mutex.h"
#include <boost/function.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/shared_array.hpp>
#include <winsock2.h>
#include <mswsock.h>
#include <windows.h>

// security.h needs to see this to distinguish from kernel use.
#define SECURITY_WIN32
#include <security.h>
#include <Schnlsp.h>
#undef SECURITY_WIN32

namespace qpid {
namespace sys {
namespace windows {

/*
 * Asynch Acceptor
 */

class AsynchAcceptor : public qpid::sys::AsynchAcceptor {

    friend class AsynchAcceptResult;

public:
    AsynchAcceptor(const Socket& s, AsynchAcceptor::Callback callback);
    ~AsynchAcceptor();
    void start(Poller::shared_ptr poller);

private:
    void restart(void);

    AsynchAcceptor::Callback acceptedCallback;
    const Socket& socket;
    const SOCKET wSocket;
    const LPFN_ACCEPTEX fnAcceptEx;
};


class AsynchConnector : public qpid::sys::AsynchConnector {
private:
    ConnectedCallback connCallback;
    FailedCallback failCallback;
    const Socket& socket;
    const std::string hostname;
    const std::string port;

public:
    AsynchConnector(const Socket& socket,
                    const std::string& hostname,
                    const std::string& port,
                    ConnectedCallback connCb,
                    FailedCallback failCb = 0);
    void start(Poller::shared_ptr poller);
    void requestCallback(RequestCallback rCb);
};

class AsynchIO : public qpid::sys::AsynchIO {

    friend class SslAsynchIO;

public:
    AsynchIO(const Socket& s,
             ReadCallback rCb,
             EofCallback eofCb,
             DisconnectCallback disCb,
             ClosedCallback cCb = 0,
             BuffersEmptyCallback eCb = 0,
             IdleCallback iCb = 0);
    ~AsynchIO();

    // Methods inherited from qpid::sys::AsynchIO

    /**
     * Notify the object is should delete itself as soon as possible.
     */
    virtual void queueForDeletion();

    /// Take any actions needed to prepare for working with the poller.
    virtual void start(Poller::shared_ptr poller);
    virtual void createBuffers(uint32_t size);
    virtual void queueReadBuffer(BufferBase* buff);
    virtual void unread(BufferBase* buff);
    virtual void queueWrite(BufferBase* buff);
    virtual void notifyPendingWrite();
    virtual void queueWriteClose();
    virtual bool writeQueueEmpty();
    virtual void requestCallback(RequestCallback);

    /**
     * getQueuedBuffer returns a buffer from the buffer queue, if one is
     * available.
     *
     * @retval Pointer to BufferBase buffer; 0 if none is available.
     */
    virtual BufferBase* getQueuedBuffer();

    virtual SecuritySettings getSecuritySettings(void);

private:
    ReadCallback readCallback;
    EofCallback eofCallback;
    DisconnectCallback disCallback;
    ClosedCallback closedCallback;
    BuffersEmptyCallback emptyCallback;
    IdleCallback idleCallback;
    const Socket& socket;
    Poller::shared_ptr poller;
    uint32_t bufferCount;

    std::deque<BufferBase*> bufferQueue;
    std::deque<BufferBase*> writeQueue;
    /* The MSVC-supplied deque is not thread-safe; keep locks to serialize
     * access to the buffer queue and write queue.
     */
    Mutex bufferQueueLock;
    std::vector<BufferBase> buffers;
    boost::shared_array<char> bufferMemory;

    // Number of outstanding I/O operations.
    volatile LONG opsInProgress;
    // Is there a write in progress?
    volatile bool writeInProgress;
    // Or a read?
    volatile bool readInProgress;
    // Deletion requested, but there are callbacks in progress.
    volatile bool queuedDelete;
    // Socket close requested, but there are operations in progress.
    volatile bool queuedClose;

protected:
    uint32_t getBufferCount(void);
    void setBufferCount(uint32_t);

private:
    // Dispatch events that have completed.
    void notifyEof(void);
    void notifyDisconnect(void);
    void notifyClosed(void);
    void notifyBuffersEmpty(void);
    void notifyIdle(void);

    /**
     * Initiate a write of the specified buffer. There's no callback for
     * write completion to the AsynchIO object.
     */
    void startWrite(AsynchIO::BufferBase* buff);

    void close(void);

    /**
     * startReading initiates reading, readComplete() is
     * called when the read completes.
     */
    void startReading();

    /**
     * readComplete is called when a read request is complete.
     *
     * @param result Results of the operation.
     */
    void readComplete(AsynchReadResult *result);

    /**
     * writeComplete is called when a write request is complete.
     *
     * @param result Results of the operation.
     */
    void writeComplete(AsynchWriteResult *result);

    /**
     * Queue of completions to run. This queue enforces the requirement
     * from upper layers that only one thread at a time is allowed to act
     * on any given connection. Once a thread is busy processing a completion
     * on this object, other threads that dispatch completions queue the
     * completions here for the in-progress thread to handle when done.
     * Thus, any threads can dispatch a completion from the IocpPoller, but
     * this class ensures that actual processing at the connection level is
     * only on one thread at a time.
     */
    std::queue<AsynchIoResult *> completionQueue;
    volatile bool working;
    Mutex completionLock;

    /**
     * Called when there's a completion to process.
     */
    void completion(AsynchIoResult *result);

    /**
     * Helper function to facilitate the close operation
     */
    void cancelRead();

    /**
     * Log information about buffer depletion, which should never happen.
     * See QPID-5033.
     */
    void logNoBuffers(const char*);
};

}}}   // namespace qpid::sys::windows

#endif // _sys_windows_AsynchIO
