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

#include "qpid/sys/AsynchIO.h"

#include "check.h"

#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <signal.h>
#include <errno.h>

#include <boost/bind.hpp>

using namespace qpid::sys;

namespace {

/*
 * Make file descriptor non-blocking
 */
void nonblocking(int fd) {
    QPID_POSIX_CHECK(::fcntl(fd, F_SETFL, O_NONBLOCK));
}

/*
 * Make *process* not generate SIGPIPE when writing to closed
 * pipe/socket (necessary as default action is to terminate process)
 */
void ignoreSigpipe() {
	::signal(SIGPIPE, SIG_IGN);
}

}

/*
 * Asynch Acceptor
 */

AsynchAcceptor::AsynchAcceptor(int fd, Callback callback) :
    acceptedCallback(callback),
    handle(fd, boost::bind(&AsynchAcceptor::readable, this, _1), 0, 0) {

    nonblocking(fd);
    ignoreSigpipe();
}

void AsynchAcceptor::start(Poller::shared_ptr poller) {
    handle.startWatch(poller);
}

/*
 * We keep on accepting as long as there is something to accept
 */
void AsynchAcceptor::readable(DispatchHandle& h) {
    int afd;
    do {
        errno = 0;
        // TODO: Currently we ignore the peers address, perhaps we should
        // log it or use it for connection acceptance.
        afd = ::accept(h.getFD(), 0, 0);
        if (afd >= 0) {
            acceptedCallback(afd);
        } else if (errno == EAGAIN) {
            break;
        } else {
            QPID_POSIX_CHECK(afd);
        }
    } while (true);

    h.rewatch();
}

/*
 * Asynch reader/writer
 */
AsynchIO::AsynchIO(int fd,
    ReadCallback rCb, EofCallback eofCb, DisconnectCallback disCb,
    BuffersEmptyCallback eCb, IdleCallback iCb) :

    DispatchHandle(fd, 
        boost::bind(&AsynchIO::readable, this, _1),
        boost::bind(&AsynchIO::writeable, this, _1),
        boost::bind(&AsynchIO::disconnected, this, _1)),
    readCallback(rCb),
    eofCallback(eofCb),
    disCallback(disCb),
    emptyCallback(eCb),
    idleCallback(iCb) {

    nonblocking(fd);
}

struct deleter
{
  template <typename T>
  void operator()(T *ptr){ delete ptr;}
};

AsynchIO::~AsynchIO() {
    std::for_each( bufferQueue.begin(), bufferQueue.end(), deleter());
    std::for_each( writeQueue.begin(), writeQueue.end(), deleter());
}

void AsynchIO::queueForDeletion() {
    DispatchHandle::doDelete();
}

void AsynchIO::start(Poller::shared_ptr poller) {
    DispatchHandle::startWatch(poller);
}

void AsynchIO::queueReadBuffer(Buffer* buff) {
    bufferQueue.push_front(buff);
    DispatchHandle::rewatchRead();
}

void AsynchIO::queueWrite(Buffer* buff) {
    writeQueue.push_front(buff);
    DispatchHandle::rewatchWrite();
}

/*
 * We keep on reading as long as we have something to read and a buffer to put
 * it in
 */
void AsynchIO::readable(DispatchHandle& h) {
    do {
        // (Try to) get a buffer
        if (!bufferQueue.empty()) {
            // Read into buffer
            Buffer* buff = bufferQueue.back();
            bufferQueue.pop_back();
            errno = 0;
            int rc = ::read(h.getFD(), buff->bytes, buff->byteCount);
            if (rc == 0) {
                eofCallback(*this);
                h.unwatchRead();
                return;
            } else if (rc > 0) {
                buff->dataStart = 0;
                buff->dataCount = rc;
                readCallback(*this, buff);
                if (rc != buff->byteCount) {
                    // If we didn't fill the read buffer then time to stop reading
                    return;
                }
            } else {
                // Put buffer back
                bufferQueue.push_back(buff);
                
                // This is effectively the same as eof
                if (errno == ECONNRESET) {
                    eofCallback(*this);
                    h.unwatchRead();
                    return;
                } else if (errno == EAGAIN) {
                    // We have just put a buffer back so we know
                    // we can carry on watching for reads
                    return;
                } else {
                    QPID_POSIX_CHECK(rc);
                }
            }
        } else {
            // Something to read but no buffer
            if (emptyCallback) {
                emptyCallback(*this);
            }
            // If we still have no buffers we can't do anything more
            if (bufferQueue.empty()) {
                h.unwatchRead();
                return;
            }
            
        }
    } while (true);
}

/*
 * We carry on writing whilst we have data to write and we can write
 */
void AsynchIO::writeable(DispatchHandle& h) {
    do {
        // See if we've got something to write
        if (!writeQueue.empty()) {
            // Write buffer
            Buffer* buff = writeQueue.back();
            writeQueue.pop_back();
            errno = 0;
            assert(buff->dataStart+buff->dataCount <= buff->byteCount);
            int rc = ::write(h.getFD(), buff->bytes+buff->dataStart, buff->dataCount);
            if (rc >= 0) {
                // If we didn't write full buffer put rest back
                if (rc != buff->dataCount) {
                    buff->dataStart += rc;
                    buff->dataCount -= rc;
                    writeQueue.push_back(buff);
                    return;
                }
                
                // Recycle the buffer
                queueReadBuffer(buff);
            } else {
                // Put buffer back
                writeQueue.push_back(buff);
                if (errno == ECONNRESET || errno == EPIPE) {
                    // Just stop watching for write here - we'll get a
                    // disconnect callback soon enough
                    h.unwatchWrite();
                    return;
                } else if (errno == EAGAIN) {
                    // We have just put a buffer back so we know
                    // we can carry on watching for writes
                    return;
                } else {
                    QPID_POSIX_CHECK(rc);
                }
            }
        } else {
            // Fd is writable, but nothing to write
            if (idleCallback) {
                idleCallback(*this);
            }
            // If we still have no buffers to write we can't do anything more
            if (writeQueue.empty()) {
                h.unwatchWrite();
                return;
            }
        }
    } while (true);
}
        
void AsynchIO::disconnected(DispatchHandle& h) {
    if (disCallback) {
        disCallback(*this);
        h.unwatch();
    }
}
