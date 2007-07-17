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
#include "GetAdapter.h"
#include "qpid/framing/MethodContext.h"

using namespace qpid::broker;
using qpid::framing::ChannelAdapter;
using qpid::framing::RequestId;
using qpid::framing::MethodContext;

GetAdapter::GetAdapter(ChannelAdapter& a, Queue::shared_ptr q, const std::string d, uint32_t f) 
    : adapter(a), queue(q), destination(d), framesize(f) {}

RequestId GetAdapter::getNextDeliveryTag()
{
    return adapter.getNextSendRequestId();
}

void GetAdapter::deliver(Message::shared_ptr& msg, framing::RequestId deliveryTag)
{
    msg->sendGetOk(MethodContext(&adapter, msg->getRespondTo()), destination,
                   queue->getMessageCount(), deliveryTag, framesize);
}
