/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include "qpid/broker/SessionHandlerFactoryImpl.h"
#include "qpid/broker/SessionHandlerImpl.h"
#include "qpid/broker/FanOutExchange.h"
#include "qpid/broker/HeadersExchange.h"

using namespace qpid::broker;
using namespace qpid::io;

namespace
{
const std::string empty;
const std::string amq_direct("amq.direct");
const std::string amq_topic("amq.topic");
const std::string amq_fanout("amq.fanout");
const std::string amq_match("amq.match");
}

SessionHandlerFactoryImpl::SessionHandlerFactoryImpl(u_int32_t _timeout) : timeout(_timeout), cleaner(&queues, timeout/10){
    exchanges.declare(new DirectExchange(empty)); // Default exchange.
    exchanges.declare(new DirectExchange(amq_direct));
    exchanges.declare(new TopicExchange(amq_topic));
    exchanges.declare(new FanOutExchange(amq_fanout));
    exchanges.declare(new HeadersExchange(amq_match));
    cleaner.start();
}

SessionHandler* SessionHandlerFactoryImpl::create(SessionContext* ctxt){
    return new SessionHandlerImpl(ctxt, &queues, &exchanges, &cleaner, timeout);
}

SessionHandlerFactoryImpl::~SessionHandlerFactoryImpl(){
    cleaner.stop();
}
