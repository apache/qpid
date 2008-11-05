#ifndef QPID_CLIENT_SESSIONBASE_H
#define QPID_CLIENT_SESSIONBASE_H

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

#include "qpid/SessionId.h"
#include "qpid/framing/amqp_structs.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/framing/MethodContent.h"
#include "qpid/framing/TransferContent.h"
#include "qpid/client/Completion.h"
#include "qpid/client/ConnectionImpl.h"
#include "qpid/client/Execution.h"
#include "qpid/client/SessionImpl.h"
#include "qpid/client/TypedResult.h"
#include "qpid/shared_ptr.h"
#include <string>

namespace qpid {
namespace client {

class Connection;

using std::string;
using framing::Content;
using framing::FieldTable;
using framing::MethodContent;
using framing::SequenceNumber;
using framing::SequenceSet;
using framing::SequenceNumberSet;
using qpid::SessionId;
using framing::Xid;

/** Unit of message credit: messages or bytes */
enum CreditUnit { MESSAGE_CREDIT=0, BYTE_CREDIT=1, UNLIMITED_CREDIT=0xFFFFFFFF };

/**
 * Base class for handles to an AMQP session.
 * 
 * Subclasses provide the AMQP commands for a given
 * version of the protocol.
 */
class SessionBase_0_10 {
  public:
    
    typedef framing::TransferContent DefaultContent;

    ///@internal
    SessionBase_0_10();
    ~SessionBase_0_10();

    /** Get the next message frame-set from the session. */
    framing::FrameSet::shared_ptr get();
    
    /** Get the session ID */
    SessionId getId() const;         

    /** Close the session.
     * A session is automatically closed when all handles to it are destroyed.
     */
    void close();
    
    /**
     * Synchronize the session: sync() waits until all commands issued
     * on this session so far have been completed by the broker.
     *
     * Note sync() is always synchronous, even on an AsyncSession object
     * because that's almost always what you want. You can call
     * AsyncSession::executionSync() directly in the unusual event
     * that you want to do an asynchronous sync.
     */
    void sync();

    /** Set the timeout for this session. */
    uint32_t timeout(uint32_t seconds);

    /** Suspend the session - detach it from its connection */
    void suspend();

    /** Resume a suspended session with a new connection */
    void resume(Connection);

    /** Get the channel associated with this session */
    uint16_t getChannel() const;

    Execution& getExecution();  
    void flush();
    void markCompleted(const framing::SequenceSet& ids, bool notifyPeer);
    void markCompleted(const framing::SequenceNumber& id, bool cumulative, bool notifyPeer);
    void sendCompletion();

  protected:
    boost::shared_ptr<SessionImpl> impl;
  friend class SessionBase_0_10Access;
};

}} // namespace qpid::client

#endif  /*!QPID_CLIENT_SESSIONBASE_H*/
