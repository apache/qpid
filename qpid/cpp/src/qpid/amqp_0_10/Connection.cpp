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

namespace qpid {
namespace amqp_0_10 {

using sys::Mutex;

Connection::Connection(sys::OutputControl& o, broker::Broker& broker, const std::string& id, bool _isClient)
    : frameQueueClosed(false), output(o),
      connection(new broker::Connection(this, broker, id, _isClient)),
      identifier(id), initialized(false), isClient(_isClient) {}

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
    Mutex::ScopedLock l(frameQueueLock);
    framing::Buffer out(const_cast<char*>(buffer), size);
    if (!isClient && !initialized) {
        framing::ProtocolInitiation pi(getVersion());
        pi.encode(out);
        initialized = true;
        QPID_LOG(trace, "SENT " << identifier << " INIT(" << pi << ")");
    }
    while (!frameQueue.empty() && (frameQueue.front().size() <= out.available())) {
            frameQueue.front().encode(out);
            QPID_LOG(trace, "SENT [" << identifier << "]: " << frameQueue.front());
            frameQueue.pop();
    }
    assert(frameQueue.empty() || frameQueue.front().size() <= size);
    if (!frameQueue.empty() && frameQueue.front().size() > size)
        throw InternalErrorException(QPID_MSG("Could not write frame, too large for buffer."));
    return out.getPosition();
}

void  Connection::activateOutput() { output.activateOutput(); }

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
            frameQueue.push(f);
    }
    activateOutput();
}

framing::ProtocolVersion Connection::getVersion() const {
    return framing::ProtocolVersion(0,10);
}

}} // namespace qpid::amqp_0_10
