#ifndef QPID_CLUSTER_EXP_TICKER_H
#define QPID_CLUSTER_EXP_TICKER_H

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


#include "qpid/RefCounted.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/PollableCondition.h"
#include "qpid/sys/Time.h"
#include "qpid/sys/Timer.h"
#include <boost/function.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/intrusive_ptr.hpp>
#include <vector>

namespace qpid {

namespace sys {
class Poller;
}

namespace cluster {

/**
 * Generate regular calls to QueueContext::tick.
 * Work of caling tick is not done in the timer thread.
 * The timer task triggers a PollableCondition, which calls the ticks.
 *
 * THREAD SAFE: add/remove are called in connection or deliver
 * threads, fire is called in timer thread and tick is called in the
 * IO thread for the PollableCondition.
 */
class Ticker : public  sys::TimerTask
{
  public:
    struct Tickable : public RefCounted {
        virtual ~Tickable();
        virtual void tick() = 0;
    };

    Ticker(sys::Duration tick, sys::Timer&, boost::shared_ptr<sys::Poller>);

    void add(boost::intrusive_ptr<Tickable>);
    void remove(boost::intrusive_ptr<Tickable>);

  private:
    typedef std::vector<boost::intrusive_ptr<Tickable> > Tickables;

    void fire();                // Called in timer thread.
    void dispatch(sys::PollableCondition&); // Called in IO thread

    sys::Timer& timer;
    sys::PollableCondition condition;

    sys::Mutex lock;
    Tickables tickables;

    // Only accessed in the condition IO thread so no lock needed.
    // This is a member to keep memory allocated by the vector and
    // avoid re-allocation each time
    Tickables working;
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_EXP_TICKER_H*/
