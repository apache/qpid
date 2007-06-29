#ifndef _posix_EventChannelConnection_h
#define _posix_EventChannelConnection_h

/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include <boost/ptr_container/ptr_deque.hpp>

#include "EventChannelThreads.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/ConnectionOutputHandler.h"
#include "qpid/sys/ConnectionInputHandler.h"
#include "qpid/sys/AtomicCount.h"
#include "qpid/framing/AMQFrame.h"

namespace qpid {
namespace sys {

class ConnectionInputHandlerFactory;

/**
 * Implements SessionContext and delegates to a SessionHandler
 * for a connection via the EventChannel.
 *@param readDescriptor file descriptor for reading.
 *@param writeDescriptor file descriptor for writing,
 * by default same as readDescriptor
 */
class EventChannelConnection : public ConnectionOutputHandler {
  public:
    EventChannelConnection(
        EventChannelThreads::shared_ptr threads, 
        ConnectionInputHandlerFactory& factory,
        int readDescriptor, 
        int writeDescriptor = 0,
        bool isTrace = false
    );

    virtual void send(qpid::framing::AMQFrame& frame);
    virtual void close();

  private:
    typedef std::deque<qpid::framing::AMQFrame> FrameQueue;
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
    std::auto_ptr<ConnectionInputHandler> handler;
    qpid::framing::Buffer in, out;
    FrameQueue writeFrames;
    bool isTrace;
    
  friend struct ScopedBusy;
};
    

}} // namespace qpid::sys



#endif  /*!_posix_EventChannelConnection_h*/
