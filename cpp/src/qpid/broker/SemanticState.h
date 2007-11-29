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
#include "DeliveryToken.h"
#include "DtxBuffer.h"
#include "DtxManager.h"
#include "NameGenerator.h"
#include "Prefetch.h"
#include "TxBuffer.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/AccumulatedAck.h"
#include "qpid/framing/Uuid.h"
#include "qpid/sys/AggregateOutput.h"
#include "qpid/shared_ptr.h"

#include <list>
#include <map>
#include <vector>

namespace qpid {
namespace broker {

class SessionState;

/**
 * SemanticState holds the L3 and L4 state of an open session, whether
 * attached to a channel or suspended. 
 */
class SemanticState : public framing::FrameHandler::Chains,
                      public sys::OutputTask,
                      private boost::noncopyable
{
    class ConsumerImpl : public Consumer, public sys::OutputTask
    {
        SemanticState* const parent;
        const DeliveryToken::shared_ptr token;
        const string name;
        const Queue::shared_ptr queue;
        const bool ackExpected;
        const bool nolocal;
        const bool acquire;
        bool blocked;
        bool windowing;
        uint32_t msgCredit;
        uint32_t byteCredit;

        bool checkCredit(intrusive_ptr<Message>& msg);
        void allocateCredit(intrusive_ptr<Message>& msg);

      public:
        ConsumerImpl(SemanticState* parent, DeliveryToken::shared_ptr token, 
                     const string& name, Queue::shared_ptr queue,
                     bool ack, bool nolocal, bool acquire);
        ~ConsumerImpl();
        bool deliver(QueuedMessage& msg);            
        bool filter(intrusive_ptr<Message> msg);            
        bool accept(intrusive_ptr<Message> msg);            
        void notify();

        void setWindowMode();
        void setCreditMode();
        void addByteCredit(uint32_t value);
        void addMessageCredit(uint32_t value);
        void flush();
        void stop();
        void acknowledged(const DeliveryRecord&);    
        Queue::shared_ptr getQueue() { return queue; }
        bool isBlocked() const { return blocked; }

        bool doOutput();
    };

    typedef boost::ptr_map<std::string,ConsumerImpl> ConsumerImplMap;
    typedef std::map<std::string, DtxBuffer::shared_ptr> DtxBufferMap;

    SessionState& session;
    DeliveryAdapter& deliveryAdapter;
    Queue::shared_ptr defaultQueue;
    ConsumerImplMap consumers;
    uint32_t prefetchSize;    
    uint16_t prefetchCount;    
    Prefetch outstanding;
    NameGenerator tagGenerator;
    std::list<DeliveryRecord> unacked;
    TxBuffer::shared_ptr txBuffer;
    DtxBuffer::shared_ptr dtxBuffer;
    bool dtxSelected;
    DtxBufferMap suspendedXids;
    framing::AccumulatedAck accumulatedAck;
    bool flowActive;
    boost::shared_ptr<Exchange> cacheExchange;
    sys::AggregateOutput outputTasks;
    
    void route(intrusive_ptr<Message> msg, Deliverable& strategy);
    void record(const DeliveryRecord& delivery);
    bool checkPrefetch(intrusive_ptr<Message>& msg);
    void checkDtxTimeout();
    ConsumerImpl& find(const std::string& destination);
    void ack(DeliveryId deliveryTag, DeliveryId endTag, bool cumulative);
    void acknowledged(const DeliveryRecord&);
    AckRange findRange(DeliveryId first, DeliveryId last);
    void requestDispatch();
    void requestDispatch(ConsumerImpl&);
    void cancel(ConsumerImpl&);

  public:
    SemanticState(DeliveryAdapter&, SessionState&);
    ~SemanticState();

    SessionState& getSession() { return session; }
    
    /**
     * Get named queue, never returns 0.
     * @return: named queue
     * @exception: ChannelException if no queue of that name is found.
     * @exception: ConnectionException if name="" and session has no default.
     */
    Queue::shared_ptr getQueue(const std::string& name) const;
    
    uint32_t setPrefetchSize(uint32_t size){ return prefetchSize = size; }
    uint16_t setPrefetchCount(uint16_t n){ return prefetchCount = n; }

    bool exists(const string& consumerTag);

    /**
     *@param tagInOut - if empty it is updated with the generated token.
     */
    void consume(DeliveryToken::shared_ptr token, string& tagInOut, Queue::shared_ptr queue, 
                 bool nolocal, bool acks, bool acquire, bool exclusive, const framing::FieldTable* = 0);

    void cancel(const string& tag);

    void setWindowMode(const std::string& destination);
    void setCreditMode(const std::string& destination);
    void addByteCredit(const std::string& destination, uint32_t value);
    void addMessageCredit(const std::string& destination, uint32_t value);
    void flush(const std::string& destination);
    void stop(const std::string& destination);

    bool get(DeliveryToken::shared_ptr token, Queue::shared_ptr queue, bool ackExpected);
    void startTx();
    void commit(MessageStore* const store);
    void rollback();
    void selectDtx();
    void startDtx(const std::string& xid, DtxManager& mgr, bool join);
    void endDtx(const std::string& xid, bool fail);
    void suspendDtx(const std::string& xid);
    void resumeDtx(const std::string& xid);
    void ackCumulative(DeliveryId deliveryTag);
    void ackRange(DeliveryId deliveryTag, DeliveryId endTag);
    void recover(bool requeue);
    void flow(bool active);
    DeliveryId redeliver(QueuedMessage& msg, DeliveryToken::shared_ptr token);            
    void acquire(DeliveryId first, DeliveryId last, DeliveryIds& acquired);
    void release(DeliveryId first, DeliveryId last);
    void reject(DeliveryId first, DeliveryId last);
    void handle(intrusive_ptr<Message> msg);

    bool doOutput() { return outputTasks.doOutput(); }
};

}} // namespace qpid::broker




#endif  /*!QPID_BROKER_SEMANTICSTATE_H*/
