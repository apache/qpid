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

#include <list>
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
    typedef boost::function<void()> CompletionListener;    
    typedef boost::function<void(const std::string&)> ResultListener;

    CompletionTracker();
    CompletionTracker(const framing::SequenceNumber& mark);
    void completed(const framing::SequenceNumber& mark);
    void received(const framing::SequenceNumber& id, const std::string& result);
    void listenForCompletion(const framing::SequenceNumber& point, CompletionListener l);
    void listenForResult(const framing::SequenceNumber& point, ResultListener l);
    void close();

private:
    struct Record 
    {
        framing::SequenceNumber id; 
        CompletionListener f;
        ResultListener g;        

        Record(const framing::SequenceNumber& _id, CompletionListener l) : id(_id), f(l) {}
        Record(const framing::SequenceNumber& _id, ResultListener l) : id(_id), g(l) {}
        void completed();
        void received(const std::string& result);

    };

    typedef std::list<Record> Listeners;
    bool closed;
    
    sys::Mutex lock;
    framing::SequenceNumber mark;
    Listeners listeners;

    bool add(const Record& r);
    Listeners::iterator seek(const framing::SequenceNumber&);
};

}
}


#endif
