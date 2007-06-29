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

}

/*
 * Asynch Acceptor
 */

AsynchAcceptor::AsynchAcceptor(int fd, Callback callback) :
    acceptedCallback(callback),
    handle(fd, boost::bind(&AsynchAcceptor::readable, this, _1), 0) {

    nonblocking(fd);
}

void AsynchAcceptor::start(Poller::shared_ptr poller) {
    handle.watch(poller);
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
AsynchIO::AsynchIO(int fd, ReadCallback rCb, EofCallback eofCb, BuffersEmptyCallback eCb, IdleCallback iCb) :
    readCallback(rCb),
    eofCallback(eofCb),
    emptyCallback(eCb),
    idleCallback(iCb),
    handle(fd, boost::bind(&AsynchIO::readable, this, _1), boost::bind(&AsynchIO::writeable, this, _1)) {

    nonblocking(fd);
}

void AsynchIO::start(Poller::shared_ptr poller) {
    handle.watch(poller);
}

void AsynchIO::QueueReadBuffer(const Buffer& buff) {
    bufferQueue.push_front(buff);
    handle.rewatchRead();
}

void AsynchIO::QueueWrite(const Buffer& buff) {
    writeQueue.push_front(buff);
    handle.rewatchWrite();
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
            Buffer buff = bufferQueue.back();
            bufferQueue.pop_back();
            errno = 0;
            int rc = ::read(h.getFD(), buff.bytes, buff.byteCount);
            if (rc == 0) {
                eofCallback();    
            } else if (rc > 0) {
                readCallback(buff, rc);
            } else {
                // Put buffer back
                bufferQueue.push_back(buff);
                
                if (errno == EAGAIN) {
                    // We must have just put a buffer back so we know
                    // we can do this
                    h.rewatchRead();
                    return;
                } else {
                    QPID_POSIX_CHECK(rc);
                }
            }
        } else {
            // Something to read but no buffer
            if (emptyCallback) {
                emptyCallback();
            }
            // If we still have no buffers we can't do anything more
            if (bufferQueue.empty()) {
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
            Buffer buff = writeQueue.back();
            writeQueue.pop_back();
            errno = 0;
            int rc = ::write(h.getFD(), buff.bytes, buff.byteCount);
            if (rc >= 0) {
                // Recycle the buffer
                QueueReadBuffer(buff);
            } else {
                // Put buffer back
                writeQueue.push_back(buff);
                
                if (errno == EAGAIN) {
                    // We have just put a buffer back so we know
                    // we can do this
                    h.rewatchWrite();
                    return;
                } else {
                    QPID_POSIX_CHECK(rc);
                }
            }
        } else {
            // Something to read but no buffer
            if (idleCallback) {
                idleCallback(h.getFD());
            }
            // If we still have no buffers to write we can't do anything more
            if (writeQueue.empty()) {
                return;
            }
        }
    } while (true);
}
        
