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
#include <qpid/broker/SessionHandlerFactoryImpl.h>

#include <qpid/broker/DirectExchange.h>
#include <qpid/broker/FanOutExchange.h>
#include <qpid/broker/HeadersExchange.h>
#include <qpid/broker/MessageStoreModule.h>
#include <qpid/broker/NullMessageStore.h>
#include <qpid/broker/SessionHandlerImpl.h>

using namespace qpid::broker;
using namespace qpid::sys;

namespace
{
const std::string empty;
const std::string amq_direct("amq.direct");
const std::string amq_topic("amq.topic");
const std::string amq_fanout("amq.fanout");
const std::string amq_match("amq.match");
}

SessionHandlerFactoryImpl::SessionHandlerFactoryImpl(const std::string& _store, u_int32_t _timeout) : 
    store(_store.empty() ? (MessageStore*)  new NullMessageStore() : (MessageStore*) new MessageStoreModule(_store)), 
    queues(store.get()), timeout(_timeout), cleaner(&queues, timeout/10)
{
    exchanges.declare(empty, DirectExchange::typeName); // Default exchange.
    exchanges.declare(amq_direct, DirectExchange::typeName);
    exchanges.declare(amq_topic, TopicExchange::typeName);
    exchanges.declare(amq_fanout, FanOutExchange::typeName);
    exchanges.declare(amq_match, HeadersExchange::typeName);

    if(store.get()) store->recover(queues);

    cleaner.start();
}

SessionHandler* SessionHandlerFactoryImpl::create(SessionContext* ctxt)
{
    return new SessionHandlerImpl(ctxt, &queues, &exchanges, &cleaner, timeout);
}

SessionHandlerFactoryImpl::~SessionHandlerFactoryImpl()
{
    cleaner.stop();
}
