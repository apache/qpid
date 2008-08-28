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

#include "IncomingExecutionContext.h"
#include "qpid/Exception.h"

namespace qpid {
namespace broker {

using boost::intrusive_ptr;
using qpid::framing::AccumulatedAck;
using qpid::framing::SequenceNumber;
using qpid::framing::SequenceNumberSet;

void IncomingExecutionContext::noop()
{
    complete(next());
}

void IncomingExecutionContext::flush()
{
    for (Messages::iterator i = incomplete.begin(); i != incomplete.end(); ) {
        if ((*i)->isEnqueueComplete()) {
            complete((*i)->getCommandId());
            i = incomplete.erase(i);
        } else {
            i++;
        }
    }
    window.lwm = completed.mark;
}

void IncomingExecutionContext::sync()
{
    while (completed.mark < window.hwm) {
        wait();
    }    
}

void IncomingExecutionContext::sync(const SequenceNumber& point)
{
    while (!isComplete(point)) {
        wait();
    }    
}

/**
 * Every call to next() should be followed be either a call to
 * complete() - in the case of commands, which are always synchronous
 * - or track() - in the case of messages which may be asynchronously
 * stored.
 */
SequenceNumber IncomingExecutionContext::next()
{
    return ++window.hwm;
}

void IncomingExecutionContext::complete(const SequenceNumber& command)
{
    completed.update(command, command);
}

void IncomingExecutionContext::track(intrusive_ptr<Message> msg)
{
    if (msg->isEnqueueComplete()) {
        complete(msg->getCommandId());
    } else {
        incomplete.push_back(msg);
    }
}

bool IncomingExecutionContext::isComplete(const SequenceNumber& command)
{
    if (command > window.hwm) {
        throw Exception(QPID_MSG("Bad sync request: point exceeds last command received [" 
                                 << command.getValue() << " > " << window.hwm.getValue() << "]"));
    }

    return completed.covers(command);
}


const SequenceNumber& IncomingExecutionContext::getMark()
{
    return completed.mark;
}

SequenceNumberSet IncomingExecutionContext::getRange()
{
    SequenceNumberSet range;
    completed.collectRanges(range);
    return range;
}

void IncomingExecutionContext::wait()
{
    check();
	// for IO flush on the store
    for (Messages::iterator i = incomplete.begin(); i != incomplete.end(); i++) {
        (*i)->flush();
    }
    incomplete.front()->waitForEnqueueComplete();
    flush();
}

/**
 * This is a check of internal state consistency.
 */
void IncomingExecutionContext::check()
{
    if (incomplete.empty()) {
        if (window.hwm != completed.mark) {
            //can only happen if there is a call to next() without a
            //corresponding call to completed() or track() - or if
            //there is a logical error in flush() or
            //AccumulatedAck::update()
            throw Exception(QPID_MSG("Completion tracking error: window.hwm=" 
                                     << window.hwm.getValue() << ", completed.mark="
                                     << completed.mark.getValue()));
        }
    }
}

}}

