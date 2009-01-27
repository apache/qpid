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

#ifndef _SessionImpl_
#define _SessionImpl_

#include "Demux.h"
#include "Execution.h"
#include "Results.h"

#include "qpid/SessionId.h"
#include "qpid/SessionState.h"
#include "boost/shared_ptr.hpp"
#include "boost/weak_ptr.hpp"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/ChannelHandler.h"
#include "qpid/framing/SequenceNumber.h"
#include "qpid/framing/AMQP_ClientOperations.h"
#include "qpid/framing/AMQP_ServerProxy.h"
#include "qpid/sys/Semaphore.h"
#include "qpid/sys/StateMonitor.h"
#include "qpid/sys/ExceptionHolder.h"

#include <boost/optional.hpp>

namespace qpid {

namespace framing {

class FrameSet;
class MethodContent;
class SequenceSet;

}

namespace client {

class Future;
class ConnectionImpl;
class SessionHandler;

///@internal
class SessionImpl : public framing::FrameHandler::InOutHandler,
                    public Execution,
                    private framing::AMQP_ClientOperations::SessionHandler,
                    private framing::AMQP_ClientOperations::ExecutionHandler,
                    private framing::AMQP_ClientOperations::MessageHandler
{
public:
    SessionImpl(const std::string& name, shared_ptr<ConnectionImpl>);
    ~SessionImpl();


    //NOTE: Public functions called in user thread.
    framing::FrameSet::shared_ptr get();

    const SessionId getId() const;

    uint16_t getChannel() const;
    void setChannel(uint16_t channel);

    void open(uint32_t detachedLifetime);
    void close();
    void resume(shared_ptr<ConnectionImpl>);
    void suspend();

    void assertOpen() const;

    Future send(const framing::AMQBody& command);
    Future send(const framing::AMQBody& command, const framing::MethodContent& content);
    Future send(const framing::AMQBody& command, const framing::FrameSet& content);

    Demux& getDemux();
    void markCompleted(const framing::SequenceNumber& id, bool cumulative, bool notifyPeer);
    void markCompleted(const framing::SequenceSet& ids, bool notifyPeer);
    bool isComplete(const framing::SequenceNumber& id);
    bool isCompleteUpTo(const framing::SequenceNumber& id);
    void waitForCompletion(const framing::SequenceNumber& id);
    void sendCompletion();
    void sendFlush();

    void setException(const sys::ExceptionHolder&);
    
    //NOTE: these are called by the network thread when the connection is closed or dies
    void connectionClosed(uint16_t code, const std::string& text);
    void connectionBroke(const std::string& text);

    /** Set timeout in seconds, returns actual timeout allowed by broker */ 
    uint32_t setTimeout(uint32_t requestedSeconds);

    /** Get timeout in seconds. */
    uint32_t getTimeout() const;

    /** Make this session use a weak_ptr to the ConnectionImpl.
     * Used for sessions created by the ConnectionImpl itself.
     */
    void setWeakPtr(bool weak=true);

private:
    enum State {
        INACTIVE,
        ATTACHING,
        ATTACHED,
        DETACHING,
        DETACHED
    };
    typedef framing::AMQP_ClientOperations::SessionHandler SessionHandler;
    typedef framing::AMQP_ClientOperations::ExecutionHandler ExecutionHandler;
    typedef framing::AMQP_ClientOperations::MessageHandler MessageHandler;
    typedef sys::StateMonitor<State, DETACHED> StateMonitor;
    typedef StateMonitor::Set States;

    inline void setState(State s);
    inline void waitFor(State);

    void setExceptionLH(const sys::ExceptionHolder&);      // LH = lock held when called.
    void detach();
    
    void check() const;
    void checkOpen() const;
    void handleClosed();

    void handleIn(framing::AMQFrame& frame);
    void handleOut(framing::AMQFrame& frame);
    void handleContentOut(framing::AMQFrame& frame);
    /**
     * Sends session controls. This case is treated slightly
     * differently than command frames sent by the application via
     * handleOut(); session controlsare not subject to bounds checking
     * on the outgoing frame queue.
     */
    void proxyOut(framing::AMQFrame& frame);
    void sendFrame(framing::AMQFrame& frame, bool canBlock);
    void deliver(framing::AMQFrame& frame);

    Future sendCommand(const framing::AMQBody&, const framing::MethodContent* = 0);
    void sendContent(const framing::MethodContent&);
    void waitForCompletionImpl(const framing::SequenceNumber& id);
    
    void sendCompletionImpl();

    // Note: Following methods are called by network thread in
    // response to session controls from the broker
    void attach(const std::string& name, bool force);    
    void attached(const std::string& name);    
    void detach(const std::string& name);    
    void detached(const std::string& name, uint8_t detachCode);
    void requestTimeout(uint32_t timeout);    
    void timeout(uint32_t timeout);    
    void commandPoint(const framing::SequenceNumber& commandId, uint64_t commandOffset);    
    void expected(const framing::SequenceSet& commands, const framing::Array& fragments);    
    void confirmed(const framing::SequenceSet& commands, const framing::Array& fragments);    
    void completed(const framing::SequenceSet& commands, bool timelyReply);    
    void knownCompleted(const framing::SequenceSet& commands);    
    void flush(bool expected, bool confirmed, bool completed);    
    void gap(const framing::SequenceSet& commands);

    // Note: Following methods are called by network thread in
    // response to execution commands from the broker
    void sync();    
    void result(const framing::SequenceNumber& commandId, const std::string& value);    
    void exception(uint16_t errorCode,
                   const framing::SequenceNumber& commandId,
                   uint8_t classCode,
                   uint8_t commandCode,
                   uint8_t fieldIndex,
                   const std::string& description,
                   const framing::FieldTable& errorInfo);
                   
    // Note: Following methods are called by network thread in
    // response to message commands from the broker
    // EXCEPT Message.Transfer
    void accept(const qpid::framing::SequenceSet&);
    void reject(const qpid::framing::SequenceSet&, uint16_t, const std::string&);
    void release(const qpid::framing::SequenceSet&, bool);
    qpid::framing::MessageResumeResult resume(const std::string&, const std::string&);
    void setFlowMode(const std::string&, uint8_t);
    void flow(const std::string&, uint8_t, uint32_t);
    void stop(const std::string&);


    sys::ExceptionHolder exceptionHolder;
    mutable StateMonitor state;
    mutable sys::Semaphore sendLock;
    uint32_t detachedLifetime;
    const uint64_t maxFrameSize;
    const SessionId id;

    shared_ptr<ConnectionImpl> connection();
    shared_ptr<ConnectionImpl> connectionShared;
    boost::weak_ptr<ConnectionImpl> connectionWeak;
    bool weakPtr;

    framing::FrameHandler::MemFunRef<SessionImpl, &SessionImpl::proxyOut> ioHandler;
    framing::ChannelHandler channel;
    framing::AMQP_ServerProxy::Session proxy;

    Results results;
    Demux demux;
    framing::FrameSet::shared_ptr arriving;

    framing::SequenceSet incompleteIn;//incoming commands that are as yet incomplete
    framing::SequenceSet completedIn;//incoming commands that are have completed
    framing::SequenceSet incompleteOut;//outgoing commands not yet known to be complete
    framing::SequenceSet completedOut;//outgoing commands that we know to be completed
    framing::SequenceNumber nextIn;
    framing::SequenceNumber nextOut;

    SessionState sessionState;

    // Only keep track of message credit 
    sys::Semaphore* sendMsgCredit;

  friend class client::SessionHandler;
};

}} // namespace qpid::client

#endif
