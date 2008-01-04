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
#include "LocalQueue.h"
#include "qpid/Exception.h"
#include "qpid/framing/FrameSet.h"
#include "qpid/framing/reply_exceptions.h"

namespace qpid {
namespace client {

using namespace framing;

LocalQueue::LocalQueue(AckPolicy a) : autoAck(a) {}
LocalQueue::~LocalQueue() {}

Message LocalQueue::pop() {
    if (!queue)
        throw ClosedException();
    FrameSet::shared_ptr content = queue->pop();
    if (content->isA<MessageTransferBody>()) {
        Message m(*content, session);
        autoAck.ack(m);
        return m;
    }
    else
        throw CommandInvalidException(
            QPID_MSG("Unexpected method: " << content->getMethod()));
}

void LocalQueue::setAckPolicy(AckPolicy a) { autoAck=a; }

bool LocalQueue::empty() 
{ 
    if (!queue)
        throw ClosedException();
    return queue->isEmpty(); 
}

}} // namespace qpid::client
