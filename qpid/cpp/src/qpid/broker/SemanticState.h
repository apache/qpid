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

#include "Consumer.h"
#include "Deliverable.h"
#include "DeliveryAdapter.h"
#include "DeliveryRecord.h"
#include "DtxBuffer.h"
#include "DtxManager.h"
#include "NameGenerator.h"
#include "TxBuffer.h"

#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/SequenceSet.h"
#include "qpid/framing/Uuid.h"
#include "qpid/sys/AggregateOutput.h"
#include "qpid/sys/Mutex.h"
#include "qpid/shared_ptr.h"
#include "AclModule.h"

#include <list>
#include <map>
#include <vector>

#include <boost/intrusive_ptr.hpp>
#include <boost/cast.hpp>

namespace qpid {
namespace broker {

class SessionContext;

/**
 * SemanticState holds the L3 and L4 state of an open session, whether
 * attached to a channel or suspended. 
 */
class SemanticState : public sys::OutputTask,
                      private boost::noncopyable
{
  public:
    class ConsumerImpl : public Consumer, public sys::OutputTask,
                         public boost::enable_shared_from_this<ConsumerImpl>
    {
        mutable qpid::sys::Mutex lock;
        SemanticState* const parent;
        const string name;
        const Queue::shared_ptr queue;
        const bool ackExpected;
        const bool acquire;
        bool blocked;
        bool windowing;
        bool exclusive;
        string resumeId;
        uint64_t resumeTtl;
        framing::FieldTable arguments;
        uint32_t msgCredit;
        uint32_t byteCredit;
        bool notifyEnabled;
        const int syncFrequency;
        int deliveryCount;

        bool checkCredit(boost::intrusive_ptr<Message>& msg);
        void allocateCredit(boost::intrusive_ptr<Message>& msg);

      public:
        typedef boost::shared_ptr<ConsumerImpl> shared_ptr;

        ConsumerImpl(SemanticState* parent, 
                     const string& name, Queue::shared_ptr queue,
                     bool ack, bool acquire, bool exclusive,
                     const std::string& resumeId, uint64_t resumeTtl, const framing::FieldTable& arguments);
        ~ConsumerImpl();
        OwnershipToken* getSession();
        bool deliver(QueuedMessage& msg);            
        bool filter(boost::intrusive_ptr<Message> msg);            
        bool accept(boost::intrusive_ptr<Message> msg);            

        void disableNotify();
        void enableNotify();
        void notify();
        bool isNotifyEnabled() const;

        void setWindowMode();
        void setCreditMode();
        void addByteCredit(uint32_t value);
        void addMessageCredit(uint32_t value);
        void flush();
        void stop();
        void complete(DeliveryRecord&);    
        Queue::shared_ptr getQueue() const { return queue; }
        bool isBlocked() const { return blocked; }
        bool setBlocked(bool set) { std::swap(set, blocked); return set; }

        bool hasOutput();
        bool doOutput();

        std::string getName() const { return name; }

        bool isAckExpected() const { return ackExpected; }
        bool isAcquire() const { return acquire; }
        bool isWindowing() const { return windowing; }
        bool isExclusive() const { return exclusive; }
        uint32_t getMsgCredit() const { return msgCredit; }
        uint32_t getByteCredit() const { return byteCredit; }
        std::string getResumeId() const { return resumeId; };
        uint64_t getResumeTtl() const { return resumeTtl; }
        const framing::FieldTable& getArguments() const { return arguments; }
    };

  private:
    typedef std::map<std::string, ConsumerImpl::shared_ptr> ConsumerImplMap;
    typedef std::map<std::string, DtxBuffer::shared_ptr> DtxBufferMap;

    SessionContext& session;
    DeliveryAdapter& deliveryAdapter;
    ConsumerImplMap consumers;
    NameGenerator tagGenerator;
    DeliveryRecords unacked;
    TxBuffer::shared_ptr txBuffer;
    DtxBuffer::shared_ptr dtxBuffer;
    bool dtxSelected;
    DtxBufferMap suspendedXids;
    framing::SequenceSet accumulatedAck;
    boost::shared_ptr<Exchange> cacheExchange;
    sys::AggregateOutput outputTasks;
    AclModule* acl;
    const bool authMsg;
    const string userID;

    void route(boost::intrusive_ptr<Message> msg, Deliverable& strategy);
    void checkDtxTimeout();

    void complete(DeliveryRecord&);
    AckRange findRange(DeliveryId first, DeliveryId last);
    void requestDispatch();
    void requestDispatch(ConsumerImpl&);
    void cancel(ConsumerImpl::shared_ptr);

  public:
    SemanticState(DeliveryAdapter&, SessionContext&);
    ~SemanticState();

    SessionContext& getSession() { return session; }

    ConsumerImpl& find(const std::string& destination);
    
    /**
     * Get named queue, never returns 0.
     * @return: named queue
     * @exception: ChannelException if no queue of that name is found.
     * @exception: ConnectionException if name="" and session has no default.
     */
    Queue::shared_ptr getQueue(const std::string& name) const;
    
    bool exists(const string& consumerTag);

    void consume(const string& destination, 
                 Queue::shared_ptr queue, 
                 bool ackRequired, bool acquire, bool exclusive,
                 const string& resumeId=string(), uint64_t resumeTtl=0,
                 const framing::FieldTable& = framing::FieldTable());

    void cancel(const string& tag);

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
    void startDtx(const std::string& xid, DtxManager& mgr, bool join);
    void endDtx(const std::string& xid, bool fail);
    void suspendDtx(const std::string& xid);
    void resumeDtx(const std::string& xid);
    void recover(bool requeue);
    void deliver(DeliveryRecord& message, bool sync);            
    void acquire(DeliveryId first, DeliveryId last, DeliveryIds& acquired);
    void release(DeliveryId first, DeliveryId last, bool setRedelivered);
    void reject(DeliveryId first, DeliveryId last);
    void handle(boost::intrusive_ptr<Message> msg);
    bool hasOutput() { return outputTasks.hasOutput(); }
    bool doOutput() { return outputTasks.doOutput(); }

    //final 0-10 spec (completed and accepted are distinct):
    void completed(DeliveryId deliveryTag, DeliveryId endTag);
    void accepted(DeliveryId deliveryTag, DeliveryId endTag);

    void attached();
    void detached();

    // Used by cluster to re-create replica sessions
    static ConsumerImpl* castToConsumerImpl(OutputTask* p) { return boost::polymorphic_downcast<ConsumerImpl*>(p); }

    template <class F> void eachConsumer(F f) { outputTasks.eachOutput(boost::bind(f, boost::bind(castToConsumerImpl, _1))); }
    DeliveryRecords& getUnacked() { return unacked; }
    framing::SequenceSet getAccumulatedAck() const { return accumulatedAck; }
    TxBuffer::shared_ptr getTxBuffer() const { return txBuffer; }
    void setTxBuffer(const TxBuffer::shared_ptr& txb) { txBuffer = txb; }
    void setAccumulatedAck(const framing::SequenceSet& s) { accumulatedAck = s; }
    void record(const DeliveryRecord& delivery);
};

}} // namespace qpid::broker




#endif  /*!QPID_BROKER_SEMANTICSTATE_H*/
