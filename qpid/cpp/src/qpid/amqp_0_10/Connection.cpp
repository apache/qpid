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
#include "Connection.h"
#include "qpid/log/Statement.h"
#include "qpid/amqp_0_10/exceptions.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/Buffer.h"
#include "qpid/framing/ProtocolInitiation.h"

namespace qpid {
namespace amqp_0_10 {

using sys::Mutex;

Connection::Connection(sys::OutputControl& o, const std::string& id, bool _isClient)
    : frameQueueClosed(false), output(o), identifier(id), initialized(false), isClient(_isClient), buffered(0)
{}

void Connection::setInputHandler(std::auto_ptr<sys::ConnectionInputHandler> c) {
    connection = c;
}

size_t  Connection::decode(const char* buffer, size_t size) {
    framing::Buffer in(const_cast<char*>(buffer), size);
    if (isClient && !initialized) {
        //read in protocol header
        framing::ProtocolInitiation pi;
        if (pi.decode(in)) {
            //TODO: check the version is correct
            QPID_LOG(trace, "RECV " << identifier << " INIT(" << pi << ")");
        }
        initialized = true;
    }
    framing::AMQFrame frame;
    while(frame.decode(in)) {
        QPID_LOG(trace, "RECV [" << identifier << "]: " << frame);
         connection->received(frame);
    }
    return in.getPosition();
}

bool Connection::canEncode() {
    if (!frameQueueClosed) connection->doOutput();
    Mutex::ScopedLock l(frameQueueLock);
    return (!isClient && !initialized) || !frameQueue.empty();
}

bool Connection::isClosed() const {
    Mutex::ScopedLock l(frameQueueLock);
    return frameQueueClosed;
}

size_t  Connection::encode(const char* buffer, size_t size) {
    {   // Swap frameQueue data into workQueue to avoid holding lock while we encode.
        Mutex::ScopedLock l(frameQueueLock);
        assert(workQueue.empty());
        workQueue.swap(frameQueue); 
    }
    framing::Buffer out(const_cast<char*>(buffer), size);
    if (!isClient && !initialized) {
        framing::ProtocolInitiation pi(getVersion());
        pi.encode(out);
        initialized = true;
        QPID_LOG(trace, "SENT " << identifier << " INIT(" << pi << ")");
    }
    size_t frameSize=0;
    size_t encoded=0;
    while (!workQueue.empty() && ((frameSize=workQueue.front().encodedSize()) <= out.available())) {
        workQueue.front().encode(out);
        QPID_LOG(trace, "SENT [" << identifier << "]: " << workQueue.front());
        workQueue.pop_front();
        encoded += frameSize;
        if (workQueue.empty() && out.available() > 0) connection->doOutput(); 
    }
    assert(workQueue.empty() || workQueue.front().encodedSize() <= size);
    if (!workQueue.empty() && workQueue.front().encodedSize() > size)
        throw InternalErrorException(QPID_MSG("Frame too large for buffer."));
    {
        Mutex::ScopedLock l(frameQueueLock);
        buffered -= encoded;
        // Put back any frames we did not encode.
        frameQueue.insert(frameQueue.begin(), workQueue.begin(), workQueue.end());
        workQueue.clear();
    }
    return out.getPosition();
}

void  Connection::activateOutput() { output.activateOutput(); }
void Connection::giveReadCredit(int32_t credit) { output.giveReadCredit(credit); }

void  Connection::close() {
    // Close the output queue.
    Mutex::ScopedLock l(frameQueueLock);
    frameQueueClosed = true;
}

void  Connection::closed() {
    connection->closed();
}

void Connection::send(framing::AMQFrame& f) {
    {
        Mutex::ScopedLock l(frameQueueLock);
	if (!frameQueueClosed)
            frameQueue.push_back(f);
        buffered += f.encodedSize();
    }
    activateOutput();
}

framing::ProtocolVersion Connection::getVersion() const {
    return framing::ProtocolVersion(0,10);
}

size_t Connection::getBuffered() const { 
    Mutex::ScopedLock l(frameQueueLock);
    return buffered;
}

}} // namespace qpid::amqp_0_10
