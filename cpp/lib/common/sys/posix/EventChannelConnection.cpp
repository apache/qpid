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

#include <iostream>

#include <boost/bind.hpp>
#include <boost/assert.hpp>

#include "EventChannelConnection.h"
#include "sys/SessionHandlerFactory.h"
#include "QpidError.h"

using namespace std;
using namespace qpid;
using namespace qpid::framing;

namespace qpid {
namespace sys {

const size_t EventChannelConnection::bufferSize = 65536;
    
EventChannelConnection::EventChannelConnection(
    EventChannelThreads::shared_ptr threads_, 
    SessionHandlerFactory& factory_,
    int rfd,
    int wfd,
    bool isTrace_
) :
    readFd(rfd),
    writeFd(wfd ? wfd : rfd),
    readCallback(boost::bind(&EventChannelConnection::closeOnException,
                             this, &EventChannelConnection::endInitRead)),

    isWriting(false),
    isClosed(false),
    threads(threads_),
    handler(factory_.create(this)),
    in(bufferSize),
    out(bufferSize),
    isTrace(isTrace_)
{
    BOOST_ASSERT(readFd > 0);
    BOOST_ASSERT(writeFd > 0);
    closeOnException(&EventChannelConnection::startRead);
}


void EventChannelConnection::send(std::auto_ptr<AMQFrame> frame) {
    {
        Monitor::ScopedLock lock(monitor);
        assert(frame.get());
        writeFrames.push_back(frame.release());
    }
    closeOnException(&EventChannelConnection::startWrite);
}

void EventChannelConnection::close() {
    {
        Monitor::ScopedLock lock(monitor);
        if (isClosed)
            return;
        isClosed = true;
    }
    ::close(readFd);
    ::close(writeFd);
    {
        Monitor::ScopedLock lock(monitor);
        while (busyThreads > 0)
            monitor.wait();
    }
    handler->closed();
}

void EventChannelConnection::closeNoThrow() {
    Exception::tryCatchLog<void>(
        boost::bind(&EventChannelConnection::close, this),
        false,
        "Exception closing channel"
    );
}

/**
 * Call f in a try/catch block and close the connection if 
 * an exception is thrown.
 */
void EventChannelConnection::closeOnException(MemberFnPtr f)
{
    try {
        Exception::tryCatchLog<void>(
            boost::bind(f, this),
            "Closing connection due to exception"
        );
        return;
    } catch (...) {
        // Exception was already logged by tryCatchLog
        closeNoThrow();
    }
}
    
// Post the write event.
// Always called inside closeOnException.
// Called by endWrite and send, but only one thread writes at a time.
// 
void EventChannelConnection::startWrite() {
    FrameQueue::auto_type frame;
    {
        Monitor::ScopedLock lock(monitor);
        // Stop if closed or a write event is already in progress.
        if (isClosed || isWriting)
            return;
        if (writeFrames.empty()) {
            isWriting = false;
            return;
        }
        isWriting = true;
        frame = writeFrames.pop_front();
    }
    // No need to lock here - only one thread can be writing at a time.
    out.clear();
    if (isTrace)
        cout << "Send on socket " << writeFd << ": " << *frame << endl;
    frame->encode(out);
    out.flip();
    writeEvent = WriteEvent(
        writeFd, out.start(), out.available(),
        boost::bind(&EventChannelConnection::closeOnException,
                    this, &EventChannelConnection::endWrite));
    threads->post(writeEvent);
}

// ScopedBusy ctor increments busyThreads.
// dtor decrements and calls monitor.notifyAll if it reaches 0.
// 
struct EventChannelConnection::ScopedBusy : public AtomicCount::ScopedIncrement
{
    ScopedBusy(EventChannelConnection& ecc)
        : AtomicCount::ScopedIncrement(
            ecc.busyThreads, boost::bind(&Monitor::notifyAll, &ecc.monitor))
    {}
};

// Write event completed.
// Always called by a channel thread inside closeOnException.
// 
void EventChannelConnection::endWrite() {
    ScopedBusy(*this);
    {
        Monitor::ScopedLock lock(monitor);
        isWriting = false;
        if (isClosed)
            return;
        writeEvent.throwIfException();
    }
    // Check if there's more in to write in the write queue.
    startWrite();
}
    

// Post the read event.
// Always called inside closeOnException.
// Called from ctor and end[Init]Read, so only one call at a time
// is possible since we only post one read event at a time.
// 
void EventChannelConnection::startRead() {
    // Non blocking read, as much as we can swallow.
    readEvent = ReadEvent(
        readFd, in.start(), in.available(), readCallback,true);
    threads->post(readEvent);
}

// Completion of initial read, expect protocolInit.
// Always called inside closeOnException in channel thread.
// Only called by one thread at a time.
void EventChannelConnection::endInitRead() {
    ScopedBusy(*this);
    if (!isClosed) {
        readEvent.throwIfException();
        in.move(readEvent.getBytesRead());
        in.flip();
        ProtocolInitiation protocolInit;
        if(protocolInit.decode(in)){
            handler->initiated(&protocolInit);
            readCallback = boost::bind(
                &EventChannelConnection::closeOnException,
                this, &EventChannelConnection::endRead);
        }
        in.compact();
        // Continue reading.
        startRead();
    }
}
    
// Normal reads, expect a frame.
// Always called inside closeOnException in channel thread.
void EventChannelConnection::endRead() {
    ScopedBusy(*this);
    if (!isClosed) {
        readEvent.throwIfException();
        in.move(readEvent.getBytesRead());
        in.flip();
        AMQFrame frame;
        while (frame.decode(in)) {
            // TODO aconway 2006-11-30: received should take Frame&
            if (isTrace)
                cout << "Received on socket " << readFd
                     << ": " << frame << endl;
            handler->received(&frame); 
        }
        in.compact();
        startRead();
    }
}

}} // namespace qpid::sys
