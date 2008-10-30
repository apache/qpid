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
#include "qpid/sys/Time.h"

#include "qpid/client/FailoverConnection.h"
#include "qpid/client/FailoverSession.h"


using namespace std;


namespace qpid {
namespace client {

FailoverSession::FailoverSession ( ) :
    failover_in_progress(false),
    failover_count(0)
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
    while(1)
    {
    try
    {
    return session.get();
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }

}


SessionId 
FailoverSession::getId()
{
    while(1)
    {
    try
    {
    return session.getId();
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}


void 
FailoverSession::close()
{
    while(1)
    {
    try
    {
    session.close();
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}


void 
FailoverSession::sync()
{
    while(1)
    {
      try
      {
        session.sync();
        return;
      }
      catch ( const std::exception& error )
      {
        if ( ! failover_in_progress )
          throw ( error );
        else
        {
          sys::Monitor::ScopedLock l(lock);
          int current_failover_count = failover_count;
          while ( current_failover_count == failover_count )
            lock.wait();
        }
      }
    }

}


uint32_t 
FailoverSession::timeout(uint32_t /*seconds*/ )
{
    // FIXME mgoulish return session.timeout ( seconds );
    return 0;
}


Execution& 
FailoverSession::getExecution()
{
    while(1)
    {
    try
    {
    return session.getExecution();
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }

}


void 
FailoverSession::flush()
{
    while(1)
    {
    try
    {
    session.flush();
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }

}


void 
FailoverSession::markCompleted(const framing::SequenceNumber& id, 
                               bool cumulative, 
                               bool notifyPeer
)
{
    while(1)
    {
    try
    {
    session.markCompleted ( id, cumulative, notifyPeer );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }

}



// Wrapped functions from Session ----------------------------

void 
FailoverSession::executionSync()
{
    while(1)
    {
    try
    {
    session.executionSync();
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }

}



void 
FailoverSession::executionResult ( const SequenceNumber& commandId, 
                                   const string& value
)
{
    while(1)
    {
    try
    {
    session.executionResult ( commandId, 
                              value 
    );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }

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
    while(1)
    {
    try
    {
    session.executionException ( errorCode,
                                 commandId,
                                 classCode,
                                 commandCode,
                                 fieldIndex,
                                 description,
                                 errorInfo
    );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }

}



void 
FailoverSession::messageTransfer ( const string& destination, 
                                   uint8_t acceptMode, 
                                   uint8_t acquireMode, 
                                   const MethodContent& content
)
{

    while ( 1 )
    {
      try
      {
        session.messageTransfer ( destination,
                                  acceptMode,
                                  acquireMode,
                                  content
        );
        return;
      }
      catch ( ... )
      {
        // Take special action only if there is a failover in progress.
        if ( ! failover_in_progress )
          break;

        qpid::sys::usleep ( 1000 );
      }
    }
}



void 
FailoverSession::messageAccept ( const SequenceSet& transfers )
{
    while(1)
    {
    try
    {
    session.messageAccept ( transfers );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }

}



void 
FailoverSession::messageReject ( const SequenceSet& transfers, 
                                 uint16_t code, 
                                 const string& text
)
{
    while(1)
    {
    try
    {
    session.messageReject ( transfers, 
                            code, 
                            text 
    );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }

}



void 
FailoverSession::messageRelease ( const SequenceSet& transfers, 
                                  bool setRedelivered
)
{
    while(1)
    {
    try
    {
    session.messageRelease ( transfers,
                             setRedelivered
    );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }

}



qpid::framing::MessageAcquireResult 
FailoverSession::messageAcquire ( const SequenceSet& transfers )
{
    while(1)
    {
    try
    {
    return session.messageAcquire ( transfers );
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }

}



qpid::framing::MessageResumeResult 
FailoverSession::messageResume ( const string& destination, 
                                 const string& resumeId
)
{
    while(1)
    {
    try
    {
    return session.messageResume ( destination,
                                   resumeId
    );
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }

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
    while(1)
    {
    try
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
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }

}



void 
FailoverSession::messageCancel ( const string& destinations )
{
    while(1)
    {
    try
    {
    session.messageCancel ( destinations );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }

}



void 
FailoverSession::messageSetFlowMode ( const string& destination, 
                                      uint8_t flowMode
)
{
    while(1)
    {
    try
    {
    session.messageSetFlowMode ( destination,
                                 flowMode
    );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



void 
FailoverSession::messageFlow(const string& destination, 
                             uint8_t unit, 
                             uint32_t value)
{
    while(1)
    {
    try
    {
    session.messageFlow ( destination,
                          unit,
                          value
    );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



void 
FailoverSession::messageFlush(const string& destination)
{
    while(1)
    {
    try
    {
    session.messageFlush ( destination );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



void 
FailoverSession::messageStop(const string& destination)
{
    while(1)
    {
    try
    {
    session.messageStop ( destination );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



void 
FailoverSession::txSelect()
{
    while(1)
    {
    try
    {
    session.txSelect ( );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



void 
FailoverSession::txCommit()
{
    while(1)
    {
    try
    {
    session.txCommit ( );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



void 
FailoverSession::txRollback()
{
    while(1)
    {
    try
    {
    session.txRollback ( );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



void 
FailoverSession::dtxSelect()
{
    while(1)
    {
    try
    {
    session.dtxSelect ( );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



qpid::framing::XaResult 
FailoverSession::dtxStart(const Xid& xid, 
                          bool join, 
                          bool resume)
{
    while(1)
    {
    try
    {
    return session.dtxStart ( xid,
                              join,
                              resume
    );
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



qpid::framing::XaResult 
FailoverSession::dtxEnd(const Xid& xid, 
                        bool fail, 
                        bool suspend)
{
    while(1)
    {
    try
    {
    return session.dtxEnd ( xid,
                            fail,
                            suspend
    );
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



qpid::framing::XaResult 
FailoverSession::dtxCommit(const Xid& xid, 
                           bool onePhase)
{
    while(1)
    {
    try
    {
    return session.dtxCommit ( xid,
                               onePhase
    );
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



void 
FailoverSession::dtxForget(const Xid& xid)
{
    while(1)
    {
    try
    {
    session.dtxForget ( xid );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



qpid::framing::DtxGetTimeoutResult 
FailoverSession::dtxGetTimeout(const Xid& xid)
{
    while(1)
    {
    try
    {
    return session.dtxGetTimeout ( xid );
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



qpid::framing::XaResult 
FailoverSession::dtxPrepare(const Xid& xid)
{
    while(1)
    {
    try
    {
    return session.dtxPrepare ( xid );
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



qpid::framing::DtxRecoverResult 
FailoverSession::dtxRecover()
{
    while(1)
    {
    try
    {
    return session.dtxRecover ( );
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



qpid::framing::XaResult 
FailoverSession::dtxRollback(const Xid& xid)
{
    while(1)
    {
    try
    {
    return session.dtxRollback ( xid );
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



void 
FailoverSession::dtxSetTimeout(const Xid& xid, 
                               uint32_t timeout)
{
    while(1)
    {
    try
    {
    session.dtxSetTimeout ( xid,
                            timeout
    );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
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
    while(1)
    {
    try
    {
    session.exchangeDeclare ( exchange,
                              type,
                              alternateExchange,
                              passive,
                              durable,
                              autoDelete,
                              arguments
    );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



void 
FailoverSession::exchangeDelete(const string& exchange, 
                                bool ifUnused)
{
    while(1)
    {
    try
    {
    session.exchangeDelete ( exchange,
                             ifUnused
    );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



qpid::framing::ExchangeQueryResult 
FailoverSession::exchangeQuery(const string& name)
{
    while(1)
    {
    try
    {
    return session.exchangeQuery ( name );
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



void 
FailoverSession::exchangeBind(const string& queue, 
                              const string& exchange, 
                              const string& bindingKey, 
                              const FieldTable& arguments)
{
    while(1)
    {
    try
    {
    session.exchangeBind ( queue,
                           exchange,
                           bindingKey,
                           arguments
    );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



void 
FailoverSession::exchangeUnbind(const string& queue, 
                                const string& exchange, 
                                const string& bindingKey)
{
    while(1)
    {
    try
    {
    session.exchangeUnbind ( queue,
                             exchange,
                             bindingKey
    );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



qpid::framing::ExchangeBoundResult 
FailoverSession::exchangeBound(const string& exchange, 
                               const string& queue, 
                               const string& bindingKey, 
                               const FieldTable& arguments)
{
    while(1)
    {
    try
    {
    return session.exchangeBound ( exchange,
                                   queue,
                                   bindingKey,
                                   arguments
    );
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
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
    while(1)
    {
    try
    {
    session.queueDeclare ( queue,
                           alternateExchange,
                           passive,
                           durable,
                           exclusive,
                           autoDelete,
                           arguments
    );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



void 
FailoverSession::queueDelete(const string& queue, 
                             bool ifUnused, 
                             bool ifEmpty)
{
    while(1)
    {
    try
    {
    session.queueDelete ( queue,
                          ifUnused,
                          ifEmpty
    );
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



void 
FailoverSession::queuePurge(const string& queue)
{
    while(1)
    {
    try
    {
    session.queuePurge ( queue) ;
    return;
    }
    catch ( const std::exception& error )
    {
      if ( ! failover_in_progress )
        throw ( error );
      else
      {
        sys::Monitor::ScopedLock l(lock);
        int current_failover_count = failover_count;
        while ( current_failover_count == failover_count )
          lock.wait();
      }
    }
    }
}



qpid::framing::QueueQueryResult 
FailoverSession::queueQuery(const string& queue)
{
    while(1)
    {
      try
      {
        return session.queueQuery ( queue );
      }
      catch ( const std::exception& error )
      {
        if ( ! failover_in_progress )
          throw ( error );
        else
        {
          sys::Monitor::ScopedLock l(lock);
          int current_failover_count = failover_count;
          while ( current_failover_count == failover_count )
            lock.wait();
        }
      }
    }
}


// end Wrapped functions from Session  ---------------------------


// Get ready for a failover.
void
FailoverSession::prepareForFailover ( Connection newConnection )
{
    failover_in_progress = true;
    try
    {
        newSession = newConnection.newSession();
    }
    catch ( const std::exception& /*error*/ )
    {
        throw Exception(QPID_MSG("Can't create failover session."));
    }

    if ( failoverSubscriptionManager )
    {
        failoverSubscriptionManager->prepareForFailover ( newSession );
    }
}


void
FailoverSession::failoverStarting ( )
{
  sys::Monitor::ScopedLock l(lock);
  failover_in_progress = true;
}


void
FailoverSession::failoverComplete ( )
{
  sys::Monitor::ScopedLock l(lock);
  failover_in_progress = false;
  ++ failover_count;
  lock.notifyAll();
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


void FailoverSession::setFailoverSubscriptionManager(FailoverSubscriptionManager* fsm) {
    failoverSubscriptionManager = fsm;
}

}} // namespace qpid::client
