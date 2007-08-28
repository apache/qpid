#ifndef _broker_MessageAdapter_h
#define _broker_MessageAdapter_h

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

#include <string>
#include "qpid/framing/BasicPublishBody.h"
#include "qpid/framing/BasicHeaderProperties.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/FrameSet.h"
#include "qpid/framing/DeliveryProperties.h"
#include "qpid/framing/MessageProperties.h"
#include "qpid/framing/MessageTransferBody.h"

namespace qpid {	
namespace broker {

struct MessageAdapter
{
    virtual ~MessageAdapter() {}

    virtual const std::string& getRoutingKey(const framing::FrameSet& f) = 0;
    virtual const std::string& getExchange(const framing::FrameSet& f) = 0;
    virtual bool isImmediate(const framing::FrameSet& f) = 0;
    virtual const framing::FieldTable& getApplicationHeaders(const framing::FrameSet& f) = 0;
    virtual bool isPersistent(const framing::FrameSet& f) = 0;
};

struct PublishAdapter : MessageAdapter
{
    const std::string& getRoutingKey(const framing::FrameSet& f)
    {
        return f.as<framing::BasicPublishBody>()->getRoutingKey();
    }

    const std::string& getExchange(const framing::FrameSet& f)
    {
        return f.as<framing::BasicPublishBody>()->getExchange();
    }

    bool isImmediate(const framing::FrameSet& f)
    {
        return f.as<framing::BasicPublishBody>()->getImmediate();
    }

    const framing::FieldTable& getApplicationHeaders(const framing::FrameSet& f)
    {
        return f.getHeaders()->get<framing::BasicHeaderProperties>()->getHeaders();
    }

    bool isPersistent(const framing::FrameSet& f)
    {
        return f.getHeaders()->get<framing::BasicHeaderProperties>()->getDeliveryMode() == 2;
    }
};

struct TransferAdapter : MessageAdapter
{
    const std::string& getRoutingKey(const framing::FrameSet& f)
    {
        return f.getHeaders()->get<framing::DeliveryProperties>()->getRoutingKey();
    }

    const std::string& getExchange(const framing::FrameSet& f)
    {
        return f.as<framing::MessageTransferBody>()->getDestination();
    }

    bool isImmediate(const framing::FrameSet&)
    {
        //TODO: we seem to have lost the immediate flag
        return false;
    }

    const framing::FieldTable& getApplicationHeaders(const framing::FrameSet& f)
    {
        return f.getHeaders()->get<framing::MessageProperties>()->getApplicationHeaders();
    }

    bool isPersistent(const framing::FrameSet& f)
    {
        return f.getHeaders()->get<framing::DeliveryProperties>()->getDeliveryMode() == 2;
    }
};

}}


#endif
