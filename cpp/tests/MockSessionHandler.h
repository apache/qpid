#ifndef _tests_MockSessionHandler_h
#define _tests_MockSessionHandler_h

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

#include "sys/SessionHandler.h"
#include "sys/SessionHandlerFactory.h"
#include "sys/Monitor.h"
#include "framing/ProtocolInitiation.h"

struct MockSessionHandler : public qpid::sys::SessionHandler {

    MockSessionHandler() : state(START) {}

    ~MockSessionHandler() {}
    
    void initiated(qpid::framing::ProtocolInitiation* pi) {
        qpid::sys::Monitor::ScopedLock l(monitor);
        init = *pi;
        setState(GOT_INIT);
    }

    void received(qpid::framing::AMQFrame* framep) {
        qpid::sys::Monitor::ScopedLock l(monitor);
        frame = *framep;
        setState(GOT_FRAME);
    }

    qpid::framing::ProtocolInitiation waitForProtocolInit() {        
        waitFor(GOT_INIT);
        return init;
    }

    qpid::framing::AMQFrame waitForFrame() {        
        waitFor(GOT_FRAME);
        return frame;
    }

    void waitForClosed() {
        waitFor(CLOSED);
    }
    
    void closed() {
        qpid::sys::Monitor::ScopedLock l(monitor);
        setState(CLOSED);
    }

    void idleOut() {}
    void idleIn() {}

  private:
    typedef enum { START, GOT_INIT, GOT_FRAME, CLOSED } State;

    void setState(State s) {
        state = s;
        monitor.notify();
    }
    
    void waitFor(State s) {
        qpid::sys::Monitor::ScopedLock l(monitor);
        qpid::sys::Time deadline = qpid::sys::now() + 10*qpid::sys::TIME_SEC; 
        while (state != s)
            CPPUNIT_ASSERT(monitor.wait(deadline));
    }

    qpid::sys::Monitor  monitor;
    State state;
    qpid::framing::ProtocolInitiation init;
    qpid::framing::AMQFrame frame;
};


struct MockSessionHandlerFactory : public qpid::sys::SessionHandlerFactory {
    MockSessionHandlerFactory() : handler(0) {}

    qpid::sys::SessionHandler* create(qpid::sys::SessionContext*) {
        qpid::sys::Monitor::ScopedLock lock(monitor);
        handler = new MockSessionHandler();
        monitor.notifyAll();
        return handler;
    }

    void waitForHandler() {
        qpid::sys::Monitor::ScopedLock lock(monitor);
        qpid::sys::Time deadline =
            qpid::sys::now() + 500 * qpid::sys::TIME_SEC;
        while (handler == 0)
            CPPUNIT_ASSERT(monitor.wait(deadline));
    }
    
    MockSessionHandler* handler;
    qpid::sys::Monitor monitor;
};



#endif  /*!_tests_MockSessionHandler_h*/
