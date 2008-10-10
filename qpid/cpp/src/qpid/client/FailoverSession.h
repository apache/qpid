#ifndef QPID_CLIENT_FAILOVERSESSION_H
#define QPID_CLIENT_FAILOVERSESSION_H

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

#include "qpid/client/Session.h"
#include "qpid/SessionId.h"
#include "qpid/framing/amqp_structs.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/framing/MethodContent.h"
#include "qpid/framing/TransferContent.h"
#include "qpid/client/Completion.h"
#include "qpid/client/Connection.h"
#include "qpid/client/ConnectionImpl.h"
#include "qpid/client/Execution.h"
#include "qpid/client/SessionImpl.h"
#include "qpid/client/TypedResult.h"
#include "qpid/shared_ptr.h"
#include "qpid/sys/Mutex.h"

#include <string>




namespace qpid {
namespace client {


class FailoverConnection;
class FailoverSubscriptionManager;


class FailoverSession
{
  public:

    typedef framing::TransferContent DefaultContent;

    FailoverSession ( );
    ~FailoverSession ( );

    std::string name;

    framing::FrameSet::shared_ptr get();

    SessionId getId() const;

    void close();

    void sync();

    uint32_t timeout ( uint32_t seconds);

    Execution& getExecution();

    void flush();

    void markCompleted(const framing::SequenceNumber& id, 
                       bool cumulative, 
                       bool notifyPeer
                      );
    
    void sendCompletion ( );



    // Wrapped functions from Session ----------------------------

    void 
    executionSync();


    void 
    executionResult(const SequenceNumber& commandId=SequenceNumber(), 
                    const string& value=string());


    void 
    executionException(uint16_t errorCode=0, 
                       const SequenceNumber& commandId=SequenceNumber(), 
                       uint8_t classCode=0, 
                       uint8_t commandCode=0, 
                       uint8_t fieldIndex=0, 
                       const string& description=string(), 
                       const FieldTable& errorInfo=FieldTable());


    void 
    messageTransfer(const string& destination=string(), 
                    uint8_t acceptMode=1, 
                    uint8_t acquireMode=0, 
                    const MethodContent& content=DefaultContent(std::string()));


    void 
    messageAccept(const SequenceSet& transfers=SequenceSet());


    void 
    messageReject(const SequenceSet& transfers=SequenceSet(), 
                  uint16_t code=0, 
                  const string& text=string());


    void 
    messageRelease(const SequenceSet& transfers=SequenceSet(), 
                   bool setRedelivered=false);


    qpid::framing::MessageAcquireResult 
    messageAcquire(const SequenceSet& transfers=SequenceSet());


    qpid::framing::MessageResumeResult 
    messageResume(const string& destination=string(), 
                  const string& resumeId=string());


    void 
    messageSubscribe(const string& queue=string(), 
                     const string& destination=string(), 
                     uint8_t acceptMode=0, 
                     uint8_t acquireMode=0, 
                     bool exclusive=false, 
                     const string& resumeId=string(), 
                     uint64_t resumeTtl=0, 
                     const FieldTable& arguments=FieldTable());


    void 
    messageCancel(const string& destination=string());

    
    void 
    messageSetFlowMode(const string& destination=string(), 
                            uint8_t flowMode=0);


    void 
    messageFlow(const string& destination=string(), 
                uint8_t unit=0, 
                uint32_t value=0);


    void 
    messageFlush(const string& destination=string());


    void 
    messageStop(const string& destination=string());


    void 
    txSelect();


    void 
    txCommit();


    void 
    txRollback();


    void 
    dtxSelect();


    qpid::framing::XaResult 
    dtxStart(const Xid& xid=Xid(), 
             bool join=false, 
             bool resume=false);


    qpid::framing::XaResult 
    dtxEnd(const Xid& xid=Xid(), 
           bool fail=false, 
           bool suspend=false);


    qpid::framing::XaResult 
    dtxCommit(const Xid& xid=Xid(), 
              bool onePhase=false);


    void 
    dtxForget(const Xid& xid=Xid());


    qpid::framing::DtxGetTimeoutResult 
    dtxGetTimeout(const Xid& xid=Xid());


    qpid::framing::XaResult 
    dtxPrepare(const Xid& xid=Xid());


    qpid::framing::DtxRecoverResult 
    dtxRecover();


    qpid::framing::XaResult 
    dtxRollback(const Xid& xid=Xid());


    void 
    dtxSetTimeout(const Xid& xid=Xid(), 
                  uint32_t timeout=0);


    void 
    exchangeDeclare(const string& exchange=string(), 
                    const string& type=string(), 
                    const string& alternateExchange=string(), 
                    bool passive=false, 
                    bool durable=false, 
                    bool autoDelete=false, 
                    const FieldTable& arguments=FieldTable());


    void 
    exchangeDelete(const string& exchange=string(), 
                   bool ifUnused=false);


    qpid::framing::ExchangeQueryResult 
    exchangeQuery(const string& name=string());


    void 
    exchangeBind(const string& queue=string(), 
                 const string& exchange=string(), 
                 const string& bindingKey=string(), 
                 const FieldTable& arguments=FieldTable());


    void 
    exchangeUnbind(const string& queue=string(), 
                   const string& exchange=string(), 
                   const string& bindingKey=string());


    qpid::framing::ExchangeBoundResult 
    exchangeBound(const string& exchange=string(), 
                  const string& queue=string(), 
                  const string& bindingKey=string(), 
                  const FieldTable& arguments=FieldTable());


    void 
    queueDeclare(const string& queue=string(), 
                 const string& alternateExchange=string(), 
                 bool passive=false, 
                 bool durable=false, 
                 bool exclusive=false, 
                 bool autoDelete=false, 
                 const FieldTable& arguments=FieldTable());


    void 
    queueDelete(const string& queue=string(), 
                bool ifUnused=false, 
                bool ifEmpty=false);


    void 
    queuePurge(const string& queue=string());


    qpid::framing::QueueQueryResult 
    queueQuery(const string& queue=string());

    // end Wrapped functions from Session  ---------------------------

    // Tells the FailoverSession to get ready for a failover.
    void prepareForFailover ( Connection newConnection );

    void failover ( );

    void setFailoverSubscriptionManager(FailoverSubscriptionManager*);

  private:
    typedef sys::Mutex::ScopedLock Lock;
    sys::Mutex lock;

    FailoverSubscriptionManager * failoverSubscriptionManager;

    Session session;
    Session newSession;

    friend class FailoverConnection;
    friend class FailoverSubscriptionManager;
};

}} // namespace qpid::client


#endif  /*!QPID_CLIENT_FAILOVERSESSION_H*/
