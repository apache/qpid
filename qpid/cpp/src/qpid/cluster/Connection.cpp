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
#include "UpdateClient.h"
#include "Cluster.h"

#include "qpid/broker/SessionState.h"
#include "qpid/broker/SemanticState.h"
#include "qpid/broker/TxBuffer.h"
#include "qpid/broker/TxPublish.h"
#include "qpid/broker/TxAccept.h"
#include "qpid/broker/RecoveredEnqueue.h"
#include "qpid/broker/RecoveredDequeue.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/Queue.h"
#include "qpid/framing/enum.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/AllInvoker.h"
#include "qpid/framing/DeliveryProperties.h"
#include "qpid/framing/ClusterConnectionDeliverCloseBody.h"
#include "qpid/framing/ConnectionCloseBody.h"
#include "qpid/framing/ConnectionCloseOkBody.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/LatencyMetric.h"

#include <boost/current_function.hpp>

// TODO aconway 2008-11-03:
// 
// Disproportionate amount of code here is dedicated to receiving an
// update when joining a cluster and building initial
// state. Should be separated out into its own classes.
//


namespace qpid {
namespace cluster {

using namespace framing;

NoOpConnectionOutputHandler Connection::discardHandler;

// Shadow connections
Connection::Connection(Cluster& c, sys::ConnectionOutputHandler& out,
                       const std::string& wrappedId, ConnectionId myId)
    : cluster(c), self(myId), catchUp(false), output(*this, out),
      connection(&output, cluster.getBroker(), wrappedId), readCredit(0), expectProtocolHeader(false)
{ init(); }

// Local connections
Connection::Connection(Cluster& c, sys::ConnectionOutputHandler& out,
                       const std::string& wrappedId, MemberId myId, bool isCatchUp, bool isLink)
    : cluster(c), self(myId, this), catchUp(isCatchUp), output(*this, out),
      connection(&output, cluster.getBroker(), wrappedId, isLink, catchUp ? ++catchUpId : 0), readCredit(0),
      expectProtocolHeader(isLink)
{ init(); }

void Connection::init() {
    QPID_LOG(debug, cluster << " new connection: " << *this);
    if (isLocalClient()) {
        cluster.addLocalConnection(this);
        if (cluster.getReadMax()) 
        output.giveReadCredit(cluster.getReadMax());
    }
}

Connection::~Connection() {
    QPID_LOG(debug, cluster << " deleted connection: " << *this);
}

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

// Received from a directly connected client.
void Connection::received(framing::AMQFrame& f) {
    QPID_LOG(trace, cluster << " RECV " << *this << ": " << f);
    if (isLocal()) {            // Local catch-up connection.
        currentChannel = f.getChannel();
        if (!framing::invoke(*this, *f.getBody()).wasHandled())
            connection.received(f);
    }
    else {             // Shadow or updated catch-up connection.
        if (f.getMethod() && f.getMethod()->isA<ConnectionCloseBody>()) {
            if (isShadow()) 
                cluster.addShadowConnection(this);
            AMQFrame ok((ConnectionCloseOkBody()));
            connection.getOutput().send(ok);
            output.setOutputHandler(discardHandler);
            catchUp = false;
        }
        else
            QPID_LOG(warning, cluster << " ignoring unexpected frame " << *this << ": " << f);
    }
}

bool Connection::checkUnsupported(const AMQBody& body) {
    std::string message;
    if (body.getMethod()) {
        switch (body.getMethod()->amqpClassId()) {
          case DTX_CLASS_ID: message = "DTX transactions are not currently supported by cluster."; break;
        }
    }
    else if (body.type() == HEADER_BODY) {
        const DeliveryProperties* dp = static_cast<const AMQHeaderBody&>(body).get<DeliveryProperties>();
        if (dp && dp->getTtl()) message = "Message TTL is not currently supported by cluster.";
    }
    if (!message.empty())
        connection.close(connection::CLOSE_CODE_FRAMING_ERROR, message);
    return !message.empty();
}

// Called in delivery thread, in cluster order.
void Connection::deliveredFrame(const EventFrame& f) {
    assert(!catchUp);
    currentChannel = f.frame.getChannel(); 
    if (!framing::invoke(*this, *f.frame.getBody()).wasHandled() // Connection contol.
        && !checkUnsupported(*f.frame.getBody())) // Unsupported operation.
    {
        connection.received(const_cast<AMQFrame&>(f.frame)); // Pass to broker connection.
    }
    if  (cluster.getReadMax() && f.readCredit)
        output.giveReadCredit(f.readCredit);
}

// A local connection is closed by the network layer.
void Connection::closed() {
    try {
        if (catchUp) {
            QPID_LOG(critical, cluster << " catch-up connection closed prematurely " << *this);
            cluster.leave();
        }
        else if (isUpdated()) {
            QPID_LOG(debug, cluster << " closed update connection " << *this);
            connection.closed();
        }
        else if (isLocal()) {
            QPID_LOG(debug, cluster << " local close of replicated connection " << *this);
            // This was a local replicated connection. Multicast a deliver
            // closed and process any outstanding frames from the cluster
            // until self-delivery of deliver-close.
            output.setOutputHandler(discardHandler);
            cluster.getMulticast().mcastControl(ClusterConnectionDeliverCloseBody(), self);
        }
    }
    catch (const std::exception& e) {
        QPID_LOG(error, cluster << " error closing connection " << *this << ": " << e.what());
    }
}

// Self-delivery of close message, close the connection.
void Connection::deliverClose () {
    assert(!catchUp);
    connection.closed();
    cluster.erase(self);
}

// Member of a shadow connection left the cluster.
void Connection::left() {
    assert(isShadow());
    connection.closed();
}

// Decode data from local clients.
size_t Connection::decode(const char* buffer, size_t size) {
    if (catchUp) {  // Handle catch-up locally.
        Buffer buf(const_cast<char*>(buffer), size);
        while (localDecoder.decode(buf))
            received(localDecoder.frame);
    }
    else {                      // Multicast local connections.
        assert(isLocal());
        const char* remainingData = buffer;
        size_t remainingSize = size;
        if (expectProtocolHeader) {
            //If this is an outgoing link, we will receive a protocol
            //header which needs to be decoded first
            framing::ProtocolInitiation pi;
            Buffer buf(const_cast<char*>(buffer), size);
            if (pi.decode(buf)) {
                //TODO: check the version is correct
                QPID_LOG(debug, "Outgoing clustered link connection received INIT(" << pi << ")");
                expectProtocolHeader = false;
                remainingData = buffer + pi.encodedSize();
                remainingSize = size - pi.encodedSize();
            } else {
                QPID_LOG(debug, "Not enough data for protocol header on outgoing clustered link");
                return 0;
            }
        }
        cluster.getMulticast().mcastBuffer(remainingData, remainingSize, self);
    }
    return size;
}

broker::SessionState& Connection::sessionState() {
    return *connection.getChannel(currentChannel).getSession();
}

broker::SemanticState& Connection::semanticState() {
    return sessionState().getSemanticState();
}

void Connection::consumerState(const string& name, bool blocked, bool notifyEnabled) {
    broker::SemanticState::ConsumerImpl& c = semanticState().find(name);
    c.setBlocked(blocked);
    if (notifyEnabled) c.enableNotify(); else c.disableNotify();
}

void Connection::sessionState(
    const SequenceNumber& replayStart,
    const SequenceNumber& sendCommandPoint,
    const SequenceSet& sentIncomplete,
    const SequenceNumber& expected,
    const SequenceNumber& received,
    const SequenceSet& unknownCompleted,
    const SequenceSet& receivedIncomplete)
{
    sessionState().setState(
        replayStart,
        sendCommandPoint,
        sentIncomplete,
        expected,
        received,
        unknownCompleted,
        receivedIncomplete);
    QPID_LOG(debug, cluster << " received session state update for " << sessionState().getId());
}
    
void Connection::shadowReady(uint64_t memberId, uint64_t connectionId) {
    ConnectionId shadow = ConnectionId(memberId, connectionId);
    QPID_LOG(debug, cluster << " catch-up connection " << *this << " becomes shadow " << shadow);
    self = shadow;
}

void Connection::membership(const FieldTable& joiners, const FieldTable& members) {
    QPID_LOG(debug, cluster << " incoming update complete on connection " << *this);
    cluster.updateInDone(ClusterMap(joiners, members));
    self.second = 0;        // Mark this as completed update connection.
}

bool Connection::isLocal() const {
    return self.first == cluster.getId() && self.second == this;
}

bool Connection::isShadow() const {
    return self.first != cluster.getId();
}

bool Connection::isUpdated() const {
    return self.first == cluster.getId() && self.second == 0;
}


shared_ptr<broker::Queue> Connection::findQueue(const std::string& qname) {
    shared_ptr<broker::Queue> queue = cluster.getBroker().getQueues().find(qname);
    if (!queue) throw Exception(QPID_MSG(cluster << " can't find queue " << qname));
    return queue;
}

broker::QueuedMessage Connection::getUpdateMessage() {
    broker::QueuedMessage m = findQueue(UpdateClient::UPDATE)->get();
    if (!m.payload) throw Exception(QPID_MSG(cluster << " empty update queue"));
    return m;
}

void Connection::deliveryRecord(const string& qname,
                                const SequenceNumber& position,
                                const string& tag,
                                const SequenceNumber& id,
                                bool acquired,
                                bool accepted,
                                bool cancelled,
                                bool completed,
                                bool ended,
                                bool windowing,
                                uint32_t credit)
{
    broker::QueuedMessage m;
    broker::Queue::shared_ptr queue = findQueue(qname);
    if (!ended) {               // Has a message
        if (acquired)           // Message is on the update queue
            m = getUpdateMessage();
        else                    // Message at original position in original queue
            m = queue->find(position);
        if (!m.payload)
            throw Exception(QPID_MSG("deliveryRecord no update message"));
    }

    broker::DeliveryRecord dr(m, queue, tag, acquired, accepted, windowing, credit);
    dr.setId(id);
    if (cancelled) dr.cancel(dr.getTag());
    if (completed) dr.complete();
    if (ended) dr.setEnded();   // Exsitance of message
    semanticState().record(dr); // Part of the session's unacked list.
}

void Connection::queuePosition(const string& qname, const SequenceNumber& position) {
    shared_ptr<broker::Queue> q = cluster.getBroker().getQueues().find(qname);
    if (!q) throw InvalidArgumentException(QPID_MSG("Invalid queue name " << qname));
    q->setPosition(position);
}

std::ostream& operator<<(std::ostream& o, const Connection& c) {
    const char* type="unknown";
    if (c.isLocal()) type = "local";
    else if (c.isShadow()) type = "shadow";
    else if (c.isUpdated()) type = "updated";
    return o << c.getId() << "(" << type << (c.isCatchUp() ? ",catchup" : "") << ")";
}

void Connection::txStart() {
    txBuffer = make_shared_ptr(new broker::TxBuffer());
}
void Connection::txAccept(const framing::SequenceSet& acked) {
    txBuffer->enlist(make_shared_ptr(new broker::TxAccept(acked, semanticState().getUnacked())));
}

void Connection::txDequeue(const std::string& queue) {
    txBuffer->enlist(make_shared_ptr(new broker::RecoveredDequeue(findQueue(queue), getUpdateMessage().payload)));
}

void Connection::txEnqueue(const std::string& queue) {
    txBuffer->enlist(make_shared_ptr(new broker::RecoveredEnqueue(findQueue(queue), getUpdateMessage().payload)));
}

void Connection::txPublish(const framing::Array& queues, bool delivered) {
    boost::shared_ptr<broker::TxPublish> txPub(new broker::TxPublish(getUpdateMessage().payload));
    for (framing::Array::const_iterator i = queues.begin(); i != queues.end(); ++i) 
        txPub->deliverTo(findQueue((*i)->get<std::string>()));
    txPub->delivered = delivered;
    txBuffer->enlist(txPub);
}

void Connection::txEnd() {
    semanticState().setTxBuffer(txBuffer);
}

void Connection::accumulatedAck(const qpid::framing::SequenceSet& s) {
    semanticState().setAccumulatedAck(s);
}

void Connection::exchange(const std::string& encoded) {
    Buffer buf(const_cast<char*>(encoded.data()), encoded.size());
    broker::Exchange::shared_ptr ex = broker::Exchange::decode(cluster.getBroker().getExchanges(), buf);
    QPID_LOG(debug, cluster << " decoded exchange " << ex->getName());    
}

void Connection::queue(const std::string& encoded) {
    Buffer buf(const_cast<char*>(encoded.data()), encoded.size());
    broker::Queue::shared_ptr q = broker::Queue::decode(cluster.getBroker().getQueues(), buf);
    QPID_LOG(debug, cluster << " decoded queue " << q->getName());    
}

qpid::sys::AtomicValue<uint64_t> Connection::catchUpId(0x5000000000000000LL);

}} // namespace qpid::cluster

