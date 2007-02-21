#ifndef _IncomingMessage_
#define _IncomingMessage_

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
#include <string>
#include <queue>
#include <framing/amqp_framing.h>
#include "ExceptionHolder.h"
#include "ClientMessage.h"
#include "sys/Mutex.h"
#include "sys/Condition.h"

namespace qpid {

namespace framing {
class AMQBody;
}

namespace client {
/**
 * Accumulates incoming message frames into messages.
 * Client-initiated messages (basic.get) are initiated and made
 * available to the user thread one at a time.
 * 
 * Broker initiated messages (basic.return, basic.deliver) are
 * queued for handling by the user dispatch thread.
 */
class IncomingMessage {
  public:
    typedef boost::shared_ptr<framing::AMQBody> BodyPtr;
    IncomingMessage();
    
    /** Expect a new message starting with getOk. Called in user thread.*/
    void startGet();

    /** Wait for the message to complete, return the message.
     * Called in user thread. 
     *@raises QpidError if there was an error.
     */
    bool waitGet(Message&);

    /** Wait for the next broker-initiated message. */
    Message waitDispatch();

    /** Add a frame body to the message. Called in network thread. */
    void add(BodyPtr);

    /** Shut down: all further calls to any function throw ex. */
    void shutdown();

    /** Check if shutdown */
    bool isShutdown() const;

  private:

    typedef void (IncomingMessage::* ExpectFn)(BodyPtr);
    typedef void (IncomingMessage::* EndFn)(Exception*);
    typedef std::queue<Message> MessageQueue;
    struct Guard;
  friend struct Guard;

    void reset();
    template <class T> boost::shared_ptr<T> expectCheck(BodyPtr);

    // State functions - a state machine where each state is
    // a member function that processes a frame body.
    void expectGetOk(BodyPtr);
    void expectHeader(BodyPtr);
    void expectContent(BodyPtr);
    void expectRequest(BodyPtr);

    // End functions.
    void endGet(Exception* ex = 0);
    void endRequest(Exception* ex);

    // Check for complete message.
    void checkComplete();
    
    mutable sys::Mutex lock;
    ExpectFn state;
    EndFn endFn;
    Message buildMessage;
    ExceptionHolder shutdownError;

    // For basic.get messages.
    sys::Condition getReady;
    ExceptionHolder getError;
    Message getMessage;
    enum { GETTING, GOT, EMPTY } getState;

    // For broker-initiated messages
    sys::Condition dispatchReady;
    MessageQueue dispatchQueue;
};

}}


#endif
