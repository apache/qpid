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
#include <ExchangeRegistry.h>
#include <DirectExchange.h>
#include <FanOutExchange.h>
#include <HeadersExchange.h>
#include <TopicExchange.h>

using namespace qpid::broker;
using namespace qpid::sys;
using std::pair;

pair<Exchange::shared_ptr, bool> ExchangeRegistry::declare(const string& name, const string& type) throw(UnknownExchangeTypeException){
    Mutex::ScopedLock locker(lock);
    ExchangeMap::iterator i =  exchanges.find(name);
    if (i == exchanges.end()) {
	Exchange::shared_ptr exchange;

        if(type == TopicExchange::typeName){
            exchange = Exchange::shared_ptr(new TopicExchange(name));
        }else if(type == DirectExchange::typeName){
            exchange = Exchange::shared_ptr(new DirectExchange(name));
        }else if(type == FanOutExchange::typeName){
            exchange = Exchange::shared_ptr(new FanOutExchange(name));
        }else if (type == HeadersExchange::typeName) {
            exchange = Exchange::shared_ptr(new HeadersExchange(name));
        }else{
            throw UnknownExchangeTypeException();    
        }
	exchanges[name] = exchange;
	return std::pair<Exchange::shared_ptr, bool>(exchange, true);
    } else {
	return std::pair<Exchange::shared_ptr, bool>(i->second, false);
    }
}

void ExchangeRegistry::destroy(const string& name){
    Mutex::ScopedLock locker(lock);
    exchanges.erase(name);
}

Exchange::shared_ptr ExchangeRegistry::get(const string& name){
    Mutex::ScopedLock locker(lock);
    Exchange::shared_ptr exchange =exchanges[name];
    if (!exchange) 
        throw ChannelException(404, "Exchange not found:" + name);
    return exchange;
}

namespace 
{
const std::string empty;
}

Exchange::shared_ptr ExchangeRegistry::getDefault()
{
    return get(empty);
}
