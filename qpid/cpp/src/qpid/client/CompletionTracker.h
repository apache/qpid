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

#include <queue>
#include <boost/function.hpp>
#include "qpid/framing/amqp_framing.h"
#include "qpid/framing/SequenceNumber.h"
#include "qpid/sys/Mutex.h"

#ifndef _CompletionTracker_
#define _CompletionTracker_

namespace qpid {
namespace client {

class CompletionTracker
{
public:
    typedef boost::function<void()> Listener;    

    CompletionTracker();
    CompletionTracker(const framing::SequenceNumber& mark);
    void completed(const framing::SequenceNumber& mark);
    void listen(const framing::SequenceNumber& point, Listener l);

private:
    sys::Mutex lock;
    framing::SequenceNumber mark;
    std::queue< std::pair<framing::SequenceNumber, Listener> > listeners;

    bool add(const framing::SequenceNumber& point, Listener l);
};

}
}


#endif
