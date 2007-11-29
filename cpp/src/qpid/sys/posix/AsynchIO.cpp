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
#include <sys/socket.h>
#include <signal.h>
#include <errno.h>

#include <boost/bind.hpp>

using namespace qpid::sys;

namespace {

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

AsynchAcceptor::AsynchAcceptor(const Socket& s, Callback callback) :
    acceptedCallback(callback),
    handle(s, boost::bind(&AsynchAcceptor::readable, this, _1), 0, 0) {

    s.setNonblocking();
    ignoreSigpipe();
}

void AsynchAcceptor::start(Poller::shared_ptr poller) {
    handle.startWatch(poller);
}

/*
 * We keep on accepting as long as there is something to accept
 */
void AsynchAcceptor::readable(DispatchHandle& h) {
    Socket* s;
    do {
        errno = 0;
        // TODO: Currently we ignore the peers address, perhaps we should
        // log it or use it for connection acceptance.
        s = h.getSocket().accept(0, 0);
        if (s) {
            acceptedCallback(*s);
        } else {
        	break;
        }
    } while (true);

    h.rewatch();
}

/*
 * Asynch reader/writer
 */
AsynchIO::AsynchIO(const Socket& s,
    ReadCallback rCb, EofCallback eofCb, DisconnectCallback disCb,
    ClosedCallback cCb, BuffersEmptyCallback eCb, IdleCallback iCb) :

    DispatchHandle(s, 
        boost::bind(&AsynchIO::readable, this, _1),
        boost::bind(&AsynchIO::writeable, this, _1),
        boost::bind(&AsynchIO::disconnected, this, _1)),
    readCallback(rCb),
    eofCallback(eofCb),
    disCallback(disCb),
    closedCallback(cCb),
    emptyCallback(eCb),
    idleCallback(iCb),
    queuedClose(false),
    writePending(false) {

    s.setNonblocking();
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

void AsynchIO::queueReadBuffer(BufferBase* buff) {
	assert(buff);
    buff->dataStart = 0;
    buff->dataCount = 0;
    bufferQueue.push_back(buff);
    DispatchHandle::rewatchRead();
}

void AsynchIO::unread(BufferBase* buff) {
	assert(buff);
	if (buff->dataStart != 0) {
		memmove(buff->bytes, buff->bytes+buff->dataStart, buff->dataCount);
		buff->dataStart = 0;
	}
    bufferQueue.push_front(buff);
    DispatchHandle::rewatchRead();
}

void AsynchIO::queueWrite(BufferBase* buff) {
    assert(buff);
    // If we've already closed the socket then throw the write away
    if (queuedClose) {
        bufferQueue.push_front(buff);
        return;
    } else {
        writeQueue.push_front(buff);
    }
    writePending = false;
    DispatchHandle::rewatchWrite();
}

void AsynchIO::notifyPendingWrite() {
    writePending = true;
    DispatchHandle::rewatchWrite();
}

void AsynchIO::queueWriteClose() {
    queuedClose = true;
    DispatchHandle::rewatchWrite();
}

/** Return a queued buffer if there are enough
 * to spare
 */
AsynchIO::BufferBase* AsynchIO::getQueuedBuffer() {
	// Always keep at least one buffer (it might have data that was "unread" in it)
	if (bufferQueue.size()<=1)
		return 0;
	BufferBase* buff = bufferQueue.back();
	buff->dataStart = 0;
	buff->dataCount = 0;
	bufferQueue.pop_back();
	return buff;
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
            BufferBase* buff = bufferQueue.front();
            bufferQueue.pop_front();
            errno = 0;
            int readCount = buff->byteCount-buff->dataCount;
            int rc = h.getSocket().read(buff->bytes + buff->dataCount, readCount);
            if (rc > 0) {
                buff->dataCount += rc;
                readCallback(*this, buff);
                if (rc != readCount) {
                    // If we didn't fill the read buffer then time to stop reading
                    return;
                }
            } else {
                // Put buffer back (at front so it doesn't interfere with unread buffers)
                bufferQueue.push_front(buff);
                
                // Eof or other side has gone away
                if (rc == 0 || errno == ECONNRESET) {
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
            BufferBase* buff = writeQueue.back();
            writeQueue.pop_back();
            errno = 0;
            assert(buff->dataStart+buff->dataCount <= buff->byteCount);
            int rc = h.getSocket().write(buff->bytes+buff->dataStart, buff->dataCount);
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
            // If we're waiting to close the socket then can do it now as there is nothing to write
            if (queuedClose) {
                close(h);
                return;
            }
            // Fd is writable, but nothing to write
            if (idleCallback) {
                writePending = false;
                idleCallback(*this);
            }
            // If we still have no buffers to write we can't do anything more
            if (writeQueue.empty() && !writePending && !queuedClose) {
                h.unwatchWrite();
                //the following handles the case where writePending is
                //set to true after the test above; in this case its
                //possible that the unwatchWrite overwrites the
                //desired rewatchWrite so we correct that here
                if (writePending) h.rewatchWrite();
                return;
            }
        }
    } while (true);
}
        
void AsynchIO::disconnected(DispatchHandle& h) {
    // If we've already queued close do it instead of disconnected callback
    if (queuedClose) {
        close(h);
    } else if (disCallback) {
        disCallback(*this);
        h.unwatch();
    }
}

/*
 * Close the socket and callback to say we've done it
 */
void AsynchIO::close(DispatchHandle& h) {
	h.stopWatch();
	h.getSocket().close();
	if (closedCallback) {
            closedCallback(*this, getSocket());
	}
}

