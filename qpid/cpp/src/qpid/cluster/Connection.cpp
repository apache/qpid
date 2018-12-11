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
#include "qpid/amqp_0_10/Codecs.h"
#include "Connection.h"
#include "UpdateClient.h"
#include "Cluster.h"
#include "UpdateReceiver.h"
#include "qpid/assert.h"
#include "qpid/broker/DtxAck.h"
#include "qpid/broker/DtxBuffer.h"
#include "qpid/broker/SessionState.h"
#include "qpid/broker/SemanticState.h"
#include "qpid/broker/TxBuffer.h"
#include "qpid/broker/TxPublish.h"
#include "qpid/broker/TxAccept.h"
#include "qpid/broker/RecoveredEnqueue.h"
#include "qpid/broker/RecoveredDequeue.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/Fairshare.h"
#include "qpid/broker/Link.h"
#include "qpid/broker/Bridge.h"
#include "qpid/broker/StatefulQueueObserver.h"
#include "qpid/broker/Queue.h"
#include "qpid/framing/enum.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/AllInvoker.h"
#include "qpid/framing/DeliveryProperties.h"
#include "qpid/framing/ClusterConnectionDeliverCloseBody.h"
#include "qpid/framing/ClusterConnectionAnnounceBody.h"
#include "qpid/framing/ConnectionCloseBody.h"
#include "qpid/framing/ConnectionCloseOkBody.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/ClusterSafe.h"
#include "qpid/types/Variant.h"
#include "qpid/management/ManagementAgent.h"
#include <boost/current_function.hpp>


namespace qpid {
namespace cluster {

using namespace framing;
using namespace framing::cluster;
using amqp_0_10::ListCodec;
using types::Variant;

qpid::sys::AtomicValue<uint64_t> Connection::catchUpId(0x5000000000000000LL);

Connection::NullFrameHandler Connection::nullFrameHandler;

struct NullFrameHandler : public framing::FrameHandler {
    void handle(framing::AMQFrame&) {}
};


namespace {
sys::AtomicValue<uint64_t> idCounter;
const std::string shadowPrefix("[shadow]");
}


// Shadow connection
Connection::Connection(Cluster& c, sys::ConnectionOutputHandler& out,
                       const std::string& mgmtId,
                       const ConnectionId& id, const qpid::sys::SecuritySettings& external)
    : cluster(c), self(id), catchUp(false), announced(false), output(*this, out),
      connectionCtor(&output, cluster.getBroker(), mgmtId, external, false, 0, true),
      expectProtocolHeader(false),
      mcastFrameHandler(cluster.getMulticast(), self),
      updateIn(c.getUpdateReceiver()),
      secureConnection(0)
{}

// Local connection
Connection::Connection(Cluster& c, sys::ConnectionOutputHandler& out,
                       const std::string& mgmtId, MemberId member,
                       bool isCatchUp, bool isLink, const qpid::sys::SecuritySettings& external
) : cluster(c), self(member, ++idCounter), catchUp(isCatchUp), announced(false), output(*this, out),
    connectionCtor(&output, cluster.getBroker(),
                   mgmtId,
                   external,
                   isLink,
                   isCatchUp ? ++catchUpId : 0,
                   isCatchUp),  // isCatchUp => shadow
    expectProtocolHeader(isLink),
    mcastFrameHandler(cluster.getMulticast(), self),
    updateIn(c.getUpdateReceiver()),
    secureConnection(0)
{
    if (isLocalClient()) {
        giveReadCredit(cluster.getSettings().readMax); // Flow control
        // Delay adding the connection to the management map until announce()
        connectionCtor.delayManagement = true;
    }
    else {
        // Catch-up shadow connections initialized using nextShadow id.
        assert(catchUp);
        if (!updateIn.nextShadowMgmtId.empty())
            connectionCtor.mgmtId = updateIn.nextShadowMgmtId;
        updateIn.nextShadowMgmtId.clear();
    }
    init();
    QPID_LOG(debug, cluster << " local connection " << *this);
}

void Connection::setSecureConnection(broker::SecureConnection* sc) {
    secureConnection = sc;
    if (connection.get()) connection->setSecureConnection(sc);
}

void Connection::init() {
    connection = connectionCtor.construct();
    if (isLocalClient()) {
        if (secureConnection) connection->setSecureConnection(secureConnection);
        // Actively send cluster-order frames from local node
        connection->setClusterOrderOutput(mcastFrameHandler);
    }
    else {                      // Shadow or catch-up connection
        // Passive, discard cluster-order frames
        connection->setClusterOrderOutput(nullFrameHandler);
        // Disable client throttling, done by active node.
        connection->setClientThrottling(false);
    }
    if (!isCatchUp())
        connection->setErrorListener(this);
}

// Called when we have consumed a read buffer to give credit to the
// connection layer to continue reading.
void Connection::giveReadCredit(int credit) {
    if (cluster.getSettings().readMax && credit)
        output.giveReadCredit(credit);
}

void Connection::announce(
    const std::string& mgmtId, uint32_t ssf, const std::string& authid, bool nodict,
    const std::string& username, const std::string& initialFrames)
{
    QPID_ASSERT(mgmtId == connectionCtor.mgmtId);
    QPID_ASSERT(ssf == connectionCtor.external.ssf);
    QPID_ASSERT(authid == connectionCtor.external.authid);
    QPID_ASSERT(nodict == connectionCtor.external.nodict);
    // Local connections are already initialized but with management delayed.
    if (isLocalClient()) {
        connection->addManagementObject();
    }
    else if (isShadow()) {
        init();
        // Play initial frames into the connection.
        Buffer buf(const_cast<char*>(initialFrames.data()), initialFrames.size());
        AMQFrame frame;
        while (frame.decode(buf))
            connection->received(frame);
        connection->setUserId(username);
    }
    // Do managment actions now that the connection is replicated.
    connection->raiseConnectEvent();
    QPID_LOG(debug, cluster << " replicated connection " << *this);
}

Connection::~Connection() {
    if (connection.get()) connection->setErrorListener(0);
    // Don't trigger cluster-safe asserts in broker:: ~Connection as
    // it may be called in an IO thread context during broker
    // shutdown.
    sys::ClusterSafeScope css;
    connection.reset();
}

bool Connection::doOutput() {
    return output.doOutput();
}

// Received from a directly connected client.
void Connection::received(framing::AMQFrame& f) {
    if (!connection.get()) {
        QPID_LOG(warning, cluster << " ignoring frame on closed connection "
                 << *this << ": " << f);
        return;
    }
    QPID_LOG_IF(trace, Cluster::loggable(f), cluster << " RECV " << *this << ": " << f);
    if (isLocal()) {            // Local catch-up connection.
        currentChannel = f.getChannel();
        if (!framing::invoke(*this, *f.getBody()).wasHandled())
            connection->received(f);
    }
    else {             // Shadow or updated catch-up connection.
        if (f.getMethod() && f.getMethod()->isA<ConnectionCloseBody>()) {
            if (isShadow())
                cluster.addShadowConnection(this);
            AMQFrame ok((ConnectionCloseOkBody()));
            connection->getOutput().send(ok);
            output.closeOutput();
            catchUp = false;
        }
        else
            QPID_LOG(warning, cluster << " ignoring unexpected frame " << *this << ": " << f);
    }
}

bool Connection::checkUnsupported(const AMQBody&) {
    // Throw an exception for unsupported commands. Currently all are supported.
    return false;
}

struct GiveReadCreditOnExit {
    Connection& connection;
    int credit;
    GiveReadCreditOnExit(Connection& connection_, int credit_) :
        connection(connection_), credit(credit_) {}
    ~GiveReadCreditOnExit() { if (credit) connection.giveReadCredit(credit); }
};

void Connection::deliverDoOutput(uint32_t limit) {
    output.deliverDoOutput(limit);
}

// Called in delivery thread, in cluster order.
void Connection::deliveredFrame(const EventFrame& f) {
    GiveReadCreditOnExit gc(*this, f.readCredit);
    assert(!catchUp);
    currentChannel = f.frame.getChannel();
    if (f.frame.getBody()       // frame can be emtpy with just readCredit
        && !framing::invoke(*this, *f.frame.getBody()).wasHandled() // Connection contol.
        && !checkUnsupported(*f.frame.getBody())) // Unsupported operation.
    {
        if (f.type == DATA) // incoming data frames to broker::Connection
            connection->received(const_cast<AMQFrame&>(f.frame));
        else {           // frame control, send frame via SessionState
            broker::SessionState* ss = connection->getChannel(currentChannel).getSession();
            if (ss) ss->out(const_cast<AMQFrame&>(f.frame));
        }
    }
}

// A local connection is closed by the network layer. Called in the connection thread.
void Connection::closed() {
    try {
        if (isUpdated()) {
            QPID_LOG(debug, cluster << " update connection closed " << *this);
            close();
            cluster.updateInClosed();
        }
        else if (catchUp && cluster.isExpectingUpdate()) {
            QPID_LOG(critical, cluster << " catch-up connection closed prematurely " << *this);
            cluster.leave();
        }
        else if (isLocal()) {
            // This was a local replicated connection. Multicast a deliver
            // closed and process any outstanding frames from the cluster
            // until self-delivery of deliver-close.
            output.closeOutput();
            if (announced)
                cluster.getMulticast().mcastControl(
                    ClusterConnectionDeliverCloseBody(), self);
        }
    }
    catch (const std::exception& e) {
        QPID_LOG(error, cluster << " error closing connection " << *this << ": " << e.what());
    }
}

// Self-delivery of close message, close the connection.
void Connection::deliverClose () {
    close();
    cluster.erase(self);
}

// Close the connection
void Connection::close() {
    if (connection.get()) {
        QPID_LOG(debug, cluster << " closed connection " << *this);
        connection->closed();
        connection.reset();
    }
}

// The connection has sent invalid data and should be aborted.
// All members will get the same abort since they all process the same data.
void Connection::abort() {
    connection->abort();
    // Aborting the connection will result in a call to ::closed()
    // and allow the connection to close in an orderly manner.
}

// ConnectionCodec::decode receives read buffers from  directly-connected clients.
size_t Connection::decode(const char* data, size_t size) {
    GiveReadCreditOnExit grc(*this, 1);   // Give a read credit by default.
    const char* ptr = data;
    const char* end = data + size;
    if (catchUp) {              // Handle catch-up locally.
        if (!cluster.isExpectingUpdate()) {
            QPID_LOG(error, "Rejecting unexpected catch-up connection.");
            abort();            // Cluster is not expecting catch-up connections.
        }
        bool wasOpen = connection->isOpen();
        Buffer buf(const_cast<char*>(ptr), size);
        ptr += size;
        while (localDecoder.decode(buf))
            received(localDecoder.getFrame());
        if (!wasOpen && connection->isOpen()) {
            // Connections marked with setUserProxyAuth are allowed to proxy
            // messages with user-ID that doesn't match the connection's
            // authenticated ID. This is important for updates.
            connection->setUserProxyAuth(isCatchUp());
        }
    }
    else {                      // Multicast local connections.
        assert(isLocalClient());
        assert(connection.get());
        if (!checkProtocolHeader(ptr, size)) // Updates ptr
            return 0; // Incomplete header

        if (!connection->isOpen())
            processInitialFrames(ptr, end-ptr); // Updates ptr

        if (connection->isOpen() && end - ptr > 0) {
            // We're multi-casting, we will give read credit on delivery.
            grc.credit = 0;
            cluster.getMulticast().mcastBuffer(ptr, end - ptr, self);
            ptr = end;
        }
    }
    return ptr - data;
}

// Decode the protocol header if needed. Updates data and size
// returns true if the header is complete or already read.
bool Connection::checkProtocolHeader(const char*& data, size_t size) {
    if (expectProtocolHeader) {
        // This is an outgoing link connection, we will receive a protocol
        // header which needs to be decoded first
        framing::ProtocolInitiation pi;
        Buffer buf(const_cast<char*&>(data), size);
        if (pi.decode(buf)) {
            //TODO: check the version is correct
            expectProtocolHeader = false;
            data += pi.encodedSize();
        } else {
            return false;
        }
    }
    return true;
}

void Connection::processInitialFrames(const char*& ptr, size_t size) {
    // Process the initial negotiation locally and store it so
    // it can be replayed on other brokers in announce()
    Buffer buf(const_cast<char*>(ptr), size);
    framing::AMQFrame frame;
    while (!connection->isOpen() && frame.decode(buf))
        received(frame);
    initialFrames.append(ptr, buf.getPosition());
    ptr += buf.getPosition();
    if (connection->isOpen()) { // initial negotiation complete
        cluster.getMulticast().mcastControl(
            ClusterConnectionAnnounceBody(
                ProtocolVersion(),
                connectionCtor.mgmtId,
                connectionCtor.external.ssf,
                connectionCtor.external.authid,
                connectionCtor.external.nodict,
                connection->getUserId(),
                initialFrames),
            getId());
        announced = true;
        initialFrames.clear();
    }
}

broker::SessionState& Connection::sessionState() {
    return *connection->getChannel(currentChannel).getSession();
}

broker::SemanticState& Connection::semanticState() {
    return sessionState().getSemanticState();
}

void Connection::shadowPrepare(const std::string& mgmtId) {
    updateIn.nextShadowMgmtId = mgmtId;
}

void Connection::shadowSetUser(const std::string& userId) {
    connection->setUserId(userId);
}

void Connection::consumerState(const string& name, bool blocked, bool notifyEnabled, const SequenceNumber& position)
{
    broker::SemanticState::ConsumerImpl& c = semanticState().find(name);
    c.position = position;
    c.setBlocked(blocked);
    if (notifyEnabled) c.enableNotify(); else c.disableNotify();
    updateIn.consumerNumbering.add(c.shared_from_this());
}


void Connection::sessionState(
    const SequenceNumber& replayStart,
    const SequenceNumber& sendCommandPoint,
    const SequenceSet& sentIncomplete,
    const SequenceNumber& expected,
    const SequenceNumber& received,
    const SequenceSet& unknownCompleted,
    const SequenceSet& receivedIncomplete,
    bool dtxSelected)
{
    sessionState().setState(
        replayStart,
        sendCommandPoint,
        sentIncomplete,
        expected,
        received,
        unknownCompleted,
        receivedIncomplete);
    if (dtxSelected) semanticState().selectDtx();
    QPID_LOG(debug, cluster << " received session state update for "
             << sessionState().getId());
    // The output tasks will be added later in the update process.
    connection->getOutputTasks().removeAll();
}

void Connection::outputTask(uint16_t channel, const std::string& name) {
    broker::SessionState* session = connection->getChannel(channel).getSession();
    if (!session)
        throw Exception(QPID_MSG(cluster << " channel not attached " << *this
                                 << "[" <<  channel << "] "));
    OutputTask* task = &session->getSemanticState().find(name);
    connection->getOutputTasks().addOutputTask(task);
}

void Connection::shadowReady(
    uint64_t memberId, uint64_t connectionId, const string& mgmtId,
    const string& username, const string& fragment, uint32_t sendMax)
{
    QPID_ASSERT(mgmtId == getBrokerConnection()->getMgmtId());
    ConnectionId shadowId = ConnectionId(memberId, connectionId);
    QPID_LOG(debug, cluster << " catch-up connection " << *this
             << " becomes shadow " << shadowId);
    self = shadowId;
    connection->setUserId(username);
    // OK to use decoder here because cluster is stalled for update.
    cluster.getDecoder().get(self).setFragment(fragment.data(), fragment.size());
    connection->setErrorListener(this);
    output.setSendMax(sendMax);
}

void Connection::setDtxBuffer(const UpdateReceiver::DtxBufferRef& bufRef) {
    broker::DtxManager& mgr = cluster.getBroker().getDtxManager();
    broker::DtxWorkRecord* record = mgr.getWork(bufRef.xid);
    broker::DtxBuffer::shared_ptr buffer = (*record)[bufRef.index];
    if (bufRef.suspended)
        bufRef.semanticState->getSuspendedXids()[bufRef.xid] = buffer;
    else
        bufRef.semanticState->setDtxBuffer(buffer);
}

// Marks the end of the update.
void Connection::membership(const FieldTable& joiners, const FieldTable& members,
                            const framing::SequenceNumber& frameSeq)
{
    QPID_LOG(debug, cluster << " incoming update complete on connection " << *this);
    updateIn.consumerNumbering.clear();
    for_each(updateIn.dtxBuffers.begin(), updateIn.dtxBuffers.end(),
             boost::bind(&Connection::setDtxBuffer, this, _1));
    closeUpdated();
    cluster.updateInDone(ClusterMap(joiners, members, frameSeq));
}

void Connection::retractOffer() {
    QPID_LOG(info, cluster << " incoming update retracted on connection " << *this);
    closeUpdated();
    cluster.updateInRetracted();
}

void Connection::closeUpdated() {
    self.second = 0;      // Mark this as completed update connection.
    if (connection.get())
        connection->close(connection::CLOSE_CODE_NORMAL, "OK");
}

bool Connection::isLocal() const {
    return self.first == cluster.getId() && self.second;
}

bool Connection::isShadow() const {
    return self.first != cluster.getId();
}

bool Connection::isUpdated() const {
    return self.first == cluster.getId() && self.second == 0;
}


boost::shared_ptr<broker::Queue> Connection::findQueue(const std::string& qname) {
    boost::shared_ptr<broker::Queue> queue = cluster.getBroker().getQueues().find(qname);
    if (!queue) throw Exception(QPID_MSG(cluster << " can't find queue " << qname));
    return queue;
}

broker::QueuedMessage Connection::getUpdateMessage() {
    boost::shared_ptr<broker::Queue> updateq = findQueue(UpdateClient::UPDATE);
    assert(!updateq->isDurable());
    broker::QueuedMessage m = updateq->get();
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
                                bool enqueued,
                                uint32_t credit)
{
    broker::QueuedMessage m;
    broker::Queue::shared_ptr queue = findQueue(qname);
    if (!ended) {               // Has a message
        if (acquired) {         // Message is on the update queue
            m = getUpdateMessage();
            m.queue = queue.get();
            m.position = position;
            if (enqueued) queue->updateEnqueued(m); //inform queue of the message
        } else {                // Message at original position in original queue
            m = queue->find(position);
        }
        // Removed this line:
        // if (!m.payload)
        //      throw Exception(QPID_MSG("deliveryRecord no update message"));
        //
        // It seems this could happen legitimately in the case one
        // session browses message M, then another session acquires
        // it. In that case the browsers delivery record is !acquired
        // but the message is not on its original Queue. In that case
        // we'll get a deliveryRecord with no payload for the browser.
        //
    }

    broker::DeliveryRecord dr(m, queue, tag, acquired, accepted, windowing, credit);
    dr.setId(id);
    if (cancelled) dr.cancel(dr.getTag());
    if (completed) dr.complete();
    if (ended) dr.setEnded();   // Exsitance of message

    if (dtxBuffer)              // Record for next dtx-ack
        dtxAckRecords.push_back(dr);
    else
        semanticState().record(dr); // Record on session's unacked list.
}

void Connection::queuePosition(const string& qname, const SequenceNumber& position) {
    findQueue(qname)->setPosition(position);
}

void Connection::queueFairshareState(const std::string& qname, const uint8_t priority, const uint8_t count)
{
    if (!qpid::broker::Fairshare::setState(findQueue(qname)->getMessages(), priority, count)) {
        QPID_LOG(error, "Failed to set fair share state on queue " << qname << "; this will result in inconsistencies.");
    }
}


namespace {
// find a StatefulQueueObserver that matches a given identifier
class ObserverFinder {
    const std::string id;
    boost::shared_ptr<broker::QueueObserver> target;
    ObserverFinder(const ObserverFinder&) {}
  public:
    ObserverFinder(const std::string& _id) : id(_id) {}
    broker::StatefulQueueObserver *getObserver()
    {
        if (target)
            return dynamic_cast<broker::StatefulQueueObserver *>(target.get());
        return 0;
    }
    void operator() (boost::shared_ptr<broker::QueueObserver> o)
    {
        if (!target) {
            broker::StatefulQueueObserver *p = dynamic_cast<broker::StatefulQueueObserver *>(o.get());
            if (p && p->getId() == id) {
                target = o;
            }
        }
    }
};
}


void Connection::queueObserverState(const std::string& qname, const std::string& observerId, const FieldTable& state)
{
    boost::shared_ptr<broker::Queue> queue(findQueue(qname));
    ObserverFinder finder(observerId);      // find this observer
    queue->eachObserver<ObserverFinder &>(finder);
    broker::StatefulQueueObserver *so = finder.getObserver();
    if (so) {
        so->setState( state );
        QPID_LOG(debug, "updated queue observer " << observerId << "'s state on queue " << qname << "; ...");
        return;
    }
    QPID_LOG(error, "Failed to find observer " << observerId << " state on queue " << qname << "; this will result in inconsistencies.");
}

std::ostream& operator<<(std::ostream& o, const Connection& c) {
    const char* type="unknown";
    if (c.isLocal()) type = "local";
    else if (c.isShadow()) type = "shadow";
    else if (c.isUpdated()) type = "updated";
    const broker::Connection* bc = c.getBrokerConnection();
    if (bc) o << bc->getMgmtId();
    else o << "<disconnected>";
    return o << "(" << c.getId() << " " << type << (c.isCatchUp() ? ",catchup":"") << ")";
}

void Connection::txStart() {
    txBuffer.reset(new broker::TxBuffer());
}

void Connection::txAccept(const framing::SequenceSet& acked) {
    txBuffer->enlist(boost::shared_ptr<broker::TxAccept>(
                         new broker::TxAccept(acked, semanticState().getUnacked())));
}

void Connection::txDequeue(const std::string& queue) {
    txBuffer->enlist(boost::shared_ptr<broker::RecoveredDequeue>(
                         new broker::RecoveredDequeue(findQueue(queue), getUpdateMessage().payload)));
}

void Connection::txEnqueue(const std::string& queue) {
    txBuffer->enlist(boost::shared_ptr<broker::RecoveredEnqueue>(
                         new broker::RecoveredEnqueue(findQueue(queue), getUpdateMessage().payload)));
}

void Connection::txPublish(const framing::Array& queues, bool delivered)
{
    boost::shared_ptr<broker::TxPublish> txPub(
        new broker::TxPublish(getUpdateMessage().payload));
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

void Connection::dtxStart(const std::string& xid,
                          bool ended,
                          bool suspended,
                          bool failed,
                          bool expired)
{
    dtxBuffer.reset(new broker::DtxBuffer(xid, ended, suspended, failed, expired));
    txBuffer = dtxBuffer;
}

void Connection::dtxEnd() {
    broker::DtxManager& mgr = cluster.getBroker().getDtxManager();
    std::string xid = dtxBuffer->getXid();
    if (mgr.exists(xid))
        mgr.join(xid, dtxBuffer);
    else
        mgr.start(xid, dtxBuffer);
    dtxBuffer.reset();
    txBuffer.reset();
}

// Sent after all DeliveryRecords for a dtx-ack have been collected in dtxAckRecords
void Connection::dtxAck() {
    dtxBuffer->enlist(
        boost::shared_ptr<broker::DtxAck>(new broker::DtxAck(dtxAckRecords)));
    dtxAckRecords.clear();
}

void Connection::dtxBufferRef(const std::string& xid, uint32_t index, bool suspended) {
    // Save the association between DtxBuffers and the session so we
    // can set the DtxBuffers at the end of the update when the
    // DtxManager has been replicated.
    updateIn.dtxBuffers.push_back(
        UpdateReceiver::DtxBufferRef(xid, index, suspended, &semanticState()));
}

// Sent at end of work record.
void Connection::dtxWorkRecord(const std::string& xid, bool prepared, uint32_t timeout)
{
    broker::DtxManager& mgr = cluster.getBroker().getDtxManager();
    if (timeout) mgr.setTimeout(xid, timeout);
    if (prepared) mgr.prepare(xid);
}


void Connection::exchange(const std::string& encoded) {
    Buffer buf(const_cast<char*>(encoded.data()), encoded.size());
    broker::Exchange::shared_ptr ex = broker::Exchange::decode(cluster.getBroker().getExchanges(), buf);
    if(ex.get() && ex->isDurable() && !ex->getName().find("amq.") == 0 && !ex->getName().find("qpid.") == 0) {
        cluster.getBroker().getStore().create(*(ex.get()), ex->getArgs());
    }
    QPID_LOG(debug, cluster << " updated exchange " << ex->getName());
}

void Connection::sessionError(uint16_t , const std::string& msg) {
    // Ignore errors before isOpen(), we're not multicasting yet.
    if (connection->isOpen())
        cluster.flagError(*this, ERROR_TYPE_SESSION, msg);
}

void Connection::connectionError(const std::string& msg) {
    // Ignore errors before isOpen(), we're not multicasting yet.
    if (connection->isOpen())
        cluster.flagError(*this, ERROR_TYPE_CONNECTION, msg);
}

void Connection::addQueueListener(const std::string& q, uint32_t listener) {
    if (listener >= updateIn.consumerNumbering.size())
        throw Exception(QPID_MSG("Invalid listener ID: " << listener));
    findQueue(q)->getListeners().addListener(updateIn.consumerNumbering[listener]);
}

//
// This is the handler for incoming managementsetup messages.
//
void Connection::managementSetupState(
    uint64_t objectNum, uint16_t bootSequence, const framing::Uuid& id,
    const std::string& vendor, const std::string& product, const std::string& instance)
{
    QPID_LOG(debug, cluster << " updated management: object number="
	     << objectNum << " boot sequence=" << bootSequence
             << " broker-id=" << id
             << " vendor=" << vendor
             << " product=" << product
             << " instance=" << instance);
    management::ManagementAgent* agent = cluster.getBroker().getManagementAgent();
    if (!agent)
        throw Exception(QPID_MSG("Management schema update but management not enabled."));
    agent->setNextObjectId(objectNum);
    agent->setBootSequence(bootSequence);
    agent->setUuid(id);
    agent->setName(vendor, product, instance);
}

void Connection::config(const std::string& encoded) {
    Buffer buf(const_cast<char*>(encoded.data()), encoded.size());
    string kind;
    buf.getShortString (kind);
    if (kind == "link") {
        broker::Link::shared_ptr link =
            broker::Link::decode(cluster.getBroker().getLinks(), buf);
        QPID_LOG(debug, cluster << " updated link "
                 << link->getHost() << ":" << link->getPort());
    }
    else if (kind == "bridge") {
        broker::Bridge::shared_ptr bridge =
            broker::Bridge::decode(cluster.getBroker().getLinks(), buf);
        QPID_LOG(debug, cluster << " updated bridge " << bridge->getName());
    }
    else throw Exception(QPID_MSG("Update failed, invalid kind of config: " << kind));
}

void Connection::doCatchupIoCallbacks() {
    // We need to process IO callbacks during the catch-up phase in
    // order to service asynchronous completions for messages
    // transferred during catch-up.

    if (catchUp) getBrokerConnection()->doIoCallbacks();
}

void Connection::clock(uint64_t time) {
    QPID_LOG(debug, "Cluster connection received time update");
    cluster.clock(time);
}

void Connection::queueDequeueSincePurgeState(const std::string& qname, uint32_t dequeueSincePurge) {
    boost::shared_ptr<broker::Queue> queue(findQueue(qname));
    queue->setDequeueSincePurge(dequeueSincePurge);
}

}} // Namespace qpid::cluster

