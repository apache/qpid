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

#include "Multicaster.h"
#include "qpid/cluster/Cpg.h"
#include "qpid/log/Statement.h"
#include "qpid/framing/AMQBody.h"
#include "qpid/framing/AMQDataBlock.h"
#include "qpid/framing/AMQFrame.h"

namespace qpid {
namespace cluster {

namespace {
const size_t MAX_IOV = 60;  // Limit imposed by CPG

struct ::iovec bufToIov(const BufferRef& buf) {
    ::iovec iov;
    iov.iov_base = buf.begin();
    iov.iov_len = buf.size();
    return iov;
}
}

Multicaster::Multicaster(Cpg& cpg_,
                         const boost::shared_ptr<sys::Poller>& poller,
                         boost::function<void()> onError_) :
    onError(onError_), cpg(cpg_),
    queue(boost::bind(&Multicaster::sendMcast, this, _1), poller),
    ioVector(MAX_IOV),
    buffers(64*1024)           // TODO aconway 2011-03-15: optimum size?
{
    queue.start();
}

void Multicaster::mcast(const framing::AMQFrame& data) {
    QPID_LOG(trace, "cluster multicast on " << cpg.getName() << ": " << data);
    BufferRef bufRef = buffers.get(data.encodedSize());
    framing::Buffer buf(bufRef.begin(), bufRef.size());
    data.encode(buf);
    queue.push(bufRef);
}

void Multicaster::mcast(const framing::AMQBody& body) {
    framing::AMQFrame f(body);
    mcast(f);
}

Multicaster::PollableEventQueue::Batch::const_iterator
Multicaster::sendMcast(const PollableEventQueue::Batch& buffers) {
    try {
        PollableEventQueue::Batch::const_iterator i = buffers.begin();
        while( i != buffers.end()) {
            size_t len = std::min(size_t(buffers.end() - i), MAX_IOV);
            PollableEventQueue::Batch::const_iterator j = i + len;
            std::transform(i, j, ioVector.begin(), &bufToIov);
            if (!cpg.mcast(&ioVector[0], len)) {
                // CPG didn't send because of CPG flow control.
                ::usleep(1000);    // Don't spin too tightly.
                return i;
            }
            i = j;
        }
        return i;
    }
    catch (const std::exception& e) {
        QPID_LOG(critical, "Multicast error: " << e.what());
        queue.stop();
        onError();
        return buffers.end();
    }
}

}} // namespace qpid::cluster
