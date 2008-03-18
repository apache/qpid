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
#include "PreviewConnectionCodec.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace broker {

using sys::Mutex;

PreviewConnectionCodec::PreviewConnectionCodec(sys::OutputControl& o, Broker& broker, const std::string& id)
    : frameQueueClosed(false), output(o), connection(this, broker, id), identifier(id) {}

size_t  PreviewConnectionCodec::decode(const char* buffer, size_t size) {
    framing::Buffer in(const_cast<char*>(buffer), size);
    framing::AMQFrame frame;
    while(frame.decode(in)) {
        QPID_LOG(trace, "RECV [" << identifier << "]: " << frame);
        connection.received(frame);
    }
    return in.getPosition();
}

bool PreviewConnectionCodec::canEncode() {
    if (!frameQueueClosed) connection.doOutput();
    return !frameQueue.empty();
}

bool PreviewConnectionCodec::isClosed() const {
    Mutex::ScopedLock l(frameQueueLock);
    return frameQueueClosed;
}

size_t  PreviewConnectionCodec::encode(const char* buffer, size_t size) {
    Mutex::ScopedLock l(frameQueueLock);
    framing::Buffer out(const_cast<char*>(buffer), size);
    while (!frameQueue.empty() && (frameQueue.front().size() <= out.available())) {
            frameQueue.front().encode(out);
            QPID_LOG(trace, "SENT [" << identifier << "]: " << frameQueue.front());
            frameQueue.pop();
    }
    if (!frameQueue.empty() && frameQueue.front().size() > size)
        throw framing::ContentTooLargeException(QPID_MSG("Could not write frame, too large for buffer."));
    return out.getPosition();
}

void  PreviewConnectionCodec::activateOutput() { output.activateOutput(); }

void  PreviewConnectionCodec::close() {
    // Close the output queue.
    Mutex::ScopedLock l(frameQueueLock);
    frameQueueClosed = true;
}

void  PreviewConnectionCodec::closed() {
    connection.closed();
}

void PreviewConnectionCodec::send(framing::AMQFrame& f) {
    {
        Mutex::ScopedLock l(frameQueueLock);
	if (!frameQueueClosed)
            frameQueue.push(f);
    }
    activateOutput();
}

framing::ProtocolVersion PreviewConnectionCodec::getVersion() const {
    return framing::ProtocolVersion(99,0);
}

}} // namespace qpid::broker
