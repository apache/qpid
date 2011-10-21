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
#include "qpid/cluster/UpdateClient.h"
#include "qpid/cluster/Cluster.h"
#include "qpid/cluster/ClusterMap.h"
#include "qpid/cluster/Connection.h"
#include "qpid/cluster/Decoder.h"
#include "qpid/cluster/ExpiryPolicy.h"
#include "qpid/cluster/UpdateDataExchange.h"
#include "qpid/client/SessionBase_0_10Access.h"
#include "qpid/client/ConnectionAccess.h"
#include "qpid/client/SessionImpl.h"
#include "qpid/client/ConnectionImpl.h"
#include "qpid/client/Future.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Fairshare.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/LinkRegistry.h"
#include "qpid/broker/Bridge.h"
#include "qpid/broker/Link.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/ExchangeRegistry.h"
#include "qpid/broker/SessionHandler.h"
#include "qpid/broker/SessionState.h"
#include "qpid/broker/TxOpVisitor.h"
#include "qpid/broker/DtxAck.h"
#include "qpid/broker/DtxBuffer.h"
#include "qpid/broker/DtxWorkRecord.h"
#include "qpid/broker/TxAccept.h"
#include "qpid/broker/TxPublish.h"
#include "qpid/broker/RecoveredDequeue.h"
#include "qpid/broker/RecoveredEnqueue.h"
#include "qpid/broker/StatefulQueueObserver.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/framing/ClusterConnectionMembershipBody.h"
#include "qpid/framing/ClusterConnectionShadowReadyBody.h"
#include "qpid/framing/ClusterConnectionSessionStateBody.h"
#include "qpid/framing/ClusterConnectionConsumerStateBody.h"
#include "qpid/framing/enum.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/framing/TypeCode.h"
#include "qpid/log/Statement.h"
#include "qpid/types/Variant.h"
#include "qpid/Url.h"
#include "qmf/org/apache/qpid/broker/ManagementSetupState.h"
#include <boost/bind.hpp>
#include <boost/cast.hpp>
#include <algorithm>
#include <iterator>
#include <sstream>

namespace qpid {
namespace cluster {

using amqp_0_10::ListCodec;
using broker::Broker;
using broker::Exchange;
using broker::Queue;
using broker::QueueBinding;
using broker::Message;
using broker::SemanticState;
using types::Variant;

using namespace framing;
namespace arg=client::arg;
using client::SessionBase_0_10Access;

// Reserved exchange/queue name for catch-up, avoid clashes with user queues/exchanges.
const std::string UpdateClient::UPDATE("x-qpid.cluster-update");
// Name for header used to carry expiration information.
const std::string UpdateClient::X_QPID_EXPIRATION = "x-qpid.expiration";
// Headers used to flag headers/properties added by the UpdateClient so they can be
// removed on the other side.
const std::string UpdateClient::X_QPID_NO_MESSAGE_PROPS = "x-qpid.no-message-props";
const std::string UpdateClient::X_QPID_NO_HEADERS = "x-qpid.no-headers";

std::ostream& operator<<(std::ostream& o, const UpdateClient& c) {
    return o << "cluster(" << c.updaterId << " UPDATER)";
}

struct ClusterConnectionProxy : public AMQP_AllProxy::ClusterConnection, public framing::FrameHandler
{
    boost::shared_ptr<qpid::client::ConnectionImpl> connection;

    ClusterConnectionProxy(client::Connection c) :
        AMQP_AllProxy::ClusterConnection(*static_cast<framing::FrameHandler*>(this)),
        connection(client::ConnectionAccess::getImpl(c)) {}
    ClusterConnectionProxy(client::AsyncSession s) :
        AMQP_AllProxy::ClusterConnection(SessionBase_0_10Access(s).get()->out) {}

    void handle(framing::AMQFrame& f)
    {
        assert(connection);
        connection->expand(f.encodedSize(), false);
        connection->handle(f);
    }
};

// Create a connection with special version that marks it as a catch-up connection.
client::Connection UpdateClient::catchUpConnection() {
    client::Connection c;
    client::ConnectionAccess::setVersion(c, ProtocolVersion(0x80 , 0x80 + 10));
    return c;
}

// Send a control body directly to the session.
void send(client::AsyncSession& s, const AMQBody& body) {
    client::SessionBase_0_10Access sb(s);
    sb.get()->send(body);
}

// TODO aconway 2008-09-24: optimization: update connections/sessions in parallel.

UpdateClient::UpdateClient(const MemberId& updater, const MemberId& updatee, const Url& url,
                           broker::Broker& broker, const ClusterMap& m, ExpiryPolicy& expiry_,
                           const Cluster::ConnectionVector& cons, Decoder& decoder_,
                           const boost::function<void()>& ok,
                           const boost::function<void(const std::exception&)>& fail,
                           const client::ConnectionSettings& cs
)
    : updaterId(updater), updateeId(updatee), updateeUrl(url), updaterBroker(broker), map(m),
      expiry(expiry_), connections(cons), decoder(decoder_),
      connection(catchUpConnection()), shadowConnection(catchUpConnection()),
      done(ok), failed(fail), connectionSettings(cs)
{}

UpdateClient::~UpdateClient() {}

void UpdateClient::run() {
    try {
        connection.open(updateeUrl, connectionSettings);
        session = connection.newSession(UPDATE);
        session.sync();
        update();
        done();
    } catch (const std::exception& e) {
        failed(e);
    }
    delete this;
}

void UpdateClient::update() {
    QPID_LOG(debug, *this << " updating state to " << updateeId
             << " at " << updateeUrl);
    Broker& b = updaterBroker;

    if(b.getExpiryPolicy()) {
        QPID_LOG(debug, *this << "Updating updatee with cluster time");
        qpid::sys::AbsTime clusterTime = b.getExpiryPolicy()->getCurrentTime();
        int64_t time = qpid::sys::Duration(qpid::sys::EPOCH, clusterTime);
        ClusterConnectionProxy(session).clock(time);
    }

    updateManagementSetupState();

    b.getExchanges().eachExchange(boost::bind(&UpdateClient::updateExchange, this, _1));
    b.getQueues().eachQueue(boost::bind(&UpdateClient::updateNonExclusiveQueue, this, _1));

    // Update queue is used to transfer acquired messages that are no
    // longer on their original queue.
    session.queueDeclare(arg::queue=UPDATE, arg::autoDelete=true);
    session.sync();

    std::for_each(connections.begin(), connections.end(),
                  boost::bind(&UpdateClient::updateConnection, this, _1));

    // some Queue Observers need session state & msgs synced first, so sync observers now
    b.getQueues().eachQueue(boost::bind(&UpdateClient::updateQueueObservers, this, _1));

    // Update queue listeners: must come after sessions so consumerNumbering is populated
    b.getQueues().eachQueue(boost::bind(&UpdateClient::updateQueueListeners, this, _1));

    updateLinks();
    updateManagementAgent();
    updateDtxManager();
    session.queueDelete(arg::queue=UPDATE);

    session.close();

    ClusterConnectionMembershipBody membership;
    map.toMethodBody(membership);
    AMQFrame frame(membership);
    client::ConnectionAccess::getImpl(connection)->expand(frame.encodedSize(), false);
    client::ConnectionAccess::getImpl(connection)->handle(frame);

    // NOTE: connection will be closed from the other end, don't close
    // it here as that causes a race.

    // TODO aconway 2010-03-15: This sleep avoids the race condition
    // described in // https://bugzilla.redhat.com/show_bug.cgi?id=568831.
    // It allows the connection to fully close before destroying the
    // Connection object. Remove when the bug is fixed.
    //
    sys::usleep(10*1000);

    QPID_LOG(debug,  *this << " update completed to " << updateeId << " at " << updateeUrl);
}

namespace {
template <class T> std::string encode(const T& t) {
    std::string encoded;
    encoded.resize(t.encodedSize());
    framing::Buffer buf(const_cast<char*>(encoded.data()), encoded.size());
    t.encode(buf);
    return encoded;
}

template <class T> std::string encode(const T& t, bool encodeKind) {
    std::string encoded;
    encoded.resize(t.encodedSize());
    framing::Buffer buf(const_cast<char*>(encoded.data()), encoded.size());
    t.encode(buf, encodeKind);
    return encoded;
}
} // namespace


// Propagate the management state
void UpdateClient::updateManagementSetupState()
{
    management::ManagementAgent* agent = updaterBroker.getManagementAgent();
    if (!agent) return;

    QPID_LOG(debug, *this << " updating management setup-state.");
    std::string vendor, product, instance;
    agent->getName(vendor, product, instance);
    ClusterConnectionProxy(session).managementSetupState(
        agent->getNextObjectId(), agent->getBootSequence(), agent->getUuid(),
        vendor, product, instance);
}

void UpdateClient::updateManagementAgent()
{
    management::ManagementAgent* agent = updaterBroker.getManagementAgent();
    if (!agent) return;
    string data;

    QPID_LOG(debug, *this << " updating management schemas. ")
    agent->exportSchemas(data);
    session.messageTransfer(
        arg::content=client::Message(data, UpdateDataExchange::MANAGEMENT_SCHEMAS_KEY),
        arg::destination=UpdateDataExchange::EXCHANGE_NAME);

    QPID_LOG(debug, *this << " updating management agents. ")
    agent->exportAgents(data);
    session.messageTransfer(
        arg::content=client::Message(data, UpdateDataExchange::MANAGEMENT_AGENTS_KEY),
        arg::destination=UpdateDataExchange::EXCHANGE_NAME);

    QPID_LOG(debug, *this << " updating management deleted objects. ")
    typedef management::ManagementAgent::DeletedObjectList DeletedObjectList;
    DeletedObjectList deleted;
    agent->exportDeletedObjects(deleted);
    Variant::List list;
    for (DeletedObjectList::iterator i = deleted.begin(); i != deleted.end(); ++i) {
        string encoded;
        (*i)->encode(encoded);
        list.push_back(encoded);
    }
    ListCodec::encode(list, data);
    session.messageTransfer(
        arg::content=client::Message(data, UpdateDataExchange::MANAGEMENT_DELETED_OBJECTS_KEY),
        arg::destination=UpdateDataExchange::EXCHANGE_NAME);
}

void UpdateClient::updateExchange(const boost::shared_ptr<Exchange>& ex) {
    QPID_LOG(debug, *this << " updating exchange " << ex->getName());
    ClusterConnectionProxy(session).exchange(encode(*ex));
}

/** Bind a queue to the update exchange and update messges to it
 * setting the message possition as needed.
 */
class MessageUpdater {
    std::string queue;
    bool haveLastPos;
    framing::SequenceNumber lastPos;
    client::AsyncSession session;
    ExpiryPolicy& expiry;

  public:

    MessageUpdater(const string& q, const client::AsyncSession s, ExpiryPolicy& expiry_) : queue(q), haveLastPos(false), session(s), expiry(expiry_) {
        session.exchangeBind(queue, UpdateClient::UPDATE);
    }

    ~MessageUpdater() {
        try {
            session.exchangeUnbind(queue, UpdateClient::UPDATE);
        }
        catch (const std::exception& e) {
            // Don't throw in a destructor.
            QPID_LOG(error, "Unbinding update queue " << queue << ": " << e.what());
        }
    }

    void updateQueuedMessage(const broker::QueuedMessage& message) {
        // Send the queue position if necessary.
        if (!haveLastPos || message.position - lastPos != 1)  {
            ClusterConnectionProxy(session).queuePosition(queue, message.position.getValue()-1);
            haveLastPos = true;
        }
        lastPos = message.position;

        // if the ttl > 0, we need to send the calculated expiration time to the updatee
        const DeliveryProperties* dprops =
            message.payload->getProperties<DeliveryProperties>();
        if (dprops && dprops->getTtl() > 0) {
            bool hadMessageProps =
                message.payload->hasProperties<framing::MessageProperties>();
            const framing::MessageProperties* mprops =
                message.payload->getProperties<framing::MessageProperties>();
            bool hadApplicationHeaders = mprops->hasApplicationHeaders();
            message.payload->insertCustomProperty(UpdateClient::X_QPID_EXPIRATION,
                            sys::Duration(sys::EPOCH, message.payload->getExpiration()));
            // If message properties or application headers didn't exist
            // prior to us adding data, we want to remove them on the other side.
            if (!hadMessageProps)
                message.payload->insertCustomProperty(UpdateClient::X_QPID_NO_MESSAGE_PROPS, 0);
            else if (!hadApplicationHeaders)
                message.payload->insertCustomProperty(UpdateClient::X_QPID_NO_HEADERS, 0);
        }

        // We can't send a broker::Message via the normal client API,
        // and it would be expensive to copy it into a client::Message
        // so we go a bit under the client API covers here.
        //
        SessionBase_0_10Access sb(session);
        // Disable client code that clears the delivery-properties.exchange
        sb.get()->setDoClearDeliveryPropertiesExchange(false);
        framing::MessageTransferBody transfer(
            *message.payload->getFrames().as<framing::MessageTransferBody>());
        transfer.setDestination(UpdateClient::UPDATE);

        sb.get()->send(transfer, message.payload->getFrames(),
                       !message.payload->isContentReleased());
        if (message.payload->isContentReleased()){
            uint16_t maxFrameSize = sb.get()->getConnection()->getNegotiatedSettings().maxFrameSize;
            uint16_t maxContentSize = maxFrameSize - AMQFrame::frameOverhead();
            bool morecontent = true;
            for (uint64_t offset = 0; morecontent; offset += maxContentSize)
            {
                AMQFrame frame((AMQContentBody()));
                morecontent = message.payload->getContentFrame(
                    *(message.queue), frame, maxContentSize, offset);
                sb.get()->sendRawFrame(frame);
            }
        }
    }

    void updateMessage(const boost::intrusive_ptr<broker::Message>& message) {
        updateQueuedMessage(broker::QueuedMessage(0, message, haveLastPos? lastPos.getValue()+1 : 1));
    }
};

void UpdateClient::updateQueue(client::AsyncSession& s, const boost::shared_ptr<Queue>& q) {
    broker::Exchange::shared_ptr alternateExchange = q->getAlternateExchange();
    s.queueDeclare(
        arg::queue = q->getName(),
        arg::durable = q->isDurable(),
        arg::autoDelete = q->isAutoDelete(),
        arg::alternateExchange = alternateExchange ? alternateExchange->getName() : "",
        arg::arguments = q->getSettings(),
        arg::exclusive = q->hasExclusiveOwner()
    );
    MessageUpdater updater(q->getName(), s, expiry);
    q->eachMessage(boost::bind(&MessageUpdater::updateQueuedMessage, &updater, _1));
    q->eachBinding(boost::bind(&UpdateClient::updateBinding, this, s, q->getName(), _1));
    ClusterConnectionProxy(s).queuePosition(q->getName(), q->getPosition());
    uint priority, count;
    if (qpid::broker::Fairshare::getState(q->getMessages(), priority, count)) {
        ClusterConnectionProxy(s).queueFairshareState(q->getName(), priority, count);
    }

    ClusterConnectionProxy(s).queueDequeueSincePurgeState(q->getName(), q->getDequeueSincePurge());
}

void UpdateClient::updateExclusiveQueue(const boost::shared_ptr<broker::Queue>& q) {
    QPID_LOG(debug, *this << " updating exclusive queue " << q->getName() << " on " << shadowSession.getId());
    updateQueue(shadowSession, q);
}

void UpdateClient::updateNonExclusiveQueue(const boost::shared_ptr<broker::Queue>& q) {
    if (!q->hasExclusiveOwner()) {
        QPID_LOG(debug, *this << " updating queue " << q->getName());
        updateQueue(session, q);
    }//else queue will be updated as part of session state of owning session
}

void UpdateClient::updateBinding(client::AsyncSession& s, const std::string& queue, const QueueBinding& binding) {
    if (binding.exchange.size())
        s.exchangeBind(queue, binding.exchange, binding.key, binding.args);
    //else its the default exchange and there is no need to replicate
    //the binding, the creation of the queue will have done so
    //automatically
}

void UpdateClient::updateOutputTask(const sys::OutputTask* task) {
    const SemanticState::ConsumerImpl* cci =
        boost::polymorphic_downcast<const SemanticState::ConsumerImpl*> (task);
    SemanticState::ConsumerImpl* ci = const_cast<SemanticState::ConsumerImpl*>(cci);
    uint16_t channel =  ci->getParent().getSession().getChannel();
    ClusterConnectionProxy(shadowConnection).outputTask(channel,  ci->getTag());
    QPID_LOG(debug, *this << " updating output task " << ci->getTag()
             << " channel=" << channel);
}

void UpdateClient::updateConnection(const boost::intrusive_ptr<Connection>& updateConnection) {
    QPID_LOG(debug, *this << " updating connection " << *updateConnection);
    assert(updateConnection->getBrokerConnection());
    broker::Connection& bc = *updateConnection->getBrokerConnection();

    // Send the management ID first on the main connection.
    std::string mgmtId = updateConnection->getBrokerConnection()->getMgmtId();
    ClusterConnectionProxy(session).shadowPrepare(mgmtId);
    // Make sure its received before opening shadow connection
    session.sync();

    // Open shadow connection and update it.
    shadowConnection = catchUpConnection();

    connectionSettings.maxFrameSize = bc.getFrameMax();
    shadowConnection.open(updateeUrl, connectionSettings);
    ClusterConnectionProxy(shadowConnection).shadowSetUser(bc.getUserId());

    bc.eachSessionHandler(boost::bind(&UpdateClient::updateSession, this, _1));
    // Safe to use decoder here because we are stalled for update.
    std::pair<const char*, size_t> fragment = decoder.get(updateConnection->getId()).getFragment();
    bc.getOutputTasks().eachOutput(
        boost::bind(&UpdateClient::updateOutputTask, this, _1));
    ClusterConnectionProxy(shadowConnection).shadowReady(
        updateConnection->getId().getMember(),
        updateConnection->getId().getNumber(),
        bc.getMgmtId(),
        bc.getUserId(),
        string(fragment.first, fragment.second),
        updateConnection->getOutput().getSendMax()
    );
    shadowConnection.close();
    QPID_LOG(debug, *this << " updated connection " << *updateConnection);
}

void UpdateClient::updateSession(broker::SessionHandler& sh) {
    broker::SessionState* ss = sh.getSession();
    if (!ss) return;            // no session.

    QPID_LOG(debug, *this << " updating session " << ss->getId());

    // Create a client session to update session state.
    boost::shared_ptr<client::ConnectionImpl> cimpl = client::ConnectionAccess::getImpl(shadowConnection);
    boost::shared_ptr<client::SessionImpl> simpl = cimpl->newSession(ss->getId().getName(), ss->getTimeout(), sh.getChannel());
    simpl->disableAutoDetach();
    client::SessionBase_0_10Access(shadowSession).set(simpl);
    AMQP_AllProxy::ClusterConnection proxy(simpl->out);

    // Re-create session state on remote connection.

    QPID_LOG(debug, *this << " updating exclusive queues.");
    ss->getSessionAdapter().eachExclusiveQueue(boost::bind(&UpdateClient::updateExclusiveQueue, this, _1));

    QPID_LOG(debug, *this << " updating consumers.");
    ss->getSemanticState().eachConsumer(
        boost::bind(&UpdateClient::updateConsumer, this, _1));

    QPID_LOG(debug, *this << " updating unacknowledged messages.");
    broker::DeliveryRecords& drs = ss->getSemanticState().getUnacked();
    std::for_each(drs.begin(), drs.end(),
                  boost::bind(&UpdateClient::updateUnacked, this, _1, shadowSession));

    updateTransactionState(ss->getSemanticState());

    // Adjust command counter for message in progress, will be sent after state update.
    boost::intrusive_ptr<Message> inProgress = ss->getMessageInProgress();
    SequenceNumber received = ss->receiverGetReceived().command;
    if (inProgress)
        --received;

    // Sync the session to ensure all responses from broker have been processed.
    shadowSession.sync();

    // Reset command-sequence state.
    proxy.sessionState(
        ss->senderGetReplayPoint().command,
        ss->senderGetCommandPoint().command,
        ss->senderGetIncomplete(),
        std::max(received, ss->receiverGetExpected().command),
        received,
        ss->receiverGetUnknownComplete(),
        ss->receiverGetIncomplete(),
        ss->getSemanticState().getDtxSelected()
    );

    // Send frames for partial message in progress.
    if (inProgress) {
        inProgress->getFrames().map(simpl->out);
    }
    QPID_LOG(debug, *this << " updated session " << sh.getSession()->getId());
}

void UpdateClient::updateConsumer(
    const broker::SemanticState::ConsumerImpl::shared_ptr& ci)
{
    QPID_LOG(debug, *this << " updating consumer " << ci->getTag() << " on "
             << shadowSession.getId());

    using namespace message;
    shadowSession.messageSubscribe(
        arg::queue       = ci->getQueue()->getName(),
        arg::destination = ci->getTag(),
        arg::acceptMode  = ci->isAckExpected() ? ACCEPT_MODE_EXPLICIT : ACCEPT_MODE_NONE,
        arg::acquireMode = ci->isAcquire() ? ACQUIRE_MODE_PRE_ACQUIRED : ACQUIRE_MODE_NOT_ACQUIRED,
        arg::exclusive   = ci->isExclusive(),
        arg::resumeId    = ci->getResumeId(),
        arg::resumeTtl   = ci->getResumeTtl(),
        arg::arguments   = ci->getArguments()
    );
    shadowSession.messageSetFlowMode(ci->getTag(), ci->isWindowing() ? FLOW_MODE_WINDOW : FLOW_MODE_CREDIT);
    shadowSession.messageFlow(ci->getTag(), CREDIT_UNIT_MESSAGE, ci->getMsgCredit());
    shadowSession.messageFlow(ci->getTag(), CREDIT_UNIT_BYTE, ci->getByteCredit());
    ClusterConnectionProxy(shadowSession).consumerState(
        ci->getTag(),
        ci->isBlocked(),
        ci->isNotifyEnabled(),
        ci->position
    );
    consumerNumbering.add(ci.get());

    QPID_LOG(debug, *this << " updated consumer " << ci->getTag()
             << " on " << shadowSession.getId());
}

void UpdateClient::updateUnacked(const broker::DeliveryRecord& dr,
                                 client::AsyncSession& updateSession)
{
    if (!dr.isEnded() && dr.isAcquired()) {
        assert(dr.getMessage().payload);
        // If the message is acquired then it is no longer on the
        // updatees queue, put it on the update queue for updatee to pick up.
        //
        MessageUpdater(UPDATE, updateSession, expiry).updateQueuedMessage(dr.getMessage());
    }
    ClusterConnectionProxy(updateSession).deliveryRecord(
        dr.getQueue()->getName(),
        dr.getMessage().position,
        dr.getTag(),
        dr.getId(),
        dr.isAcquired(),
        dr.isAccepted(),
        dr.isCancelled(),
        dr.isComplete(),
        dr.isEnded(),
        dr.isWindowing(),
        dr.getQueue()->isEnqueued(dr.getMessage()),
        dr.getCredit()
    );
}

class TxOpUpdater : public broker::TxOpConstVisitor, public MessageUpdater {
  public:
    TxOpUpdater(UpdateClient& dc, client::AsyncSession s, ExpiryPolicy& expiry)
        : MessageUpdater(UpdateClient::UPDATE, s, expiry), parent(dc), session(s), proxy(s) {}

    void operator()(const broker::DtxAck& ack) {
        std::for_each(ack.getPending().begin(), ack.getPending().end(),
                      boost::bind(&UpdateClient::updateUnacked, &parent, _1, session));
        proxy.dtxAck();
    }

    void operator()(const broker::RecoveredDequeue& rdeq) {
        updateMessage(rdeq.getMessage());
        proxy.txEnqueue(rdeq.getQueue()->getName());
    }

    void operator()(const broker::RecoveredEnqueue& renq) {
        updateMessage(renq.getMessage());
        proxy.txEnqueue(renq.getQueue()->getName());
    }

    void operator()(const broker::TxAccept& txAccept) {
        proxy.txAccept(txAccept.getAcked());
    }

    typedef std::list<Queue::shared_ptr> QueueList;

    void copy(const QueueList& l, Array& a) {
        for (QueueList::const_iterator i = l.begin(); i!=l.end(); ++i)
            a.push_back(Array::ValuePtr(new Str8Value((*i)->getName())));
    }

    void operator()(const broker::TxPublish& txPub) {
        updateMessage(txPub.getMessage());
        assert(txPub.getQueues().empty() || txPub.getPrepared().empty());
        Array qarray(TYPE_CODE_STR8);
        copy(txPub.getQueues().empty() ? txPub.getPrepared() : txPub.getQueues(), qarray);
        proxy.txPublish(qarray, txPub.delivered);
    }

  private:
    UpdateClient& parent;
    client::AsyncSession session;
    ClusterConnectionProxy proxy;
};

void UpdateClient::updateBufferRef(const broker::DtxBuffer::shared_ptr& dtx,bool suspended)
{
    ClusterConnectionProxy proxy(shadowSession);
    broker::DtxWorkRecord* record =
        updaterBroker.getDtxManager().getWork(dtx->getXid());
    proxy.dtxBufferRef(dtx->getXid(), record->indexOf(dtx), suspended);

}

void UpdateClient::updateTransactionState(broker::SemanticState& s) {
    ClusterConnectionProxy proxy(shadowSession);
    proxy.accumulatedAck(s.getAccumulatedAck());
    broker::TxBuffer::shared_ptr tx = s.getTxBuffer();
    broker::DtxBuffer::shared_ptr dtx = s.getDtxBuffer();
    if (dtx) {
        updateBufferRef(dtx, false); // Current transaction.
    } else if (tx) {
        proxy.txStart();
        TxOpUpdater updater(*this, shadowSession, expiry);
        tx->accept(updater);
        proxy.txEnd();
    }
    for (SemanticState::DtxBufferMap::iterator i = s.getSuspendedXids().begin();
         i != s.getSuspendedXids().end();
         ++i)
    {
        updateBufferRef(i->second, true);
    }
}

void UpdateClient::updateDtxBuffer(const broker::DtxBuffer::shared_ptr& dtx) {
    ClusterConnectionProxy proxy(session);
    proxy.dtxStart(
        dtx->getXid(), dtx->isEnded(), dtx->isSuspended(), dtx->isFailed(), dtx->isExpired());
    TxOpUpdater updater(*this, session, expiry);
    dtx->accept(updater);
    proxy.dtxEnd();
}

void UpdateClient::updateQueueListeners(const boost::shared_ptr<broker::Queue>& queue) {
    queue->getListeners().eachListener(
        boost::bind(&UpdateClient::updateQueueListener, this, queue->getName(), _1));
}

void UpdateClient::updateQueueListener(std::string& q,
                                       const boost::shared_ptr<broker::Consumer>& c)
{
    SemanticState::ConsumerImpl* ci = dynamic_cast<SemanticState::ConsumerImpl*>(c.get());
    size_t n = consumerNumbering[ci];
    if (n >= consumerNumbering.size())
        throw Exception(QPID_MSG("Unexpected listener on queue " << q));
    ClusterConnectionProxy(session).addQueueListener(q, n);
}

void UpdateClient::updateLinks() {
    broker::LinkRegistry& links = updaterBroker.getLinks();
    links.eachLink(boost::bind(&UpdateClient::updateLink, this, _1));
    links.eachBridge(boost::bind(&UpdateClient::updateBridge, this, _1));
}

void UpdateClient::updateLink(const boost::shared_ptr<broker::Link>& link) {
    QPID_LOG(debug, *this << " updating link "
             << link->getHost() << ":" << link->getPort());
    ClusterConnectionProxy(session).config(encode(*link));
}

void UpdateClient::updateBridge(const boost::shared_ptr<broker::Bridge>& bridge) {
    QPID_LOG(debug, *this << " updating bridge " << bridge->getName());
    ClusterConnectionProxy(session).config(encode(*bridge));
}

void UpdateClient::updateQueueObservers(const boost::shared_ptr<broker::Queue>& q)
{
    q->eachObserver(boost::bind(&UpdateClient::updateObserver, this, q, _1));
}

void UpdateClient::updateObserver(const boost::shared_ptr<broker::Queue>& q,
                                        boost::shared_ptr<broker::QueueObserver> o)
{
    qpid::framing::FieldTable state;
    broker::StatefulQueueObserver *so = dynamic_cast<broker::StatefulQueueObserver *>(o.get());
    if (so) {
        so->getState( state );
        std::string id(so->getId());
        QPID_LOG(debug, *this << " updating queue " << q->getName() << "'s observer " << id);
        ClusterConnectionProxy(session).queueObserverState( q->getName(), id, state );
    }
}

void UpdateClient::updateDtxManager() {
    broker::DtxManager& dtm = updaterBroker.getDtxManager();
    dtm.each(boost::bind(&UpdateClient::updateDtxWorkRecord, this, _1));
}

void UpdateClient::updateDtxWorkRecord(const broker::DtxWorkRecord& r) {
    QPID_LOG(debug, *this << " updating DTX transaction: " << r.getXid());
    for (size_t i = 0; i < r.size(); ++i)
        updateDtxBuffer(r[i]);
    ClusterConnectionProxy(session).dtxWorkRecord(
        r.getXid(), r.isPrepared(), r.getTimeout());
}

}} // namespace qpid::cluster
