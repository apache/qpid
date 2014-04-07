#ifndef QPID_BROKER_SEMANTICSTATE_H
#define QPID_BROKER_SEMANTICSTATE_H

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

#include "qpid/broker/BrokerImportExport.h"
#include "qpid/broker/Consumer.h"
#include "qpid/broker/Credit.h"
#include "qpid/broker/Deliverable.h"
#include "qpid/broker/DeliveryRecord.h"
#include "qpid/broker/DtxBuffer.h"
#include "qpid/broker/DtxManager.h"
#include "qpid/broker/NameGenerator.h"
#include "qpid/broker/QueueObserver.h"
#include "qpid/broker/TxBuffer.h"

#include "qpid/framing/FieldTable.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/SequenceSet.h"
#include "qpid/framing/Uuid.h"
#include "qpid/sys/AggregateOutput.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/AtomicValue.h"
#include "qpid/broker/AclModule.h"
#include "qpid/management/Manageable.h"
#include "qmf/org/apache/qpid/broker/Subscription.h"

#include <list>
#include <map>
#include <set>
#include <vector>

#include <boost/enable_shared_from_this.hpp>
#include <boost/cast.hpp>
#include <boost/tuple/tuple.hpp>
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace broker {

class Exchange;
class MessageStore;
class ProtocolRegistry;
class Selector;
class SessionContext;
class SessionState;

/**
 *
 * SemanticState implements the behavior of a Session, especially the
 * state of consumers subscribed to queues. The code for ConsumerImpl
 * is also in SemanticState.cpp
 *
 * SemanticState holds the AMQP Execution and Model state of an open
 * session, whether attached to a channel or suspended. It is not
 * dependent on any specific AMQP version.
 *
 * Message delivery is driven by ConsumerImpl::doOutput(), which is
 * called when a client's socket is ready to write data.
 *
 */
class SemanticStateConsumerImpl;
class SemanticState : private boost::noncopyable {
  friend class SemanticStateConsumerImpl;

  public:
    typedef SemanticStateConsumerImpl ConsumerImpl;
    typedef std::map<std::string, boost::intrusive_ptr<DtxBuffer> > DtxBufferMap;

  private:
    typedef std::map<std::string, boost::shared_ptr<ConsumerImpl> > ConsumerImplMap;
    typedef boost::tuple<std::string, std::string, std::string, std::string> Binding;
    typedef std::set<Binding> Bindings;

    SessionState& session;
    ConsumerImplMap consumers;
    NameGenerator tagGenerator;
    DeliveryRecords unacked;
    boost::intrusive_ptr<TxBuffer> txBuffer;
    boost::intrusive_ptr<DtxBuffer> dtxBuffer;
    bool dtxSelected;
    DtxBufferMap suspendedXids;
    framing::SequenceSet accumulatedAck;
    boost::shared_ptr<Exchange> cacheExchange;
    const bool authMsg;
    const std::string userID;
    bool closeComplete;
    //needed for queue delete events in auto-delete:
    const std::string connectionId;

    Bindings bindings;

    void checkDtxTimeout();

    bool complete(DeliveryRecord&);
    AckRange findRange(DeliveryId first, DeliveryId last);
    void requestDispatch();
    void cancel(boost::shared_ptr<ConsumerImpl>);
    void disable(boost::shared_ptr<ConsumerImpl>);
    void unbindSessionBindings();

  public:

    SemanticState(SessionState&);
    ~SemanticState();

    SessionContext& getSession();
    const SessionContext& getSession() const;
    SessionState& getSessionState() { return session; }

    const boost::shared_ptr<ConsumerImpl> find(const std::string& destination) const;
    bool find(const std::string& destination, boost::shared_ptr<ConsumerImpl>&) const;

    /**
     * Get named queue, never returns 0.
     * @return: named queue
     * @exception: ChannelException if no queue of that name is found.
     * @exception: ConnectionException if name="" and session has no default.
     */
    boost::shared_ptr<Queue> getQueue(const std::string& name) const;

    bool exists(const std::string& consumerTag);

    void consume(const std::string& destination,
                 boost::shared_ptr<Queue> queue,
                 bool ackRequired, bool acquire, bool exclusive,
                 const std::string& resumeId=std::string(), uint64_t resumeTtl=0,
                 const framing::FieldTable& = framing::FieldTable());

    bool cancel(const std::string& tag);

    void setWindowMode(const std::string& destination);
    void setCreditMode(const std::string& destination);
    void addByteCredit(const std::string& destination, uint32_t value);
    void addMessageCredit(const std::string& destination, uint32_t value);
    void flush(const std::string& destination);
    void stop(const std::string& destination);

    void startTx();
    void commit(MessageStore* const store);
    void rollback();
    void selectDtx();
    bool getDtxSelected() const { return dtxSelected; }
    void startDtx(const std::string& xid, DtxManager& mgr, bool join);
    void endDtx(const std::string& xid, bool fail);
    void suspendDtx(const std::string& xid);
    void resumeDtx(const std::string& xid);
    TxBuffer* getTxBuffer();
    void requeue();
    void acquire(DeliveryId first, DeliveryId last, DeliveryIds& acquired);
    void release(DeliveryId first, DeliveryId last, bool setRedelivered);
    void reject(DeliveryId first, DeliveryId last);
    void route(Message& msg, Deliverable& strategy);

    void completed(const framing::SequenceSet& commands);
    void accepted(const framing::SequenceSet& commands);

    void attached();
    void detached();
    void closed();

    DeliveryRecords& getUnacked() { return unacked; }
    framing::SequenceSet getAccumulatedAck() const { return accumulatedAck; }
    boost::intrusive_ptr<TxBuffer> getTxBuffer() const { return txBuffer; }
    boost::intrusive_ptr<DtxBuffer> getDtxBuffer() const { return dtxBuffer; }
    void setTxBuffer(const boost::intrusive_ptr<TxBuffer>& txb) { txBuffer = txb; }
    void setDtxBuffer(const boost::intrusive_ptr<DtxBuffer>& dtxb) { dtxBuffer = dtxb; txBuffer = dtxb; }
    void setAccumulatedAck(const framing::SequenceSet& s) { accumulatedAck = s; }
    void record(const DeliveryRecord& delivery);
    DtxBufferMap& getSuspendedXids() { return suspendedXids; }

    void addBinding(const std::string& queueName, const std::string& exchangeName,
                   const std::string& routingKey, const framing::FieldTable& arguments);
    void removeBinding(const std::string& queueName, const std::string& exchangeName,
                      const std::string& routingKey);
};

class SemanticStateConsumerImpl : public Consumer, public sys::OutputTask,
                        public boost::enable_shared_from_this<SemanticStateConsumerImpl>,
                        public management::Manageable
{
    protected:
    mutable qpid::sys::Mutex lock;
    SemanticState* const parent;
    const boost::shared_ptr<Queue> queue;

    private:
    const bool ackExpected;
    const bool acquire;
    bool blocked;
    bool exclusive;
    std::string resumeId;
    boost::shared_ptr<Selector> selector;
    uint64_t resumeTtl;
    framing::FieldTable arguments;
    Credit credit;
    bool notifyEnabled;
    const int syncFrequency;
    int deliveryCount;
    qmf::org::apache::qpid::broker::Subscription::shared_ptr mgmtObject;
    ProtocolRegistry& protocols;

    bool checkCredit(const Message& msg);
    void allocateCredit(const Message& msg);
    bool haveCredit();

    protected:
    QPID_BROKER_EXTERN virtual bool doDispatch();
    size_t unacked() { return parent->unacked.size(); }
    QPID_BROKER_EXTERN bool deliver(const QueueCursor&, const Message&, boost::shared_ptr<Consumer>);

    public:
    typedef boost::shared_ptr<SemanticStateConsumerImpl> shared_ptr;

    QPID_BROKER_EXTERN SemanticStateConsumerImpl(SemanticState* parent,
                    const std::string& name, boost::shared_ptr<Queue> queue,
                    bool ack, SubscriptionType type, bool exclusive,
                    const std::string& tag, const std::string& resumeId,
                    uint64_t resumeTtl, const framing::FieldTable& arguments);
    QPID_BROKER_EXTERN ~SemanticStateConsumerImpl();
    QPID_BROKER_EXTERN OwnershipToken* getSession();
    QPID_BROKER_EXTERN bool deliver(const QueueCursor&, const Message&);
    QPID_BROKER_EXTERN bool filter(const Message&);
    QPID_BROKER_EXTERN bool accept(const Message&);
    QPID_BROKER_EXTERN void cancel() {}

    QPID_BROKER_EXTERN void disableNotify();
    QPID_BROKER_EXTERN void enableNotify();
    QPID_BROKER_EXTERN void notify();
    QPID_BROKER_EXTERN bool isNotifyEnabled() const;

    QPID_BROKER_EXTERN void requestDispatch();

    QPID_BROKER_EXTERN void setWindowMode();
    QPID_BROKER_EXTERN void setCreditMode();
    QPID_BROKER_EXTERN void addByteCredit(uint32_t value);
    QPID_BROKER_EXTERN void addMessageCredit(uint32_t value);
    QPID_BROKER_EXTERN void flush();
    QPID_BROKER_EXTERN void stop();
    QPID_BROKER_EXTERN void complete(DeliveryRecord&);
    boost::shared_ptr<Queue> getQueue() const { return queue; }
    bool isBlocked() const { return blocked; }
    bool setBlocked(bool set) { std::swap(set, blocked); return set; }

    QPID_BROKER_EXTERN bool doOutput();

    Credit& getCredit() { return credit; }
    const Credit& getCredit() const { return credit; }
    bool isAckExpected() const { return ackExpected; }
    bool isAcquire() const { return acquire; }
    bool isExclusive() const { return exclusive; }
    std::string getResumeId() const { return resumeId; };
    uint64_t getResumeTtl() const { return resumeTtl; }
    uint32_t getDeliveryCount() const { return deliveryCount; }
    void setDeliveryCount(uint32_t _deliveryCount) { deliveryCount = _deliveryCount; }
    const framing::FieldTable& getArguments() const { return arguments; }

    SemanticState& getParent() { return *parent; }
    const SemanticState& getParent() const { return *parent; }

    void acknowledged(const DeliveryRecord&) {}

    // manageable entry points
    QPID_BROKER_EXTERN management::ManagementObject::shared_ptr
    GetManagementObject(void) const;

    QPID_BROKER_EXTERN management::Manageable::status_t
    ManagementMethod(uint32_t methodId, management::Args& args, std::string& text);
};

}} // namespace qpid::broker




#endif  /*!QPID_BROKER_SEMANTICSTATE_H*/
