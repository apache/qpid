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

NoOpConnectionOutputHandler OutputInterceptor::discardHandler;

OutputInterceptor::OutputInterceptor(Connection& p, sys::ConnectionOutputHandler& h)
    : parent(p), closing(false), next(&h), sent(),
      estimate(p.getCluster().getSettings().writeEstimate*1024),
      minimum(p.getCluster().getSettings().writeMin*1024),
      moreOutput(), doingOutput()
{}

void OutputInterceptor::send(framing::AMQFrame& f) {
    parent.getCluster().checkQuorum();
    {
        // FIXME aconway 2009-04-28: locking around next-> may be redundant
        // with the fixes to read-credit in the IO layer. Review.
        sys::Mutex::ScopedLock l(lock);
        next->send(f);
    }
    if (!parent.isCatchUp())
        sent += f.encodedSize();
}

void OutputInterceptor::activateOutput() {
    if (parent.isCatchUp()) {
        sys::Mutex::ScopedLock l(lock);
        next->activateOutput();
    }
    else if (!closing) {        // Don't send do ouput after output stopped.
        QPID_LOG(trace,  parent << " activateOutput - sending doOutput");
        moreOutput = true;
        sendDoOutput(estimate);
    }
}

void OutputInterceptor::giveReadCredit(int32_t credit) {
    sys::Mutex::ScopedLock l(lock);
    next->giveReadCredit(credit);
}

// Called in write thread when the IO layer has no more data to write.
// We do nothing in the write thread, we run doOutput only on delivery
// of doOutput requests.
bool  OutputInterceptor::doOutput() { return false; }

// Delivery of doOutput allows us to run the real connection doOutput()
// which tranfers frames to the codec for writing.
// 
void OutputInterceptor::deliverDoOutput(size_t requested) {
    size_t buf = getBuffered();
    if (parent.isLocal()) {  // Adjust estimate for next sendDoOutput 
        sent = sent > buf ? sent - buf : 0; // Buffered data was not sent.
        if (buf > 0)          // Wrote to capacity, move estimate towards sent.
            estimate = (estimate + sent) /2;  
        else if (sent >= estimate) // Last estimate was too small, increase it.
            estimate *= 2;  
        if (estimate < minimum) estimate = minimum;
    }
    // Run the real doOutput() till we have added the requested data
    // or there's nothing to output. Record how much we send.
    sent = 0;
    do {
        moreOutput = parent.getBrokerConnection().doOutput();
    } while (sent < requested && moreOutput);
    sent += buf;                // Include data previously in the buffer

    if (parent.isLocal()) {
        // Send the next doOutput request
        doingOutput = false;
        sendDoOutput(estimate); // FIXME aconway 2009-04-28: account for data in buffer?
    }
}

// Send a doOutput request if one is not already in flight.
void OutputInterceptor::sendDoOutput(size_t request) {
    if (!parent.isLocal() || doingOutput || !moreOutput) return;
    doingOutput = true;
    parent.getCluster().getMulticast().mcastControl(
        ClusterConnectionDeliverDoOutputBody(ProtocolVersion(), estimate), parent.getId());
    QPID_LOG(trace, parent << "Send doOutput request for " << request);
}

void OutputInterceptor::closeOutput() {
    sys::Mutex::ScopedLock l(lock);
    closing = true;
    next = &discardHandler;
}

void OutputInterceptor::close() {
    sys::Mutex::ScopedLock l(lock);
    next->close();
}

size_t OutputInterceptor::getBuffered() const {
    sys::Mutex::ScopedLock l(lock);
    return next->getBuffered();
}

}} // namespace qpid::cluster
