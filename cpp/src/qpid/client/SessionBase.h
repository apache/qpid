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
#include "qpid/client/Execution.h"
#include "qpid/client/SessionImpl.h"
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

/** \defgroup clientapi Synchronous mode of a session.
 * 
 * SYNC means that Session functions do not return until the remote
 * broker has confirmed that the command was executed. 
 * 
 * ASYNC means that the client sends commands asynchronously, Session
 * functions return immediately.
 *
 * ASYNC mode gives better performance for high-volume traffic, but
 * requires some additional caution:
 * 
 * Session functions return immediately. If the command causes an
 * exception on the broker, the exception will be thrown on a
 * <em>later</em> function call. 
 *
 * If you need to notify some extenal agent that some actions have
 * been taken (e.g. binding queues to exchanges), you must ensure that
 * the broker has completed the command. In synchronous mode this is
 * when the session method for the command returns. In asynchronous
 * mode you can call Session::sync(), to ensure that all the commands
 * are complete.
 *
 * You can freely switch between modes by calling Session::setSynchronous()
 * 
 * @see Session::sync(), Session::setSynchronous()
 */
enum SynchronousMode { SYNC=true, ASYNC=false };


/**
 * Basic session operations that are not derived from AMQP XML methods.
 */
class SessionBase
{
  public:
    /**
     * Instances of this class turn synchronous mode on for the
     * duration of their scope (and revert back to async if required
     * afterwards).
     */
    class ScopedSync
    {
        SessionBase& session;
        const bool change; 
    public:
        ScopedSync(SessionBase& s);
        ~ScopedSync();
    };


    SessionBase();
    ~SessionBase();

    /** Get the next message frame-set from the session. */
    framing::FrameSet::shared_ptr get();
    
    /** Get the session ID */
    Uuid getId() const;

    /**
     * In synchronous mode, wait for the broker's response before
     * returning. Note this gives lower throughput than asynchronous
     * mode.
     *
     * In asynchronous mode commands are sent without waiting
     * for a respose (you can use the returned Completion object
     * to wait for completion.)
     * 
     * @see SynchronousMode
     */
    void setSynchronous(SynchronousMode mode);
    void setSynchronous(bool set);
    bool isSynchronous() const;
    SynchronousMode getSynchronous() const;

    /**
     * Suspend the session, can be resumed on a different connection.
     * @see Connection::resume()
     */
    void suspend();
    
    /** Close the session */
    void close();
    
    Execution& getExecution();
    void sync();
    void flush();
    void markCompleted(const framing::SequenceNumber& id, bool cumulative, bool notifyPeer);
    void sendCompletion();
    
    typedef framing::TransferContent DefaultContent;

  protected:
    shared_ptr<SessionImpl> impl;
    framing::ProtocolVersion version;
    friend class Connection;
    SessionBase(shared_ptr<SessionImpl>);
};

}} // namespace qpid::client

#endif  /*!QPID_CLIENT_SESSIONBASE_H*/
