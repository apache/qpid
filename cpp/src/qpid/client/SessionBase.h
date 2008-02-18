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

#include "qpid/framing/Uuid.h"
#include "qpid/framing/amqp_structs.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/framing/MethodContent.h"
#include "qpid/framing/TransferContent.h"
#include "qpid/client/Completion.h"
#include "qpid/client/ConnectionImpl.h"
#include "qpid/client/Response.h"
#include "qpid/client/SessionCore.h"
#include "qpid/client/TypedResult.h"
#include "qpid/shared_ptr.h"
#include <string>

namespace qpid {
namespace client {

using std::string;
using framing::Content;
using framing::FieldTable;
using framing::MethodContent;
using framing::SequenceNumberSet;
using framing::Uuid;

/**
 * Basic session operations that are not derived from AMQP XML methods.
 */
class SessionBase
{
  public:
    SessionBase();
    ~SessionBase();

    /** Get the next message frame-set from the session. */
    framing::FrameSet::shared_ptr get();
    
    /** Get the session ID */
    Uuid getId() const;

    /**
     * In synchronous mode, the session sets the sync bit on every
     * command and waits for the broker's response before returning.
     * Note this gives lower throughput than non-synchronous mode.
     *
     * In non-synchronous mode commands are sent without waiting
     * for a respose (you can use the returned Completion object
     * to wait for completion.)
     * 
     *@param if true set the session to synchronous mode, else
     * set it to non-synchronous mode.
     */
    void setSynchronous(bool isSync);

    bool isSynchronous() const;

    /**
     * Suspend the session, can be resumed on a different connection.
     * @see Connection::resume()
     */
    void suspend();
    
    /** Close the session */
    void close();

    /** Synchronize with the broker. Wait for all commands issued so far in
     * the session to complete.
     */
    void sync();
    
    Execution& getExecution();
    
    typedef framing::TransferContent DefaultContent;

  protected:
    shared_ptr<SessionCore> impl;
    framing::ProtocolVersion version;
    friend class Connection;
    SessionBase(shared_ptr<SessionCore>);
};

}} // namespace qpid::client

#endif  /*!QPID_CLIENT_SESSIONBASE_H*/
