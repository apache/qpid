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
#ifndef _GetAdapter_
#define _GetAdapter_

#include "BrokerQueue.h"
#include "DeliveryAdapter.h"
#include "qpid/framing/ChannelAdapter.h"

namespace qpid {
namespace broker {

    class GetAdapter : public DeliveryAdapter
    {
        framing::ChannelAdapter& adapter;
        Queue::shared_ptr queue;
        const std::string destination;
        const uint32_t framesize;
    public:
        GetAdapter(framing::ChannelAdapter& adapter, Queue::shared_ptr queue, const std::string destination, uint32_t framesize);
        ~GetAdapter(){}
        framing::RequestId getNextDeliveryTag();
        void deliver(Message::shared_ptr& msg, framing::RequestId tag);
    };

}}


#endif
