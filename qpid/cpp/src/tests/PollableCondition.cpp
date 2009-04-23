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

#include "test_tools.h"
#include "unit_test.h"
#include "qpid/sys/Poller.h"
#include "qpid/sys/PollableCondition.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Time.h"
#include "qpid/sys/Thread.h"
#include <boost/bind.hpp>


QPID_AUTO_TEST_SUITE(PollableConditionTest)

using namespace qpid::sys;

const Duration SHORT = TIME_SEC/100;
const Duration LONG = TIME_SEC/10;

class  Callback {
  public:    
    enum Action { NONE, DISARM, CLEAR, DISARM_CLEAR };

    Callback() : count(), action(NONE) {}

    void call(PollableCondition& pc) {
        Mutex::ScopedLock l(lock);
        ++count;
        switch(action) {
          case NONE: break; 
          case DISARM:  pc.disarm(); break;
          case CLEAR: pc.clear(); break;
          case DISARM_CLEAR: pc.disarm(); pc.clear(); break;
        }
        action = NONE;
        lock.notify();
    }

    bool isCalling() { Mutex::ScopedLock l(lock); return wait(LONG); }

    bool isNotCalling() { Mutex::ScopedLock l(lock); return !wait(SHORT); }

    bool nextCall(Action a=NONE) {
        Mutex::ScopedLock l(lock);
        action = a;
        return wait(LONG);
    }
    
  private:
    bool wait(Duration timeout) {        
        int n = count;
        AbsTime deadline(now(), timeout);
        while (n == count && lock.wait(deadline))
               ;
        return n != count;
    }

    Monitor lock;
    int count;
    Action action;
};

QPID_AUTO_TEST_CASE(testPollableCondition) {
    boost::shared_ptr<Poller> poller(new Poller());
    Callback callback;
    PollableCondition pc(boost::bind(&Callback::call, &callback, _1), poller);

    Thread runner = Thread(*poller);
    
    BOOST_CHECK(callback.isNotCalling()); // condition is not set or armed.

    pc.rearm();                          
    BOOST_CHECK(callback.isNotCalling()); // Armed but not set

    pc.set();
    BOOST_CHECK(callback.isCalling()); // Armed and set.
    BOOST_CHECK(callback.isCalling()); // Still armed and set.

    callback.nextCall(Callback::DISARM);
    BOOST_CHECK(callback.isNotCalling()); // set but not armed

    pc.rearm();
    BOOST_CHECK(callback.isCalling()); // Armed and set.
    callback.nextCall(Callback::CLEAR);    
    BOOST_CHECK(callback.isNotCalling()); // armed but not set

    pc.set();
    BOOST_CHECK(callback.isCalling()); // Armed and set.
    callback.nextCall(Callback::DISARM_CLEAR);    
    BOOST_CHECK(callback.isNotCalling()); // not armed or set.

    poller->shutdown();
    runner.join();
}

QPID_AUTO_TEST_SUITE_END()


