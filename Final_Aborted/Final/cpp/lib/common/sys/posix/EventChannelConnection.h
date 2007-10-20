#ifndef _posix_EventChannelConnection_h
#define _posix_EventChannelConnection_h

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

#include <boost/ptr_container/ptr_deque.hpp>

#include "EventChannelThreads.h"
#include "sys/Monitor.h"
#include "sys/SessionContext.h"
#include "sys/SessionHandler.h"
#include "sys/AtomicCount.h"
#include "framing/AMQFrame.h"

namespace qpid {
namespace sys {

class SessionHandlerFactory;

/**
 * Implements SessionContext and delegates to a SessionHandler
 * for a connection via the EventChannel.
 *@param readDescriptor file descriptor for reading.
 *@param writeDescriptor file descriptor for writing,
 * by default same as readDescriptor
 */
class EventChannelConnection : public SessionContext {
  public:
    EventChannelConnection(
        EventChannelThreads::shared_ptr threads, 
        SessionHandlerFactory& factory,
        int readDescriptor, 
        int writeDescriptor = 0,
        bool isTrace = false
    );

    // TODO aconway 2006-11-30: SessionContext::send should take auto_ptr
    virtual void send(qpid::framing::AMQFrame* frame) {
        send(std::auto_ptr<qpid::framing::AMQFrame>(frame));
    }
            
    virtual void send(std::auto_ptr<qpid::framing::AMQFrame> frame);

    virtual void close();

  private:
    typedef boost::ptr_deque<qpid::framing::AMQFrame> FrameQueue;
    typedef void (EventChannelConnection::*MemberFnPtr)();
    struct ScopedBusy;

    void startWrite();
    void endWrite();
    void startRead();
    void endInitRead();
    void endRead();
    void closeNoThrow();
    void closeOnException(MemberFnPtr);
    bool shouldContinue(bool& flag);

    static const size_t bufferSize;

    Monitor monitor;

    int readFd, writeFd;
    ReadEvent readEvent;
    WriteEvent writeEvent;
    Event::Callback readCallback;
    bool isWriting;
    bool isClosed;
    AtomicCount busyThreads;

    EventChannelThreads::shared_ptr threads;
    std::auto_ptr<SessionHandler> handler;
    qpid::framing::Buffer in, out;
    FrameQueue writeFrames;
    bool isTrace;
    
  friend struct ScopedBusy;
};
    

}} // namespace qpid::sys



#endif  /*!_posix_EventChannelConnection_h*/
