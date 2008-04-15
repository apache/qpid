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

#include "MessageAdapter.h"

namespace {
    const std::string empty;
}

namespace qpid {
namespace broker{

    std::string TransferAdapter::getRoutingKey(const framing::FrameSet& f)
    {
        const framing::DeliveryProperties010* p = f.getHeaders()->get<framing::DeliveryProperties010>();
        return p ? p->getRoutingKey() : empty;
    }

    std::string TransferAdapter::getExchange(const framing::FrameSet& f)
    {
        return f.as<framing::Message010TransferBody>()->getDestination();
    }

    bool TransferAdapter::isImmediate(const framing::FrameSet&)
    {
        //TODO: delete this, immediate is no longer part of the spec
        return false;
    }

    const framing::FieldTable* TransferAdapter::getApplicationHeaders(const framing::FrameSet& f)
    {
        const framing::MessageProperties010* p = f.getHeaders()->get<framing::MessageProperties010>();
        return p ? &(p->getApplicationHeaders()) : 0;
    }

    bool TransferAdapter::isPersistent(const framing::FrameSet& f)
    {
        const framing::DeliveryProperties010* p = f.getHeaders()->get<framing::DeliveryProperties010>();
        return p && p->getDeliveryMode() == 2;
    }

    bool TransferAdapter::requiresAccept(const framing::FrameSet& f)
    {
        const framing::Message010TransferBody* b = f.as<framing::Message010TransferBody>();
        return b && b->getAcceptMode() == 0/*EXPLICIT == 0*/;
    }

    std::string PreviewAdapter::getExchange(const framing::FrameSet& f)
    {
        return f.as<framing::MessageTransferBody>()->getDestination();
    }

    std::string PreviewAdapter::getRoutingKey(const framing::FrameSet& f)
    {
        const framing::DeliveryProperties* p = f.getHeaders()->get<framing::DeliveryProperties>();
        return p ? p->getRoutingKey() : empty;
    }

    const framing::FieldTable* PreviewAdapter::getApplicationHeaders(const framing::FrameSet& f)
    {
        const framing::MessageProperties* p = f.getHeaders()->get<framing::MessageProperties>();
        return p ? &(p->getApplicationHeaders()) : 0;
    }

    bool PreviewAdapter::isPersistent(const framing::FrameSet& f)
    {
        const framing::DeliveryProperties* p = f.getHeaders()->get<framing::DeliveryProperties>();
        return p && p->getDeliveryMode() == 2;
    }

}}
