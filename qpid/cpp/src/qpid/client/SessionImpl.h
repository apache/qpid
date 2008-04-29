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

#include "qpid/shared_ptr.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/ChannelHandler.h"
#include "qpid/framing/SessionState.h"
#include "qpid/framing/SequenceNumber.h"
#include "qpid/framing/AMQP_ClientOperations.h"
#include "qpid/framing/AMQP_ServerProxy.h"
#include "qpid/sys/Semaphore.h"
#include "qpid/sys/StateMonitor.h"

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

class SessionImpl : public framing::FrameHandler::InOutHandler,
                    public Execution,
                    private framing::AMQP_ClientOperations::SessionHandler,
                    private framing::AMQP_ClientOperations::ExecutionHandler
{
public:
    SessionImpl(shared_ptr<ConnectionImpl>, uint16_t channel, uint64_t maxFrameSize);
    ~SessionImpl();


    //NOTE: Public functions called in user thread.
    framing::FrameSet::shared_ptr get();

    const framing::Uuid getId() const;

    uint16_t getChannel() const;
    void setChannel(uint16_t channel);

    void open(uint32_t detachedLifetime);
    void close();
    void resume(shared_ptr<ConnectionImpl>);
    void suspend();

    void setSync(bool s);
    bool isSync();
    void assertOpen() const;

    Future send(const framing::AMQBody& command);
    Future send(const framing::AMQBody& command, const framing::MethodContent& content);

    Demux& getDemux();
    void markCompleted(const framing::SequenceNumber& id, bool cumulative, bool notifyPeer);
    bool isComplete(const framing::SequenceNumber& id);
    bool isCompleteUpTo(const framing::SequenceNumber& id);
    void waitForCompletion(const framing::SequenceNumber& id);
    void sendCompletion();
    void sendFlush();

    //NOTE: these are called by the network thread when the connection is closed or dies
    void connectionClosed(uint16_t code, const std::string& text);
    void connectionBroke(uint16_t code, const std::string& text);

private:
    enum ErrorType {
        OK,
        CONNECTION_CLOSE,
        SESSION_DETACH,
        EXCEPTION
    };
    enum State {
        INACTIVE,
        ATTACHING,
        ATTACHED,
        DETACHING,
        DETACHED
    };
    typedef framing::AMQP_ClientOperations::SessionHandler SessionHandler;
    typedef framing::AMQP_ClientOperations::ExecutionHandler ExecutionHandler;
    typedef sys::StateMonitor<State, DETACHED> StateMonitor;
    typedef StateMonitor::Set States;

    inline void setState(State s);
    inline void waitFor(State);

    void detach();
    
    void check() const;
    void checkOpen() const;
    void handleClosed();

    void handleIn(framing::AMQFrame& frame);
    void handleOut(framing::AMQFrame& frame);
    void proxyOut(framing::AMQFrame& frame);
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

    ErrorType error;
    int code;                   // Error code
    std::string text;           // Error text
    mutable StateMonitor state;
    mutable sys::Semaphore sendLock;
    volatile bool syncMode;
    uint32_t detachedLifetime;
    const uint64_t maxFrameSize;
    const framing::Uuid id;
    const std::string name;

    shared_ptr<ConnectionImpl> connection;
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

};

}} // namespace qpid::client

#endif
