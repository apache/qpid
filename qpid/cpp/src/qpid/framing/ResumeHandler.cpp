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
 * "AS IS" BASIS, WITHOUT WARRANTIE4bS OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
#include "ResumeHandler.h"
#include "qpid/framing/reply_exceptions.h"

#include <boost/bind.hpp>

#include <algorithm>

namespace qpid {
namespace framing {

void  ResumeHandler::ackReceived(SequenceNumber acked) {
    if (lastSent < acked)
        throw InvalidArgumentException("Invalid sequence number in ack");
    size_t keep = lastSent - acked;
    if (keep < unacked.size()) 
        unacked.erase(unacked.begin(), unacked.end()-keep);
}

void ResumeHandler::resend() {
    std::for_each(unacked.begin(), unacked.end(),
                  boost::bind(&FrameHandler::handle,out->next, _1));
}

void ResumeHandler::handleIn(AMQFrame& f) {
    ++lastReceived;
    in.next->handle(f);
}

void ResumeHandler::handleOut(AMQFrame& f) {
    ++lastSent;
    unacked.push_back(f);
    out.next->handle(f);
}


}} // namespace qpid::framing
