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
#include "SessionHandlerFactoryImpl.h"
#include "SessionHandlerImpl.h"
#include "FanOutExchange.h"

using namespace qpid::broker;
using namespace qpid::io;

SessionHandlerFactoryImpl::SessionHandlerFactoryImpl(u_int32_t _timeout) : timeout(_timeout), cleaner(&queues, timeout/10){
    exchanges.declare(new DirectExchange("amq.direct"));
    exchanges.declare(new TopicExchange("amq.topic"));
    exchanges.declare(new FanOutExchange("amq.fanout"));
    cleaner.start();
}

SessionHandler* SessionHandlerFactoryImpl::create(SessionContext* ctxt){
    return new SessionHandlerImpl(ctxt, &queues, &exchanges, &cleaner, timeout);
}

SessionHandlerFactoryImpl::~SessionHandlerFactoryImpl(){
    cleaner.stop();
    exchanges.destroy("amq.direct");
    exchanges.destroy("amq.topic");    
}
