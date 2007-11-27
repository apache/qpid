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

#ifndef _SessionCore_
#define _SessionCore_

#include "qpid/shared_ptr.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/ChannelHandler.h"
#include "qpid/framing/SessionState.h"
#include "qpid/framing/SequenceNumber.h"
#include "qpid/framing/AMQP_ClientOperations.h"
#include "qpid/framing/AMQP_ServerProxy.h"
#include "qpid/sys/StateMonitor.h"
#include "ExecutionHandler.h"

#include <boost/optional.hpp>

namespace qpid {
namespace framing {
class FrameSet;
class MethodContent;
class SequenceNumberSet;
}

namespace client {

class Future;
class ConnectionImpl;

/**
 * Session implementation, sets up handler chains.
 * Attaches to a SessionHandler when active, detaches
 * when closed.
 */
class SessionCore : public framing::FrameHandler::InOutHandler,
                    private framing::AMQP_ClientOperations::SessionHandler
{
  public:
    SessionCore(shared_ptr<ConnectionImpl>, uint16_t channel, uint64_t maxFrameSize);
    ~SessionCore();

    framing::FrameSet::shared_ptr get();
    const framing::Uuid getId() const;
    uint16_t getChannel() const { return channel; }
    void assertOpen() const;

    // NOTE: Public functions called in user thread.
    void open(uint32_t detachedLifetime);
    void close();
    void resume(shared_ptr<ConnectionImpl>);
    void suspend();
    void setChannel(uint16_t channel);

    void setSync(bool s);
    bool isSync();
    ExecutionHandler& getExecution();

    Future send(const framing::AMQBody& command);

    Future send(const framing::AMQBody& command, const framing::MethodContent& content);

    void connectionClosed(uint16_t code, const std::string& text);
    void connectionBroke(uint16_t code, const std::string& text);

  private:
    enum State {
        OPENING,
        RESUMING,
        OPEN,
        CLOSING,
        SUSPENDING,
        SUSPENDED,
        CLOSED
    };
    typedef framing::AMQP_ClientOperations::SessionHandler SessionHandler;
    typedef sys::StateMonitor<State, CLOSED> StateMonitor;
    typedef StateMonitor::Set States;

    inline void invariant() const;
    inline void setState(State s);
    inline void waitFor(State);
    void doClose(int code, const std::string& text);
    void doSuspend(int code, const std::string& text);
    
    /** If there is an error, throw the exception */
    void check(bool condition, int code, const std::string& text) const;
    /** Throw if *error */
    void check() const;

    void handleIn(framing::AMQFrame& frame);
    void handleOut(framing::AMQFrame& frame);

    // Private functions are called by broker in network thread.
    void attached(const framing::Uuid& sessionId, uint32_t detachedLifetime);
    void flow(bool active);
    void flowOk(bool active);
    void detached();
    void ack(uint32_t cumulativeSeenMark,
             const framing::SequenceNumberSet& seenFrameSet);
    void highWaterMark(uint32_t lastSentMark);
    void solicitAck();
    void closed(uint16_t code, const std::string& text);

    void attaching(shared_ptr<ConnectionImpl>);
    void detach(int code, const std::string& text);
    void checkOpen() const;

    int code;                   // Error code
    std::string text;           // Error text
    boost::optional<framing::SessionState> session;
    shared_ptr<ConnectionImpl> connection;
    ExecutionHandler l3;
    volatile bool sync;
    framing::ChannelHandler channel;
    framing::AMQP_ServerProxy::Session proxy;
    mutable StateMonitor state;
    uint32_t detachedLifetime;
};

}} // namespace qpid::client

#endif
