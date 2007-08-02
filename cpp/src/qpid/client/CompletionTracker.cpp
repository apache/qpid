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

#include "CompletionTracker.h"

using qpid::client::CompletionTracker;
using namespace qpid::framing;
using namespace boost;

CompletionTracker::CompletionTracker() {}
CompletionTracker::CompletionTracker(const SequenceNumber& m) : mark(m) {}


void CompletionTracker::completed(const SequenceNumber& _mark)
{   
    sys::Mutex::ScopedLock l(lock);
    mark = _mark;
    while (!listeners.empty() && !(listeners.front().first > mark)) {
        Listener f(listeners.front().second);
        {
            sys::Mutex::ScopedUnlock u(lock);
            f();
        }
        listeners.pop();
    }
}

void CompletionTracker::listen(const SequenceNumber& point, Listener listener)
{
    if (!add(point, listener)) {
        listener();
    }
}

bool CompletionTracker::add(const SequenceNumber& point, Listener listener)
{
    sys::Mutex::ScopedLock l(lock);
    if (point < mark) {
        return false;
    } else {
        listeners.push(make_pair(point, listener));
        return true;
    }
}


