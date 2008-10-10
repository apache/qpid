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

#include <iostream>
#include <fstream>


#include "qpid/log/Logger.h"
#include "qpid/log/Options.h"
#include "qpid/log/Statement.h"

#include "qpid/client/FailoverConnection.h"
#include "qpid/client/FailoverSession.h"


using namespace std;


namespace qpid {
namespace client {

FailoverSession::FailoverSession ( ) :
  name("no_name")
{
  // The session is created by FailoverConnection::newSession
  failoverSubscriptionManager = 0;
}


FailoverSession::~FailoverSession ( )
{
}


framing::FrameSet::shared_ptr 
FailoverSession::get()
{
  return session.get();
}


SessionId 
FailoverSession::getId() const
{
  return session.getId();
}


void 
FailoverSession::close()
{
  session.close();
}


void 
FailoverSession::sync()
{
  session.sync();
}


uint32_t 
FailoverSession::timeout(uint32_t /*seconds*/ )
{
  // MICK WTF?  return session.timeout ( seconds );
  return 0;
}


Execution& 
FailoverSession::getExecution()
{
  return session.getExecution();
}


void 
FailoverSession::flush()
{
  session.flush();
}


void 
FailoverSession::markCompleted(const framing::SequenceNumber& id, 
                               bool cumulative, 
                               bool notifyPeer
                              )
{
  session.markCompleted ( id, cumulative, notifyPeer );
}



// Wrapped functions from Session ----------------------------

void 
FailoverSession::executionSync()
{
  session.executionSync();
}



void 
FailoverSession::executionResult ( const SequenceNumber& commandId, 
                                   const string& value
                                 )
{
  session.executionResult ( commandId, 
                            value 
                          );
}



void 
FailoverSession::executionException ( uint16_t errorCode, 
                                      const SequenceNumber& commandId, 
                                      uint8_t classCode, 
                                      uint8_t commandCode, 
                                      uint8_t fieldIndex, 
                                      const string& description, 
                                      const FieldTable& errorInfo
                                    )
{
  session.executionException ( errorCode,
                               commandId,
                               classCode,
                               commandCode,
                               fieldIndex,
                               description,
                               errorInfo
                             );
}



void 
FailoverSession::messageTransfer ( const string& destination, 
                                   uint8_t acceptMode, 
                                   uint8_t acquireMode, 
                                   const MethodContent& content
                                 )
{
  session.messageTransfer ( destination,
                            acceptMode,
                            acquireMode,
                            content
                          );
}



void 
FailoverSession::messageAccept ( const SequenceSet& transfers )
{
  session.messageAccept ( transfers );
}



void 
FailoverSession::messageReject ( const SequenceSet& transfers, 
                                 uint16_t code, 
                                 const string& text
                               )
{
  session.messageReject ( transfers, 
                          code, 
                          text 
                        );
}



void 
FailoverSession::messageRelease ( const SequenceSet& transfers, 
                                  bool setRedelivered
                                )
{
  session.messageRelease ( transfers,
                           setRedelivered
                         );
}



qpid::framing::MessageAcquireResult 
FailoverSession::messageAcquire ( const SequenceSet& transfers )
{
  return session.messageAcquire ( transfers );
}



qpid::framing::MessageResumeResult 
FailoverSession::messageResume ( const string& destination, 
                                 const string& resumeId
                               )
{
  return session.messageResume ( destination,
                                 resumeId
                               );
}



void 
FailoverSession::messageSubscribe ( const string& queue, 
                                    const string& destination, 
                                    uint8_t acceptMode, 
                                    uint8_t acquireMode, 
                                    bool exclusive, 
                                    const string& resumeId, 
                                    uint64_t resumeTtl, 
                                    const FieldTable& arguments
                                  )
{
  session.messageSubscribe ( queue,
                             destination,
                             acceptMode,
                             acquireMode,
                             exclusive,
                             resumeId,
                             resumeTtl,
                             arguments
                           );
}



void 
FailoverSession::messageCancel ( const string& destinations )
{
  session.messageCancel ( destinations );
}



void 
FailoverSession::messageSetFlowMode ( const string& destination, 
                                      uint8_t flowMode
                                    )
{
  session.messageSetFlowMode ( destination,
                               flowMode
                             );
}



void 
FailoverSession::messageFlow(const string& destination, 
                             uint8_t unit, 
                             uint32_t value)
{
  session.messageFlow ( destination,
                        unit,
                        value
                      );
}



void 
FailoverSession::messageFlush(const string& destination)
{
  session.messageFlush ( destination );
}



void 
FailoverSession::messageStop(const string& destination)
{
  session.messageStop ( destination );
}



void 
FailoverSession::txSelect()
{
  session.txSelect ( );
}



void 
FailoverSession::txCommit()
{
  session.txCommit ( );
}



void 
FailoverSession::txRollback()
{
  session.txRollback ( );
}



void 
FailoverSession::dtxSelect()
{
  session.dtxSelect ( );
}



qpid::framing::XaResult 
FailoverSession::dtxStart(const Xid& xid, 
                          bool join, 
                          bool resume)
{
  return session.dtxStart ( xid,
                            join,
                            resume
                          );
}



qpid::framing::XaResult 
FailoverSession::dtxEnd(const Xid& xid, 
                        bool fail, 
                        bool suspend)
{
  return session.dtxEnd ( xid,
                          fail,
                          suspend
                        );
}



qpid::framing::XaResult 
FailoverSession::dtxCommit(const Xid& xid, 
                           bool onePhase)
{
  return session.dtxCommit ( xid,
                             onePhase
                           );
}



void 
FailoverSession::dtxForget(const Xid& xid)
{
  session.dtxForget ( xid );
}



qpid::framing::DtxGetTimeoutResult 
FailoverSession::dtxGetTimeout(const Xid& xid)
{
  return session.dtxGetTimeout ( xid );
}



qpid::framing::XaResult 
FailoverSession::dtxPrepare(const Xid& xid)
{
  return session.dtxPrepare ( xid );
}



qpid::framing::DtxRecoverResult 
FailoverSession::dtxRecover()
{
  return session.dtxRecover ( );
}



qpid::framing::XaResult 
FailoverSession::dtxRollback(const Xid& xid)
{
  return session.dtxRollback ( xid );
}



void 
FailoverSession::dtxSetTimeout(const Xid& xid, 
                               uint32_t timeout)
{
  session.dtxSetTimeout ( xid,
                          timeout
                        );
}



void 
FailoverSession::exchangeDeclare(const string& exchange, 
                                 const string& type, 
                                 const string& alternateExchange, 
                                 bool passive, 
                                 bool durable, 
                                 bool autoDelete, 
                                 const FieldTable& arguments)
{
  session.exchangeDeclare ( exchange,
                            type,
                            alternateExchange,
                            passive,
                            durable,
                            autoDelete,
                            arguments
                          );
}



void 
FailoverSession::exchangeDelete(const string& exchange, 
                                bool ifUnused)
{
  session.exchangeDelete ( exchange,
                           ifUnused
                         );
}



qpid::framing::ExchangeQueryResult 
FailoverSession::exchangeQuery(const string& name)
{
  return session.exchangeQuery ( name );
}



void 
FailoverSession::exchangeBind(const string& queue, 
                              const string& exchange, 
                              const string& bindingKey, 
                              const FieldTable& arguments)
{
  session.exchangeBind ( queue,
                         exchange,
                         bindingKey,
                         arguments
                       );
}



void 
FailoverSession::exchangeUnbind(const string& queue, 
                                const string& exchange, 
                                const string& bindingKey)
{
  session.exchangeUnbind ( queue,
                           exchange,
                           bindingKey
                         );
}



qpid::framing::ExchangeBoundResult 
FailoverSession::exchangeBound(const string& exchange, 
                               const string& queue, 
                               const string& bindingKey, 
                               const FieldTable& arguments)
{
  return session.exchangeBound ( exchange,
                                 queue,
                                 bindingKey,
                                 arguments
                               );
}



void 
FailoverSession::queueDeclare(const string& queue, 
                              const string& alternateExchange, 
                              bool passive, 
                              bool durable, 
                              bool exclusive, 
                              bool autoDelete, 
                              const FieldTable& arguments)
{
  session.queueDeclare ( queue,
                         alternateExchange,
                         passive,
                         durable,
                         exclusive,
                         autoDelete,
                         arguments
                       );
}



void 
FailoverSession::queueDelete(const string& queue, 
                             bool ifUnused, 
                             bool ifEmpty)
{
  session.queueDelete ( queue,
                        ifUnused,
                        ifEmpty
                      );
}



void 
FailoverSession::queuePurge(const string& queue)
{
  session.queuePurge ( queue) ;
}



qpid::framing::QueueQueryResult 
FailoverSession::queueQuery(const string& queue)
{
  return session.queueQuery ( queue );
}


// end Wrapped functions from Session  ---------------------------


// Get ready for a failover.
void
FailoverSession::prepareForFailover ( Connection newConnection )
{
  try
  {
    newSession = newConnection.newSession();
  }
  catch ( const std::exception& error )
  {
    throw Exception(QPID_MSG("Can't create failover session."));
  }


  if ( failoverSubscriptionManager )
  {
    failoverSubscriptionManager->prepareForFailover ( newSession );
  }
}



void 
FailoverSession::failover (  )
{
  if ( failoverSubscriptionManager )
  {
    failoverSubscriptionManager->failover ( );
  }

  session = newSession;
}




}} // namespace qpid::client
