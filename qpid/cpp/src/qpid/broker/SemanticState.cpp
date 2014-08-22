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

#include "qpid/broker/SessionState.h"

#include "qpid/broker/AsyncCommandCallback.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/amqp_0_10/Connection.h"
#include "qpid/broker/DeliverableMessage.h"
#include "qpid/broker/DtxAck.h"
#include "qpid/broker/DtxTimeout.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/Selector.h"
#include "qpid/broker/SessionContext.h"
#include "qpid/broker/SessionOutputException.h"
#include "qpid/broker/TransactionObserver.h"
#include "qpid/broker/TxAccept.h"
#include "qpid/broker/amqp_0_10/MessageTransfer.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/framing/SequenceSet.h"
#include "qpid/framing/IsInSequenceSet.h"
#include "qpid/log/Statement.h"
#include "qpid/management/ManagementAgent.h"
#include "qpid/ptr_map.h"
#include "qpid/broker/AclModule.h"
#include "qpid/broker/FedOps.h"
#include "qpid/sys/ConnectionOutputHandler.h"

#include <boost/bind.hpp>
#include <boost/format.hpp>
#include <boost/tuple/tuple_comparison.hpp>

#include <iostream>
#include <sstream>
#include <algorithm>
#include <functional>

#include <assert.h>

namespace {
const std::string X_SCOPE("x-scope");
const std::string SESSION("session");
}

namespace qpid {
namespace broker {

using namespace std;
using boost::intrusive_ptr;
using boost::shared_ptr;
using boost::bind;
using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;
using qpid::ptr_map_ptr;
using qpid::management::ManagementAgent;
using qpid::management::ManagementObject;
using qpid::management::Manageable;
using qpid::management::Args;
namespace _qmf = qmf::org::apache::qpid::broker;

SemanticState::SemanticState(SessionState& ss)
    : session(ss),
      tagGenerator("sgen"),
      dtxSelected(false),
      authMsg(getSession().getBroker().isAuthenticating() && !getSession().getConnection().isFederationLink()),
      userID(getSession().getConnection().getUserId()),
      closeComplete(false),
      connectionId(getSession().getConnection().getMgmtId())
{}

SemanticState::~SemanticState() {
    closed();
}

void SemanticState::closed() {
    if (!closeComplete) {
        //prevent requeued messages being redelivered to consumers
        for (ConsumerImplMap::iterator i = consumers.begin(); i != consumers.end(); i++) {
            disable(i->second);
        }
        if (dtxBuffer.get()) {
            dtxBuffer->fail();
        }
        unbindSessionBindings();
        requeue();

        //now unsubscribe, which may trigger queue deletion and thus
        //needs to occur after the requeueing of unacked messages
        for (ConsumerImplMap::iterator i = consumers.begin(); i != consumers.end(); i++) {
            cancel(i->second);
        }
        closeComplete = true;
        if (txBuffer) txBuffer->rollback();
    }
}

bool SemanticState::exists(const string& consumerTag){
    return consumers.find(consumerTag) != consumers.end();
}

namespace {
    const std::string SEPARATOR("::");
}

void SemanticState::consume(const string& tag,
                            Queue::shared_ptr queue, bool ackRequired, bool acquire,
                            bool exclusive, const string& resumeId, uint64_t resumeTtl,
                            const FieldTable& arguments)
{
    // "tag" is only guaranteed to be unique to this session (see AMQP 0-10 Message.subscribe, destination).
    // Create a globally unique name so the broker can identify individual consumers
    std::string name = session.getSessionId().str() + SEPARATOR + tag;
    const ConsumerFactories::Factories& cf(
        session.getBroker().getConsumerFactories().get());
    ConsumerImpl::shared_ptr c;
    for (ConsumerFactories::Factories::const_iterator i = cf.begin(); i != cf.end() && !c; ++i)
        c = (*i)->create(this, name, queue, ackRequired, acquire, exclusive, tag,
                         resumeId, resumeTtl, arguments);
    if (!c)                     // Create plain consumer
        c = ConsumerImpl::shared_ptr(
            new ConsumerImpl(this, name, queue, ackRequired, acquire ? CONSUMER : BROWSER, exclusive, tag,
                             resumeId, resumeTtl, arguments));
    queue->consume(c, exclusive, arguments, connectionId, userID);//may throw exception
    consumers[tag] = c;
}

bool SemanticState::cancel(const string& tag)
{
    ConsumerImplMap::iterator i = consumers.find(tag);
    if (i != consumers.end()) {
        cancel(i->second);
        consumers.erase(i);
        //should cancel all unacked messages for this consumer so that
        //they are not redelivered on recovery
        for_each(unacked.begin(), unacked.end(), boost::bind(&DeliveryRecord::cancel, _1, tag));
        //can also remove any records that are now redundant
        DeliveryRecords::iterator removed =
            remove_if(unacked.begin(), unacked.end(), bind(&DeliveryRecord::isRedundant, _1));
        unacked.erase(removed, unacked.end());
        getSession().setUnackedCount(unacked.size());
        return true;
    } else {
        return false;
    }
}


void SemanticState::startTx()
{
    accumulatedAck.clear();
    txBuffer = boost::intrusive_ptr<TxBuffer>(new TxBuffer());
    session.getBroker().getBrokerObservers().startTx(txBuffer);
    session.startTx(); //just to update statistics
}

namespace {
struct StartTxOnExit {
    SemanticState& session;
    StartTxOnExit(SemanticState& ss) : session(ss) {}
    ~StartTxOnExit() { session.startTx(); }
};
} // namespace

void SemanticState::commit(MessageStore* const store)
{
    if (!txBuffer) throw
        CommandInvalidException(
            QPID_MSG("Session has not been selected for use with transactions"));
    // Start a new TX regardless of outcome of this one.
    StartTxOnExit e(*this);
    session.getCurrentCommand().setCompleteSync(false); // Async completion
    txBuffer->begin();          // Begin async completion.
    session.commitTx();         //just to update statistics
    TxOp::shared_ptr txAck(static_cast<TxOp*>(new TxAccept(accumulatedAck, unacked)));
    txBuffer->enlist(txAck);
    // In a HA cluster, tx.commit may complete asynchronously.
    txBuffer->startCommit(store);
    AsyncCommandCallback callback(
        session,
        boost::bind(&TxBuffer::endCommit, txBuffer, store),
        true                    // This is a sync point
    );
    txBuffer->end(callback);
}

void SemanticState::rollback()
{
    if (!txBuffer)
        throw CommandInvalidException(QPID_MSG("Session has not been selected for use with transactions"));
    StartTxOnExit e(*this);
    session.rollbackTx();       // Just to update statistics
    txBuffer->rollback();
}

void SemanticState::selectDtx()
{
    dtxSelected = true;
}

void SemanticState::startDtx(const std::string& xid, DtxManager& mgr, bool join)
{
    if (!dtxSelected) {
        throw CommandInvalidException(QPID_MSG("Session has not been selected for use with dtx"));
    }
    dtxBuffer = new DtxBuffer(xid);
    txBuffer = dtxBuffer;
    session.getBroker().getBrokerObservers().startDtx(dtxBuffer);
    if (join) {
        mgr.join(xid, dtxBuffer);
    } else {
        mgr.start(xid, dtxBuffer);
    }
}

void SemanticState::endDtx(const std::string& xid, bool fail)
{
    if (!dtxBuffer) {
        throw IllegalStateException(QPID_MSG("xid " << xid << " not associated with this session"));
    }
    if (dtxBuffer->getXid() != xid) {
        throw CommandInvalidException(
            QPID_MSG("xid specified on start was " << dtxBuffer->getXid() << ", but " << xid << " specified on end"));

    }

    txBuffer = 0;//ops on this session no longer transactional

    checkDtxTimeout();
    if (fail) {
        dtxBuffer->fail();
    } else {
        dtxBuffer->markEnded();
    }
    dtxBuffer = 0;
}

void SemanticState::suspendDtx(const std::string& xid)
{
    if (dtxBuffer->getXid() != xid) {
        throw CommandInvalidException(
            QPID_MSG("xid specified on start was " << dtxBuffer->getXid() << ", but " << xid << " specified on suspend"));
    }
    txBuffer = 0;//ops on this session no longer transactional

    checkDtxTimeout();
    dtxBuffer->setSuspended(true);
    suspendedXids[xid] = dtxBuffer;
    dtxBuffer = 0;
}

void SemanticState::resumeDtx(const std::string& xid)
{
    if (!dtxSelected) {
        throw CommandInvalidException(QPID_MSG("Session has not been selected for use with dtx"));
    }

    dtxBuffer = suspendedXids[xid];
    if (!dtxBuffer) {
        throw CommandInvalidException(QPID_MSG("xid " << xid << " not attached"));
    } else {
        suspendedXids.erase(xid);
    }

    if (dtxBuffer->getXid() != xid) {
        throw CommandInvalidException(
            QPID_MSG("xid specified on start was " << dtxBuffer->getXid() << ", but " << xid << " specified on resume"));

    }
    if (!dtxBuffer->isSuspended()) {
        throw CommandInvalidException(QPID_MSG("xid " << xid << " not suspended"));
    }

    checkDtxTimeout();
    dtxBuffer->setSuspended(false);
    txBuffer = dtxBuffer;
}

void SemanticState::checkDtxTimeout()
{
    if (dtxBuffer->isExpired()) {
        dtxBuffer = 0;
        throw DtxTimeoutException();
    }
}

void SemanticState::record(const DeliveryRecord& delivery)
{
    unacked.push_back(delivery);
    getSession().setUnackedCount(unacked.size());
}

const std::string QPID_SYNC_FREQUENCY("qpid.sync_frequency");
const std::string APACHE_SELECTOR("x-apache-selector");

SemanticStateConsumerImpl::SemanticStateConsumerImpl(SemanticState* _parent,
                                          const string& _name,
                                          Queue::shared_ptr _queue,
                                          bool ack,
                                          SubscriptionType type,
                                          bool _exclusive,
                                          const string& _tag,
                                          const string& _resumeId,
                                          uint64_t _resumeTtl,
                                          const framing::FieldTable& _arguments

) :
    Consumer(_name, type, _tag),
    parent(_parent),
    queue(_queue),
    ackExpected(ack),
    acquire(type == CONSUMER),
    blocked(true),
    exclusive(_exclusive),
    resumeId(_resumeId),
    selector(returnSelector(_arguments.getAsString(APACHE_SELECTOR))),
    resumeTtl(_resumeTtl),
    arguments(_arguments),
    notifyEnabled(true),
    syncFrequency(_arguments.getAsInt(QPID_SYNC_FREQUENCY)),
    deliveryCount(0),
    protocols(parent->getSession().getBroker().getProtocolRegistry())
{
    if (parent != 0 && queue.get() != 0 && queue->GetManagementObject() !=0)
    {
        ManagementAgent* agent = parent->session.getBroker().getManagementAgent();
        qpid::management::Manageable* ms = dynamic_cast<qpid::management::Manageable*> (&(parent->session));

        if (agent != 0)
        {
            mgmtObject = _qmf::Subscription::shared_ptr(new _qmf::Subscription(agent, this, ms , queue->GetManagementObject()->getObjectId(), getTag(),
                                                                               !acquire, ackExpected, exclusive, ManagementAgent::toMap(arguments)));
            agent->addObject (mgmtObject);
            mgmtObject->set_creditMode("WINDOW");
        }
    }
}

ManagementObject::shared_ptr SemanticStateConsumerImpl::GetManagementObject (void) const
{
    return mgmtObject;
}

Manageable::status_t SemanticStateConsumerImpl::ManagementMethod (uint32_t methodId, Args&, string&)
{
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;

    QPID_LOG (debug, "Queue::ManagementMethod [id=" << methodId << "]");

    return status;
}


OwnershipToken* SemanticStateConsumerImpl::getSession()
{
    return &(parent->session);
}

bool SemanticStateConsumerImpl::deliver(const QueueCursor& cursor, const Message& msg)
{
    return deliver(cursor, msg, shared_from_this());
}
bool SemanticStateConsumerImpl::deliver(const QueueCursor& cursor, const Message& msg, boost::shared_ptr<Consumer> consumer)
{
    allocateCredit(msg);
    boost::intrusive_ptr<const amqp_0_10::MessageTransfer> transfer = protocols.translate(msg);
    DeliveryRecord record(cursor, msg.getSequence(), msg.getReplicationId(), queue, getTag(),
                          consumer, acquire, !ackExpected, credit.isWindowMode(), transfer->getRequiredCredit());
    bool sync = syncFrequency && ++deliveryCount >= syncFrequency;
    if (sync) deliveryCount = 0;//reset

    record.setId(parent->session.deliver(*transfer, getTag(), msg.isRedelivered(), msg.getTtl(),
                                         ackExpected ? message::ACCEPT_MODE_EXPLICIT : message::ACCEPT_MODE_NONE,
                                         acquire ? message::ACQUIRE_MODE_PRE_ACQUIRED : message::ACQUIRE_MODE_NOT_ACQUIRED,
                                         msg.getAnnotations(),
                                         sync));
    if (credit.isWindowMode() || ackExpected || !acquire) {
        parent->record(record);
    }
    if (acquire && !ackExpected) {  // auto acquire && auto accept
        queue->dequeue(0 /*ctxt*/, cursor);
    }
    if (mgmtObject) { mgmtObject->inc_delivered(); }
    return true;
}

bool SemanticStateConsumerImpl::filter(const Message& msg)
{
    return !selector || selector->filter(msg);
}

bool SemanticStateConsumerImpl::accept(const Message& msg)
{
    // TODO aconway 2009-06-08: if we have byte & message credit but
    // checkCredit fails because the message is to big, we should
    // remain on queue's listener list for possible smaller messages
    // in future.
    //
    blocked = !checkCredit(msg);
    return !blocked;
}

namespace {
struct ConsumerName {
    const SemanticStateConsumerImpl& consumer;
    ConsumerName(const SemanticStateConsumerImpl& ci) : consumer(ci) {}
};

ostream& operator<<(ostream& o, const ConsumerName& pc) {
    return o << pc.consumer.getTag() << " on "
             << pc.consumer.getParent().getSession().getSessionId();
}
}

void SemanticStateConsumerImpl::allocateCredit(const Message& msg)
{
    Credit original = credit;
    boost::intrusive_ptr<const amqp_0_10::MessageTransfer> transfer = protocols.translate(msg);
    credit.consume(1, transfer->getRequiredCredit());
    QPID_LOG(debug, "Credit allocated for " << ConsumerName(*this)
             << ", was " << original << " now " << credit);

}

bool SemanticStateConsumerImpl::checkCredit(const Message& msg)
{
    boost::intrusive_ptr<const amqp_0_10::MessageTransfer> transfer = protocols.translate(msg);
    bool enoughCredit = credit.check(1, transfer->getRequiredCredit());
    QPID_LOG(debug, "Subscription " << ConsumerName(*this) << " has " << (enoughCredit ? "sufficient " : "insufficient")
             <<  " credit for message of " << transfer->getRequiredCredit() << " bytes: "
             << credit);
    return enoughCredit;
}

SemanticStateConsumerImpl::~SemanticStateConsumerImpl()
{
    if (mgmtObject != 0) {
        mgmtObject->debugStats("destroying");
        mgmtObject->resourceDestroy ();
    }
}

void SemanticState::disable(ConsumerImpl::shared_ptr c)
{
    c->disableNotify();
    if (session.isAttached())
        session.getConnection().removeOutputTask(c.get());
}

void SemanticState::cancel(ConsumerImpl::shared_ptr c)
{
    disable(c);
    Queue::shared_ptr queue = c->getQueue();
    if(queue) {
        queue->cancel(c, connectionId, userID);
    }
    c->cancel();
}

TxBuffer* SemanticState::getTxBuffer()
{
    return txBuffer.get();
}

void SemanticState::route(Message& msg, Deliverable& strategy) {
    std::string exchangeName = qpid::broker::amqp_0_10::MessageTransfer::get(msg).getExchangeName();
    if (!cacheExchange || cacheExchange->getName() != exchangeName || cacheExchange->isDestroyed())
        cacheExchange = session.getBroker().getExchanges().get(exchangeName);

    /* verify the userid if specified: */
    std::string id = msg.getUserId();
    if (authMsg &&  !id.empty() && !session.getConnection().isAuthenticatedUser(id))
    {
        QPID_LOG(debug, "authorised user id : " << userID << " but user id in message declared as " << id);
        throw UnauthorizedAccessException(QPID_MSG("authorised user id : " << userID << " but user id in message declared as " << id));
    }

    AclModule* acl = getSession().getBroker().getAcl();
    if (acl && acl->doTransferAcl())
    {
        if (!acl->authorise(getSession().getConnection().getUserId(),acl::ACT_PUBLISH,acl::OBJ_EXCHANGE,exchangeName, msg.getRoutingKey() ))
            throw UnauthorizedAccessException(QPID_MSG(userID << " cannot publish to " <<
                                               exchangeName << " with routing-key " << msg.getRoutingKey()));
    }

    cacheExchange->route(strategy);

    if (!strategy.delivered) {
        //TODO:if discard-unroutable, just drop it
        //TODO:else if accept-mode is explicit, reject it
        //else route it to alternate exchange
        if (cacheExchange->getAlternate()) {
            cacheExchange->getAlternate()->route(strategy);
        }
    }

}

void SemanticState::requestDispatch()
{
    for (ConsumerImplMap::iterator i = consumers.begin(); i != consumers.end(); i++)
        i->second->requestDispatch();
}

void SemanticStateConsumerImpl::requestDispatch()
{
    if (blocked) {
        parent->session.getConnection().addOutputTask(this);
        parent->session.getConnection().activateOutput();
        blocked = false;
    }
}

bool SemanticState::complete(DeliveryRecord& delivery)
{
    ConsumerImplMap::iterator i = consumers.find(delivery.getTag());
    if (i != consumers.end()) {
        i->second->complete(delivery);
    }
    return delivery.isRedundant();
}

void SemanticStateConsumerImpl::complete(DeliveryRecord& delivery)
{
    if (!delivery.isComplete()) {
        delivery.complete();
        if (credit.isWindowMode()) {
            credit.moveWindow(1, delivery.getCredit());
        }
    }
}

void SemanticState::requeue()
{
    //take copy and clear unacked as requeue may result in redelivery to this session
    //which will in turn result in additions to unacked
    DeliveryRecords copy = unacked;
    unacked.clear();
    for_each(copy.rbegin(), copy.rend(), mem_fun_ref(&DeliveryRecord::requeue));
    getSession().setUnackedCount(unacked.size());
}


SessionContext& SemanticState::getSession() { return session; }
const SessionContext& SemanticState::getSession() const { return session; }


const SemanticStateConsumerImpl::shared_ptr SemanticState::find(const std::string& destination) const
{
    ConsumerImpl::shared_ptr consumer;
    if (!find(destination, consumer)) {
        throw NotFoundException(QPID_MSG("Unknown destination " << destination << " session=" << session.getSessionId()));
    } else {
        return consumer;
    }
}

bool SemanticState::find(const std::string& destination, ConsumerImpl::shared_ptr& consumer) const
{
    // @todo KAG gsim: shouldn't the consumers map be locked????
    ConsumerImplMap::const_iterator i = consumers.find(destination);
    if (i == consumers.end()) {
        return false;
    }
    consumer = i->second;
    return true;
}

void SemanticState::setWindowMode(const std::string& destination)
{
    find(destination)->setWindowMode();
}

void SemanticState::setCreditMode(const std::string& destination)
{
    find(destination)->setCreditMode();
}

void SemanticState::addByteCredit(const std::string& destination, uint32_t value)
{
    ConsumerImpl::shared_ptr c = find(destination);
    c->addByteCredit(value);
    c->requestDispatch();
}


void SemanticState::addMessageCredit(const std::string& destination, uint32_t value)
{
    ConsumerImpl::shared_ptr c = find(destination);
    c->addMessageCredit(value);
    c->requestDispatch();
}

void SemanticState::flush(const std::string& destination)
{
    find(destination)->flush();
}


void SemanticState::stop(const std::string& destination)
{
    find(destination)->stop();
}

void SemanticStateConsumerImpl::setWindowMode()
{
    credit.setWindowMode(true);
    if (mgmtObject){
        mgmtObject->set_creditMode("WINDOW");
    }
}

void SemanticStateConsumerImpl::setCreditMode()
{
    credit.setWindowMode(false);
    if (mgmtObject){
        mgmtObject->set_creditMode("CREDIT");
    }
}

void SemanticStateConsumerImpl::addByteCredit(uint32_t value)
{
    credit.addByteCredit(value);
}

void SemanticStateConsumerImpl::addMessageCredit(uint32_t value)
{
    credit.addMessageCredit(value);
}

bool SemanticStateConsumerImpl::haveCredit()
{
    if (credit) {
        return true;
    } else {
        blocked = true;
        return false;
    }
}

bool SemanticStateConsumerImpl::doDispatch()
{
    return queue->dispatch(shared_from_this());
}

void SemanticStateConsumerImpl::flush()
{
    while(haveCredit() && doDispatch())
        ;
    credit.cancel();
}

void SemanticStateConsumerImpl::stop()
{
    credit.cancel();
}

Queue::shared_ptr SemanticState::getQueue(const string& name) const {
    Queue::shared_ptr queue;
    if (name.empty()) {
        throw NotAllowedException(QPID_MSG("No queue name specified."));
    } else {
        queue = session.getBroker().getQueues().get(name);
    }
    return queue;
}

AckRange SemanticState::findRange(DeliveryId first, DeliveryId last)
{
    return DeliveryRecord::findRange(unacked, first, last);
}

void SemanticState::acquire(DeliveryId first, DeliveryId last, DeliveryIds& acquired)
{
    AckRange range = findRange(first, last);
    for_each(range.start, range.end, AcquireFunctor(acquired));
}

void SemanticState::release(DeliveryId first, DeliveryId last, bool setRedelivered)
{
    AckRange range = findRange(first, last);
    //release results in the message being added to the head so want
    //to release in reverse order to keep the original transfer order
    DeliveryRecords::reverse_iterator start(range.end);
    DeliveryRecords::reverse_iterator end(range.start);
    for_each(start, end, boost::bind(&DeliveryRecord::release, _1, setRedelivered));

    DeliveryRecords::iterator removed =
        remove_if(range.start, range.end, bind(&DeliveryRecord::isRedundant, _1));
    unacked.erase(removed, range.end);
    getSession().setUnackedCount(unacked.size());
}

void SemanticState::reject(DeliveryId first, DeliveryId last)
{
    AckRange range = findRange(first, last);
    for_each(range.start, range.end, mem_fun_ref(&DeliveryRecord::reject));
    //may need to remove the delivery records as well
    for (DeliveryRecords::iterator i = range.start; i != unacked.end() && i->getId() <= last; ) {
        if (i->isRedundant()) i = unacked.erase(i);
        else i++;
    }
    getSession().setUnackedCount(unacked.size());
}

bool SemanticStateConsumerImpl::doOutput()
{
    try {
        return haveCredit() && doDispatch();
    } catch (const SessionException& e) {
        throw SessionOutputException(e, parent->session.getChannel());
    }
}

void SemanticStateConsumerImpl::enableNotify()
{
    Mutex::ScopedLock l(lock);
    notifyEnabled = true;
}

void SemanticStateConsumerImpl::disableNotify()
{
    Mutex::ScopedLock l(lock);
    notifyEnabled = false;
}

bool SemanticStateConsumerImpl::isNotifyEnabled() const {
    Mutex::ScopedLock l(lock);
    return notifyEnabled;
}

void SemanticStateConsumerImpl::notify()
{
    Mutex::ScopedLock l(lock);
    if (notifyEnabled) {
        parent->session.getConnection().addOutputTask(this);
        parent->session.getConnection().activateOutput();
    }
}


// Test that a DeliveryRecord's ID is in a sequence set and some other
// predicate on DeliveryRecord holds.
template <class Predicate> struct IsInSequenceSetAnd {
    IsInSequenceSet isInSet;
    Predicate predicate;
    IsInSequenceSetAnd(const SequenceSet& s, Predicate p) : isInSet(s), predicate(p) {}
    bool operator()(DeliveryRecord& dr) {
        return isInSet(dr.getId()) && predicate(dr);
    }
};

template<class Predicate> IsInSequenceSetAnd<Predicate>
isInSequenceSetAnd(const SequenceSet& s, Predicate p) {
    return IsInSequenceSetAnd<Predicate>(s,p);
}

void SemanticState::accepted(const SequenceSet& commands) {
    if (txBuffer.get()) {
        //in transactional mode, don't dequeue or remove, just
        //maintain set of acknowledged messages:
        accumulatedAck.add(commands);

        if (dtxBuffer.get()) {
            //if enlisted in a dtx, copy the relevant slice from
            //unacked and record it against that transaction
            TxOp::shared_ptr txAck(new DtxAck(accumulatedAck, unacked));
            accumulatedAck.clear();
            dtxBuffer->enlist(txAck);
            //mark the relevant messages as 'ended' in unacked
            //if the messages are already completed, they can be
            //removed from the record
            DeliveryRecords::iterator removed =
                remove_if(unacked.begin(), unacked.end(),
                          isInSequenceSetAnd(commands,
                                             bind(&DeliveryRecord::setEnded, _1)));
            unacked.erase(removed, unacked.end());
        }
    } else {
        DeliveryRecords::iterator removed =
            remove_if(unacked.begin(), unacked.end(),
                      isInSequenceSetAnd(commands,
                                         bind(&DeliveryRecord::accept, _1,
                                              (TransactionContext*) 0)));
        unacked.erase(removed, unacked.end());
    }
    getSession().setUnackedCount(unacked.size());
}

void SemanticState::completed(const SequenceSet& commands) {
    DeliveryRecords::iterator removed =
        remove_if(unacked.begin(), unacked.end(),
                  isInSequenceSetAnd(commands,
                                     bind(&SemanticState::complete, this, _1)));
    unacked.erase(removed, unacked.end());
    requestDispatch();
    getSession().setUnackedCount(unacked.size());
}

void SemanticState::attached()
{
    for (ConsumerImplMap::iterator i = consumers.begin(); i != consumers.end(); i++) {
        i->second->enableNotify();
        session.getConnection().addOutputTask(i->second.get());
    }
    session.getConnection().activateOutput();
}

void SemanticState::detached()
{
    for (ConsumerImplMap::iterator i = consumers.begin(); i != consumers.end(); i++) {
        i->second->disableNotify();
        session.getConnection().removeOutputTask(i->second.get());
    }
}

void SemanticState::addBinding(const string& queueName, const string& exchangeName,
                               const string& routingKey, const framing::FieldTable& arguments)
{
    QPID_LOG (debug, "SemanticState::addBinding ["
              << "queue=" << queueName << ", "
              << "exchange=" << exchangeName << ", "
              << "key=" << routingKey << ", "
              << "args=" << arguments << "]");
    std::string fedOp = arguments.getAsString(qpidFedOp);
    if ((arguments.isSet(qpidFedOp)) && (fedOp.empty())) {
      fedOp = fedOpBind;
    }
    std::string fedOrigin = arguments.getAsString(qpidFedOrigin);
    if ((arguments.getAsString(X_SCOPE) == SESSION) || (fedOp == fedOpBind)) {
        bindings.insert(boost::make_tuple(queueName, exchangeName, routingKey, fedOrigin));
    }
    else if (fedOp == fedOpUnbind) {
        bindings.erase(boost::make_tuple(queueName, exchangeName, routingKey, fedOrigin));
    }
}

void SemanticState::removeBinding(const string& queueName, const string& exchangeName,
                                  const string& routingKey)
{
    QPID_LOG (debug, "SemanticState::removeBinding ["
              << "queue=" << queueName << ", "
              << "exchange=" << exchangeName << ", "
              << "key=" << routingKey)
    bindings.erase(boost::make_tuple(queueName, exchangeName, routingKey, ""));
}

void SemanticState::unbindSessionBindings()
{
    //unbind session-scoped bindings
    for (Bindings::iterator i = bindings.begin(); i != bindings.end(); i++) {
        QPID_LOG (debug, "SemanticState::unbindSessionBindings ["
                  << "queue=" << i->get<0>() << ", "
                  << "exchange=" << i->get<1>()<< ", "
                  << "key=" << i->get<2>() << ", "
                  << "fedOrigin=" << i->get<3>() << "]");
        try {
            std::string fedOrigin = i->get<3>();
            if (!fedOrigin.empty()) {
                framing::FieldTable fedArguments;
                fedArguments.setString(qpidFedOp, fedOpUnbind);
                fedArguments.setString(qpidFedOrigin, fedOrigin);
                session.getBroker().bind(i->get<0>(), i->get<1>(), i->get<2>(), fedArguments,
                                         &session, userID, connectionId);
            } else {
                session.getBroker().unbind(i->get<0>(), i->get<1>(), i->get<2>(),
                                           &session, userID, connectionId);
            }
        }
        catch (...) {
        }
    }
    bindings.clear();
}

}} // namespace qpid::broker
