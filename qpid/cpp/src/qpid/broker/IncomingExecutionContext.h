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
#ifndef _IncomingExecutionContext_
#define _IncomingExecutionContext_

#include "Message.h"

#include "qpid/framing/AccumulatedAck.h"
#include "qpid/framing/SequenceNumber.h"

#include <boost/intrusive_ptr.hpp>

namespace qpid {
namespace broker {

class IncomingExecutionContext
{
    typedef std::list<boost::intrusive_ptr<Message> > Messages;
    framing::Window window;
    framing::AccumulatedAck completed;
    Messages incomplete;

    bool isComplete(const framing::SequenceNumber& command);
    void check();
    void wait();
public:
    void noop();
    void flush();
    void sync();
    void sync(const framing::SequenceNumber& point);
    framing::SequenceNumber next();
    void complete(const framing::SequenceNumber& command);
    void track(boost::intrusive_ptr<Message>);

    const framing::SequenceNumber& getMark();
    framing::SequenceNumberSet getRange();

};


}}

#endif
