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

#include "qpid/sys/windows/AsynchIoResult.h"
#include "qpid/sys/windows/IoHandlePrivate.h"
#include "qpid/sys/AsynchIO.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Socket.h"
#include "qpid/sys/windows/WinSocket.h"
#include "qpid/sys/SecuritySettings.h"
#include "qpid/sys/SocketAddress.h"
#include "qpid/sys/Poller.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Time.h"
#include "qpid/log/Statement.h"

#include "qpid/sys/windows/check.h"
#include "qpid/sys/windows/mingw32_compat.h"

#include <boost/thread/once.hpp>

#include <queue>
#include <winsock2.h>
#include <mswsock.h>
#include <windows.h>

#include <boost/bind.hpp>
#include <boost/shared_array.hpp>
#include "qpid/sys/windows/AsynchIO.h"

namespace {

    typedef qpid::sys::ScopedLock<qpid::sys::Mutex>  QLock;

/*
 * The function pointers for AcceptEx and ConnectEx need to be looked up
 * at run time.
 */
const LPFN_ACCEPTEX lookUpAcceptEx(const qpid::sys::IOHandle& io) {
    SOCKET h = io.fd;
    GUID guidAcceptEx = WSAID_ACCEPTEX;
    DWORD dwBytes = 0;
    LPFN_ACCEPTEX fnAcceptEx;
    WSAIoctl(h,
             SIO_GET_EXTENSION_FUNCTION_POINTER,
             &guidAcceptEx,
             sizeof(guidAcceptEx),
             &fnAcceptEx,
             sizeof(fnAcceptEx),
             &dwBytes,
             NULL,
             NULL);
    if (fnAcceptEx == 0)
        throw qpid::Exception(QPID_MSG("Failed to look up AcceptEx"));
    return fnAcceptEx;
}

}

namespace qpid {
namespace sys {
namespace windows {

/*
 * Asynch Acceptor
 *
 */
AsynchAcceptor::AsynchAcceptor(const Socket& s, Callback callback)
  : acceptedCallback(callback),
    socket(s),
    wSocket(IOHandle(s).fd),
    fnAcceptEx(lookUpAcceptEx(s)) {

    s.setNonblocking();
}

AsynchAcceptor::~AsynchAcceptor()
{
    socket.close();
}

void AsynchAcceptor::start(Poller::shared_ptr poller) {
    PollerHandle ph = PollerHandle(socket);
    poller->monitorHandle(ph, Poller::INPUT);
    restart ();
}

void AsynchAcceptor::restart(void) {
    DWORD bytesReceived = 0;  // Not used, needed for AcceptEx API
    AsynchAcceptResult *result = new AsynchAcceptResult(acceptedCallback,
                                                        this,
                                                        socket);
    BOOL status;
    status = fnAcceptEx(wSocket,
                        IOHandle(*result->newSocket).fd,
                        result->addressBuffer,
                        0,
                        AsynchAcceptResult::SOCKADDRMAXLEN,
                        AsynchAcceptResult::SOCKADDRMAXLEN,
                        &bytesReceived,
                        result->overlapped());
    QPID_WINDOWS_CHECK_ASYNC_START(status);
}


Socket* createSameTypeSocket(const Socket& sock) {
    SOCKET socket = IOHandle(sock).fd;
    // Socket currently has no actual socket attached
    if (socket == INVALID_SOCKET)
        return new WinSocket;

    ::sockaddr_storage sa;
    ::socklen_t salen = sizeof(sa);
    QPID_WINSOCK_CHECK(::getsockname(socket, (::sockaddr*)&sa, &salen));
    SOCKET s = ::socket(sa.ss_family, SOCK_STREAM, 0); // Currently only work with SOCK_STREAM
    if (s == INVALID_SOCKET) throw QPID_WINDOWS_ERROR(WSAGetLastError());
    return new WinSocket(s);
}

AsynchAcceptResult::AsynchAcceptResult(AsynchAcceptor::Callback cb,
                                       AsynchAcceptor *acceptor,
                                       const Socket& lsocket)
  : callback(cb), acceptor(acceptor),
    listener(IOHandle(lsocket).fd),
    newSocket(createSameTypeSocket(lsocket)) {
}

void AsynchAcceptResult::success(size_t /*bytesTransferred*/) {
    ::setsockopt (IOHandle(*newSocket).fd,
                  SOL_SOCKET,
                  SO_UPDATE_ACCEPT_CONTEXT,
                  (char*)&listener,
                  sizeof (listener));
    callback(*(newSocket.release()));
    acceptor->restart ();
    delete this;
}

void AsynchAcceptResult::failure(int /*status*/) {
    //if (status != WSA_OPERATION_ABORTED)
    // Can there be anything else?  ;
    delete this;
}

/*
 * AsynchConnector does synchronous connects for now... to do asynch the
 * IocpPoller will need some extension to register an event handle as a
 * CONNECT-type "direction", the connect completion/result will need an
 * event handle to associate with the connecting handle. But there's no
 * time for that right now...
 */
AsynchConnector::AsynchConnector(const Socket& sock,
                                 const std::string& hname,
                                 const std::string& p,
                                 ConnectedCallback connCb,
                                 FailedCallback failCb) :
    connCallback(connCb), failCallback(failCb), socket(sock),
    hostname(hname), port(p)
{
}

void AsynchConnector::start(Poller::shared_ptr)
{
    try {
        socket.connect(SocketAddress(hostname, port));
        socket.setNonblocking();
        connCallback(socket);
    } catch(std::exception& e) {
        if (failCallback)
            failCallback(socket, -1, std::string(e.what()));
        socket.close();
    }
}

// This can never be called in the current windows code as connect
// is blocking and requestCallback only makes sense if connect is
// non-blocking with the results returned via a poller callback.
void AsynchConnector::requestCallback(RequestCallback rCb)
{
}

} // namespace windows

AsynchAcceptor* AsynchAcceptor::create(const Socket& s, 
                                       Callback callback)
{
    return new windows::AsynchAcceptor(s, callback);
}

AsynchConnector* qpid::sys::AsynchConnector::create(const Socket& s,
                                                    const std::string& hostname,
                                                    const std::string& port,
                                                    ConnectedCallback connCb,
                                                    FailedCallback failCb)
{
    return new windows::AsynchConnector(s,
                                        hostname,
                                        port,
                                        connCb,
                                        failCb);
}


/*
 * Asynch reader/writer
 */

namespace windows {

// This is used to encapsulate pure callbacks into a handle
class CallbackHandle : public IOHandle {
public:
    CallbackHandle(AsynchIoResult::Completer completeCb,
                   AsynchIO::RequestCallback reqCb = 0) :
        IOHandle(INVALID_SOCKET, completeCb, reqCb)
    {}
};

AsynchIO::AsynchIO(const Socket& s,
                   ReadCallback rCb,
                   EofCallback eofCb,
                   DisconnectCallback disCb,
                   ClosedCallback cCb,
                   BuffersEmptyCallback eCb,
                   IdleCallback iCb) :

    readCallback(rCb),
    eofCallback(eofCb),
    disCallback(disCb),
    closedCallback(cCb),
    emptyCallback(eCb),
    idleCallback(iCb),
    socket(s),
    bufferCount(BufferCount),
    opsInProgress(0),
    writeInProgress(false),
    readInProgress(false),
    queuedDelete(false),
    queuedClose(false),
    working(false) {
}

AsynchIO::~AsynchIO() {
}

void AsynchIO::queueForDeletion() {
    {
        ScopedLock<Mutex> l(completionLock);
        assert(!queuedDelete);
        queuedDelete = true;
        if (working || opsInProgress > 0) {
            QPID_LOG(info, "Delete AsynchIO queued; ops in progress");
            // AsynchIOHandler calls this then deletes itself; don't do any more
            // callbacks.
            readCallback = 0;
            eofCallback = 0;
            disCallback = 0;
            closedCallback = 0;
            emptyCallback = 0;
            idleCallback = 0;
            return;
        }
    }
    delete this;
}

void AsynchIO::start(Poller::shared_ptr poller0) {
    PollerHandle ph = PollerHandle(socket);
    poller = poller0;
    poller->monitorHandle(ph, Poller::INPUT);
    if (writeQueue.size() > 0)  // Already have data queued for write
        notifyPendingWrite();
    startReading();
}

uint32_t AsynchIO::getBufferCount(void) { return bufferCount; }

void AsynchIO::setBufferCount(uint32_t count) { bufferCount = count; }


void AsynchIO::createBuffers(uint32_t size) {
    // Allocate all the buffer memory at once
    bufferMemory.reset(new char[size*bufferCount]);

    // Create the Buffer structs in a vector
    // And push into the buffer queue
    buffers.reserve(bufferCount);
    for (uint32_t i = 0; i < bufferCount; i++) {
        buffers.push_back(BufferBase(&bufferMemory[i*size], size));
        queueReadBuffer(&buffers[i]);
    }
}

void AsynchIO::queueReadBuffer(AsynchIO::BufferBase* buff) {
    assert(buff);
    buff->dataStart = 0;
    buff->dataCount = 0;
    QLock l(bufferQueueLock);
    bufferQueue.push_back(buff);
}

void AsynchIO::unread(AsynchIO::BufferBase* buff) {
    assert(buff);
    buff->squish();
    QLock l(bufferQueueLock);
    bufferQueue.push_front(buff);
}

void AsynchIO::queueWrite(AsynchIO::BufferBase* buff) {
    assert(buff);
    QLock l(bufferQueueLock);
    writeQueue.push_back(buff);
    if (!writeInProgress)
        notifyPendingWrite();
}

void AsynchIO::notifyPendingWrite() {
    // This method is generally called from a processing thread; transfer
    // work on this to an I/O thread. Much of the upper layer code assumes
    // that all I/O-related things happen in an I/O thread.
    if (poller == 0)    // Not really going yet...
        return;

    InterlockedIncrement(&opsInProgress);
    PollerHandle ph(CallbackHandle(boost::bind(&AsynchIO::completion, this, _1)));
    poller->monitorHandle(ph, Poller::OUTPUT);
}

void AsynchIO::queueWriteClose() {
    {
        ScopedLock<Mutex> l(completionLock);
        queuedClose = true;
        if (working || writeInProgress)
            // no need to summon an IO thread
            return;
    }
    notifyPendingWrite();
}

bool AsynchIO::writeQueueEmpty() {
    QLock l(bufferQueueLock);
    return writeQueue.size() == 0;
}

/*
 * Initiate a read operation. AsynchIO::readComplete() will be
 * called when the read is complete and data is available.
 */
void AsynchIO::startReading() {
    if (queuedDelete || queuedClose)
        return;

    // (Try to) get a buffer; look on the front since there may be an
    // "unread" one there with data remaining from last time.
    AsynchIO::BufferBase *buff = 0;
    {
        QLock l(bufferQueueLock);

        if (!bufferQueue.empty()) {
            buff = bufferQueue.front();
            assert(buff);
            bufferQueue.pop_front();
        }
        else {
            logNoBuffers("startReading");
        }
    }
    if (buff != 0) {
        int readCount = buff->byteCount - buff->dataCount;
        AsynchReadResult *result =
            new AsynchReadResult(boost::bind(&AsynchIO::completion, this, _1),
                                 buff,
                                 readCount);
        DWORD bytesReceived = 0, flags = 0;
        InterlockedIncrement(&opsInProgress);
        readInProgress = true;
        int status = WSARecv(IOHandle(socket).fd,
                             const_cast<LPWSABUF>(result->getWSABUF()), 1,
                             &bytesReceived,
                             &flags,
                             result->overlapped(),
                             0);
        if (status != 0) {
            int error = WSAGetLastError();
            if (error != WSA_IO_PENDING) {
                result->failure(error);
                result = 0;   // result is invalid here
                return;
            }
        }
        // On status 0 or WSA_IO_PENDING, completion will handle the rest.
    }
    else {
        notifyBuffersEmpty();
    }
    return;
}

// Queue the specified callback for invocation from an I/O thread.
void AsynchIO::requestCallback(RequestCallback callback) {
    // This method is generally called from a processing thread; transfer
    // work on this to an I/O thread. Much of the upper layer code assumes
    // that all I/O-related things happen in an I/O thread.
    if (poller == 0)    // Not really going yet...
        return;

    InterlockedIncrement(&opsInProgress);
    PollerHandle ph(CallbackHandle(
        boost::bind(&AsynchIO::completion, this, _1),
        callback));
    poller->monitorHandle(ph, Poller::INPUT);
}

/**
 * Return a queued buffer if there are enough to spare.
 */
AsynchIO::BufferBase* AsynchIO::getQueuedBuffer() {
    QLock l(bufferQueueLock);
    BufferBase* buff = bufferQueue.empty() ? 0 : bufferQueue.back();
    // An "unread" buffer is reserved for future read operations (which
    // take from the front of the queue).
    if (!buff || (buff->dataCount && bufferQueue.size() == 1)) {
        if (buff)
            logNoBuffers("getQueuedBuffer with unread data");
        else
            logNoBuffers("getQueuedBuffer with empty queue");
        return 0;
    }
    assert(buff->dataCount == 0);
    bufferQueue.pop_back();
    return buff;
}

void AsynchIO::notifyEof(void) {
    if (eofCallback)
        eofCallback(*this);
}

void AsynchIO::notifyDisconnect(void) {
    if (disCallback) {
        DisconnectCallback dcb = disCallback;
        closedCallback = 0;
        disCallback = 0;
        dcb(*this);
        // May have just been deleted.
        return;
    }
}

void AsynchIO::notifyClosed(void) {
    if (closedCallback) {
        ClosedCallback ccb = closedCallback;
        closedCallback = 0;
        disCallback = 0;
        ccb(*this, socket);
        // May have just been deleted.
        return;
    }
}

void AsynchIO::notifyBuffersEmpty(void) {
    if (emptyCallback)
        emptyCallback(*this);
}

void AsynchIO::notifyIdle(void) {
    if (idleCallback)
        idleCallback(*this);
}

/*
 * Asynch reader/writer using overlapped I/O
 */

void AsynchIO::startWrite(AsynchIO::BufferBase* buff) {
    writeInProgress = true;
    InterlockedIncrement(&opsInProgress);
    AsynchWriteResult *result =
        new AsynchWriteResult(boost::bind(&AsynchIO::completion, this, _1),
                              buff,
                              buff->dataCount);
    DWORD bytesSent = 0;
    int status = WSASend(IOHandle(socket).fd,
                         const_cast<LPWSABUF>(result->getWSABUF()), 1,
                         &bytesSent,
                         0,
                         result->overlapped(),
                         0);
    if (status != 0) {
        int error = WSAGetLastError();
        if (error != WSA_IO_PENDING) {
            result->failure(error);   // Also decrements in-progress count
            result = 0;   // result is invalid here
            return;
        }
    }
    // On status 0 or WSA_IO_PENDING, completion will handle the rest.
    return;
}

/*
 * Close the socket and callback to say we've done it
 */
void AsynchIO::close(void) {
    socket.close();
    notifyClosed();
}

SecuritySettings AsynchIO::getSecuritySettings() {
    SecuritySettings settings;
    settings.ssf = socket.getKeyLen();
    settings.authid = socket.getClientAuthId();
    return settings;
}

void AsynchIO::readComplete(AsynchReadResult *result) {
    int status = result->getStatus();
    size_t bytes = result->getTransferred();
    readInProgress = false;
    if (status == 0 && bytes > 0) {
        if (readCallback)
            readCallback(*this, result->getBuff());
        startReading();
    }
    else {
        // No data read, so put the buffer back. It may be partially filled,
        // so "unread" it back to the front of the queue.
        unread(result->getBuff());
        if (queuedClose) {
            return; // Expected from cancelRead()
        }
        notifyEof();
        if (status != 0)
        {
            notifyDisconnect();
        }
    }
}

/*
 * NOTE - this completion is called for completed writes and also when 
 * a write is desired. The difference is in the buff - if a write is desired
 * the buff is 0.
 */
void AsynchIO::writeComplete(AsynchWriteResult *result) {
    int status = result->getStatus();
    size_t bytes = result->getTransferred();
    AsynchIO::BufferBase *buff = result->getBuff();
    if (buff != 0) {
        writeInProgress = false;
        if (status == 0 && bytes > 0) {
            if (bytes < result->getRequested()) // Still more to go; resubmit
                startWrite(buff);
            else
                queueReadBuffer(buff);     // All done; back to the pool
        }
        else {
            // An error... if it's a connection close, ignore it - it will be
            // noticed and handled on a read completion any moment now.
            // What to do with real error??? Save the Buffer?  TBD.
            queueReadBuffer(buff);     // All done; back to the pool
        }
    }

    // If there are no writes outstanding, check for more writes to initiate
    // (either queued or via idle). The opsInProgress count is handled in
    // completion()
    if (!writeInProgress) {
        bool writing = false;
        {
            QLock l(bufferQueueLock);
            if (writeQueue.size() > 0) {
                buff = writeQueue.front();
                assert(buff);
                writeQueue.pop_front();
                startWrite(buff);
                writing = true;
            }
        }
        if (!writing && !queuedClose) {
            notifyIdle();
        }
    }
    return;
}

void AsynchIO::completion(AsynchIoResult *result) {
    bool closing = false;
    bool deleting = false;
    {
        ScopedLock<Mutex> l(completionLock);
        if (working) {
            completionQueue.push(result);
            return;
        }

        // First thread in with something to do; note we're working then keep
        // handling completions.
        working = true;
        while (result != 0) {
            // New scope to unlock temporarily.
            {
                ScopedUnlock<Mutex> ul(completionLock);
                AsynchReadResult *r = dynamic_cast<AsynchReadResult*>(result);
                if (r != 0)
                    readComplete(r);
                else {
                    AsynchWriteResult *w =
                        dynamic_cast<AsynchWriteResult*>(result);
                    if (w != 0)
                        writeComplete(w);
                    else {
                        AsynchCallbackRequest *req =
                          dynamic_cast<AsynchCallbackRequest*>(result);
                        req->reqCallback(*this);
                    }
                }
                delete result;
                result = 0;
                InterlockedDecrement(&opsInProgress);
                if (queuedClose && opsInProgress == 1 && readInProgress)
                    cancelRead();
            }
            // Lock is held again.
            if (completionQueue.empty())
                continue;
            result = completionQueue.front();
            completionQueue.pop();
        }
        working = false;
        if (opsInProgress == 0) {
            closing = queuedClose;
            deleting = queuedDelete;
        }
    }
    // Lock released; ok to close if ops are done and close requested.
    // Layer above will call back to queueForDeletion() if it hasn't
    // already been done. If it already has, go ahead and delete.
    if (deleting)
        delete this;
    else if (closing)
        // close() may cause a delete; don't trust 'this' on return
        close();
}

/*
 * NOTE - this method must be called in the same context as other completions,
 * so that the resulting readComplete, and final AsynchIO::close() is serialized
 * after this method returns.
 */
void AsynchIO::cancelRead() {
    if (queuedDelete)
        return;                 // socket already deleted
    else {
        ScopedLock<Mutex> l(completionLock);;
        if (!completionQueue.empty())
            return;             // process it; come back later if necessary
    }
    // Cancel outstanding read and force to completion.  Otherwise, on a faulty
    // physical link, the pending read can remain uncompleted indefinitely.
    // Draining the pending read will result in the official close (and
    // notifyClosed).  CancelIoEX() is the natural choice, but not available in
    // XP, so we make do with closesocket().
    socket.close();
}

/*
 * Track down cause of unavailable buffer if it recurs: QPID-5033
 */
void AsynchIO::logNoBuffers(const char *context) {
    QPID_LOG(error, "No IO buffers available: " << context <<
             ". Debug data: " << bufferQueue.size() <<
             ' ' << writeQueue.size() <<
             ' ' << completionQueue.size() <<
             ' ' << opsInProgress <<
             ' ' << writeInProgress <<
             ' ' << readInProgress <<
             ' ' << working);
}


} // namespace windows

AsynchIO* qpid::sys::AsynchIO::create(const Socket& s,
                                      AsynchIO::ReadCallback rCb,
                                      AsynchIO::EofCallback eofCb,
                                      AsynchIO::DisconnectCallback disCb,
                                      AsynchIO::ClosedCallback cCb,
                                      AsynchIO::BuffersEmptyCallback eCb,
                                      AsynchIO::IdleCallback iCb)
{
    return new qpid::sys::windows::AsynchIO(s, rCb, eofCb, disCb, cCb, eCb, iCb);
}

}}  // namespace qpid::sys
