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
#include "Cluster.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/ClusterConnectionDeliverCloseBody.h"
#include "qpid/log/Statement.h"
#include "qpid/framing/AllInvoker.h"
#include <boost/current_function.hpp>

namespace qpid {
namespace cluster {

using namespace framing;

// Shadow connections
Connection::Connection(Cluster& c, sys::ConnectionOutputHandler& out,
                       const std::string& wrappedId, ConnectionId myId)
    : cluster(c), self(myId), catchUp(false), output(*this, out),
      connection(&output, cluster.getBroker(), wrappedId)
{
    QPID_LOG(debug, "New connection: " << *this);
}

// Local connections
Connection::Connection(Cluster& c, sys::ConnectionOutputHandler& out,
                       const std::string& wrappedId, MemberId myId, bool isCatchUp)
    : cluster(c), self(myId, this), catchUp(isCatchUp), output(*this, out),
      connection(&output, cluster.getBroker(), wrappedId)
{
    QPID_LOG(debug, "New connection: " << *this);
}

Connection::~Connection() {}

bool Connection::doOutput() {
    return output.doOutput();
}

// Delivery of doOutput allows us to run the real connection doOutput()
// which stocks up the write buffers with data.
//
void Connection::deliverDoOutput(uint32_t requested) {
    assert(!catchUp);
    output.deliverDoOutput(requested);
}

void Connection::received(framing::AMQFrame& f) {
    QPID_LOG(trace, "EXEC [" << *this << "]: " << f);
    // Handle connection controls, deliver other frames to connection.
    if (!framing::invoke(*this, *f.getBody()).wasHandled())
        connection.received(f);
}

void Connection::closed() {
    try {
        // Local network connection has closed.  We need to keep the
        // connection around but replace the output handler with a
        // no-op handler as the network output handler will be
        // deleted.
        output.setOutputHandler(discardHandler);
        if (catchUp) {
            catchUp = false;
            cluster.catchUpClosed(boost::intrusive_ptr<Connection>(this));
        }
        else {
            // This was a local replicated connection. Multicast a deliver closed
            // and process any outstanding frames from the cluster until
            // self-delivery of deliver-closed.
            cluster.mcastControl(ClusterConnectionDeliverCloseBody(), this);
            ++mcastSeq;
        }
    }
    catch (const std::exception& e) {
        QPID_LOG(error, QPID_MSG("While closing connection: " << e.what()));
    }
}

void Connection::deliverClose () {
    assert(!catchUp);
    connection.closed();
    cluster.erase(self);
}

size_t Connection::decode(const char* buffer, size_t size) { 
    assert(!catchUp);
    ++mcastSeq;
    cluster.mcastBuffer(buffer, size, self);
    return size;
}

void Connection::deliverBuffer(Buffer& buf) {
    assert(!catchUp);
    ++deliverSeq;
    while (decoder.decode(buf))
        received(decoder.frame);
}


void Connection::sessionState(const SequenceNumber& /*replayStart*/,
                  const SequenceSet& /*sentIncomplete*/,
                  const SequenceNumber& /*expected*/,
                  const SequenceNumber& /*received*/,
                  const SequenceSet& /*unknownCompleted*/,
                  const SequenceSet& /*receivedIncomplete*/)
{
    // FIXME aconway 2008-09-10: TODO
}
    
void Connection::shadowReady(uint64_t memberId, uint64_t connectionId) {
    ConnectionId shadow = ConnectionId(memberId, connectionId);
    QPID_LOG(debug, "Catch-up connection " << self << " becomes shadow " << shadow);
    self = shadow;
    assert(isShadow());
}

void Connection::dumpComplete() {
    // FIXME aconway 2008-09-18: use or remove.
}

bool Connection::isLocal() const { return self.first == cluster.getSelf() && self.second == this; }

std::ostream& operator<<(std::ostream& o, const Connection& c) {
    return o << c.getId() << "(" << (c.isLocal() ? "local" : "shadow")
             << (c.isCatchUp() ? ",catchup" : "") << ")";
}

}} // namespace qpid::cluster

