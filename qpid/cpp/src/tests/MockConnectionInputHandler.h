#ifndef _tests_MockConnectionInputHandler_h
#define _tests_MockConnectionInputHandler_h

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

#include "qpid/sys/ConnectionInputHandler.h"
#include "qpid/sys/ConnectionInputHandlerFactory.h"
#include "qpid/sys/Monitor.h"

struct MockConnectionInputHandler : public qpid::sys::ConnectionInputHandler {

    MockConnectionInputHandler() : state(START) {}

    ~MockConnectionInputHandler() {}
    
    void received(qpid::framing::AMQFrame* framep) {
        qpid::sys::Monitor::ScopedLock l(monitor);
        frame = *framep;
        setState(GOT_FRAME);
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
    typedef enum { START, GOT_FRAME, CLOSED } State;

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
    qpid::framing::AMQFrame frame;
};


struct MockConnectionInputHandlerFactory : public qpid::sys::ConnectionInputHandlerFactory {
    MockConnectionInputHandlerFactory() : handler(0) {}

    qpid::sys::ConnectionInputHandler* create(qpid::sys::ConnectionOutputHandler*) {
        qpid::sys::Monitor::ScopedLock lock(monitor);
        handler = new MockConnectionInputHandler();
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
    
    MockConnectionInputHandler* handler;
    qpid::sys::Monitor monitor;
};



#endif  /*!_tests_MockConnectionInputHandler_h*/
