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
#include <algorithm>

using qpid::client::CompletionTracker;
using namespace qpid::framing;
using namespace boost;

namespace 
{
const std::string empty;
}

CompletionTracker::CompletionTracker() : closed(false) {}
CompletionTracker::CompletionTracker(const SequenceNumber& m) : mark(m) {}

void CompletionTracker::close()
{   
    sys::Mutex::ScopedLock l(lock);
    closed=true;
    while (!listeners.empty()) {
        Record r(listeners.front());
        {
            sys::Mutex::ScopedUnlock u(lock);
            r.completed();
        }
        listeners.pop_front();
    }
}


void CompletionTracker::completed(const SequenceNumber& _mark)
{   
    sys::Mutex::ScopedLock l(lock);
    mark = _mark;
    while (!listeners.empty() && !(listeners.front().id > mark)) {
        Record r(listeners.front());
        listeners.pop_front();
        {
            sys::Mutex::ScopedUnlock u(lock);
            r.completed();
        }
    }
}

void CompletionTracker::received(const SequenceNumber& id, const std::string& result)
{
    sys::Mutex::ScopedLock l(lock);
    Listeners::iterator i = seek(id);
    if (i != listeners.end() && i->id == id) {
        i->received(result);
        listeners.erase(i);
    }
}

void CompletionTracker::listenForCompletion(const SequenceNumber& point, CompletionListener listener)
{
    if (!add(Record(point, listener))) {
        listener();
    }
}

void CompletionTracker::listenForResult(const SequenceNumber& point, ResultListener listener)
{
    if (!add(Record(point, listener))) {
        listener(empty);
    }
}

bool CompletionTracker::add(const Record& record)
{
    sys::Mutex::ScopedLock l(lock);
    if (record.id < mark || closed) {
        return false;
    } else {
        //insert at the correct position
        Listeners::iterator i = seek(record.id);
        if (i == listeners.end()) i = listeners.begin();
        listeners.insert(i, record);
        return true;
    }
}

CompletionTracker::Listeners::iterator CompletionTracker::seek(const framing::SequenceNumber& point)
{
    Listeners::iterator i = listeners.begin(); 
    while (i != listeners.end() && i->id < point) i++;
    return i;
}


void CompletionTracker::Record::completed() 
{ 
    if (f)     f(); 
    else if(g) g(empty);//won't get a result if command is now complete
}

void CompletionTracker::Record::received(const std::string& result) 
{ 
    if (g) g(result);
}
