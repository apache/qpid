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
#include "OutputInterceptor.h"
#include "Connection.h"
#include "Cluster.h"
#include "qpid/framing/ClusterConnectionDeliverDoOutputBody.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/log/Statement.h"
#include <boost/current_function.hpp>


namespace qpid {
namespace cluster {

using namespace framing;

OutputInterceptor::OutputInterceptor(cluster::Connection& p, sys::ConnectionOutputHandler& h)
    : parent(p), next(h), sent(), moreOutput(), doingOutput()
{}

void OutputInterceptor::send(framing::AMQFrame& f) {
    Locker l(lock); 
    next.send(f);
    sent += f.size();
}

void OutputInterceptor::activateOutput() {
    Locker l(lock); 
    moreOutput = true;
    sendDoOutput();             
}

// Called in write thread when the IO layer has no more data to write.
// We do nothing in the write thread, we run doOutput only on delivery
// of doOutput requests.
bool  OutputInterceptor::doOutput() {
    return false;
}

// Delivery of doOutput allows us to run the real connection doOutput()
// which stocks up the write buffers with data.
// 
void OutputInterceptor::deliverDoOutput(size_t requested) {
    Locker l(lock);
    size_t buf = next.getBuffered();
    if (parent.isLocal())
        writeEstimate.delivered(sent, buf); // Update the estimate.

    // Run the real doOutput() till we have added the requested data or there's nothing to output.
    sent = 0;
    do {
        sys::Mutex::ScopedUnlock u(lock);
        moreOutput = parent.getBrokerConnection().doOutput();
    } while (sent < requested && moreOutput);
    sent += buf;                // Include buffered data in the sent total.

    QPID_LOG(trace, "Delivered doOutput: requested=" << requested << " output=" << sent << " more=" << moreOutput);

    if (parent.isLocal() && moreOutput) 
        sendDoOutput();
    else
        doingOutput = false;
}

void OutputInterceptor::startDoOutput() {
    if (!doingOutput) 
        sendDoOutput();
}

// Send a doOutput request if one is not already in flight.
void OutputInterceptor::sendDoOutput() {
    // Call with lock held.
    // FIXME aconway 2008-08-28: used to  have || parent.getClosed())
    if (!parent.isLocal()) return;

    doingOutput = true;
    size_t request = writeEstimate.sending(getBuffered());
    
    // Note we may send 0 size request if there's more than 2*estimate in the buffer.
    // Send it anyway to keep the doOutput chain going until we are sure there's no more output
    // (in deliverDoOutput)
    // 
    parent.getCluster().mcastFrame(AMQFrame(in_place<ClusterConnectionDeliverDoOutputBody>(
                                          framing::ProtocolVersion(), request)), parent.getId());
    QPID_LOG(trace, &parent << "Send doOutput request for " << request);
}

}} // namespace qpid::cluster
