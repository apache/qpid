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

#include "qpid/broker/Broker.h"
#include "qpid/broker/ExchangeRegistry.h"
#include "qpid/broker/DirectExchange.h"
#include "qpid/broker/FanOutExchange.h"
#include "qpid/broker/HeadersExchange.h"
#include "qpid/broker/TopicExchange.h"
#include "qpid/broker/Link.h"
#include "qpid/management/ManagementDirectExchange.h"
#include "qpid/management/ManagementTopicExchange.h"
#include "qpid/framing/reply_exceptions.h"
#include "qmf/org/apache/qpid/broker/EventExchangeDeclare.h"
#include "qmf/org/apache/qpid/broker/EventExchangeDelete.h"

using namespace qpid::broker;
using namespace qpid::sys;
using std::pair;
using std::string;
using qpid::framing::FieldTable;
using qpid::management::ManagementAgent;
namespace _qmf = qmf::org::apache::qpid::broker;

pair<Exchange::shared_ptr, bool> ExchangeRegistry::declare(const string& name, const string& type){

    return declare(name, type, false, false, FieldTable());
}

pair<Exchange::shared_ptr, bool> ExchangeRegistry::declare(
    const string& name, const string& type, bool durable, bool autodelete, const FieldTable& args,
    Exchange::shared_ptr alternate, const string& connectionId, const string& userId)
{
    Exchange::shared_ptr exchange;
    std::pair<Exchange::shared_ptr, bool> result;
    {
        RWlock::ScopedWlock locker(lock);
        ExchangeMap::iterator i =  exchanges.find(name);
        if (i == exchanges.end()) {
            if (type == TopicExchange::typeName){
                exchange = Exchange::shared_ptr(new TopicExchange(name, durable, autodelete, args, parent, broker));
            }else if(type == DirectExchange::typeName){
                exchange = Exchange::shared_ptr(new DirectExchange(name, durable, autodelete, args, parent, broker));
            }else if(type == FanOutExchange::typeName){
                exchange = Exchange::shared_ptr(new FanOutExchange(name, durable, autodelete, args, parent, broker));
            }else if (type == HeadersExchange::typeName) {
                exchange = Exchange::shared_ptr(new HeadersExchange(name, durable, autodelete, args, parent, broker));
            }else if (type == ManagementDirectExchange::typeName) {
                exchange = Exchange::shared_ptr(new ManagementDirectExchange(name, durable, args, parent, broker));
            }else if (type == ManagementTopicExchange::typeName) {
                exchange = Exchange::shared_ptr(new ManagementTopicExchange(name, durable, args, parent, broker));
            }else if (type == Link::exchangeTypeName) {
                exchange = Link::linkExchangeFactory(name);
            }else{
                FunctionMap::iterator i =  factory.find(type);
                if (i == factory.end()) {
                    throw UnknownExchangeTypeException(type);
                } else {
                    exchange = i->second(name, durable, autodelete, args, parent, broker);
                }
            }
            exchanges[name] = exchange;
            result = std::pair<Exchange::shared_ptr, bool>(exchange, true);
            if (alternate) exchange->setAlternate(alternate);
            // Call exchangeCreate inside the lock to ensure correct ordering.
            if (broker) broker->getBrokerObservers().exchangeCreate(exchange);
        } else {
            result = std::pair<Exchange::shared_ptr, bool>(i->second, false);
        }
        if (broker && broker->getManagementAgent()) {
            // Call raiseEvent inside the lock to ensure correct ordering.
            broker->getManagementAgent()->raiseEvent(
                _qmf::EventExchangeDeclare(
                    connectionId,
                    userId,
                    name,
                    type,
                    alternate ? alternate->getName() : string(),
                    durable,
                    false,
                    ManagementAgent::toMap(result.first->getArgs()),
                    result.second ? "created" : "existing"));
        }
    }
    return result;
}

void ExchangeRegistry::destroy(
    const string& name, const string& connectionId, const string& userId)
{
    if (name.empty() ||
        (name.find("amq.") == 0 &&
         (name == "amq.direct" || name == "amq.fanout" || name == "amq.topic" || name == "amq.match")) ||
        name == "qpid.management")
        throw framing::NotAllowedException(QPID_MSG("Cannot delete default exchange: '" << name << "'"));
    {
        RWlock::ScopedWlock locker(lock);
        ExchangeMap::iterator i =  exchanges.find(name);
        if (i != exchanges.end()) {
            if (broker) {
                // Call exchangeDestroy and raiseEvent inside the lock to ensure
                // correct ordering.
                broker->getBrokerObservers().exchangeDestroy(i->second);
                if (broker->getManagementAgent())
                    broker->getManagementAgent()->raiseEvent(
                        _qmf::EventExchangeDelete(connectionId, userId, name));
            }
            i->second->destroy();
            exchanges.erase(i);

        }
    }
}

Exchange::shared_ptr ExchangeRegistry::find(const string& name){
    RWlock::ScopedRlock locker(lock);
    ExchangeMap::iterator i =  exchanges.find(name);
    if (i == exchanges.end())
        return Exchange::shared_ptr();
    else
        return i->second;
}

Exchange::shared_ptr ExchangeRegistry::get(const string& name) {
    Exchange::shared_ptr ex = find(name);
    if (!ex) throw framing::NotFoundException(QPID_MSG("Exchange not found: "<<name));
    return ex;
}

bool ExchangeRegistry::registerExchange(const Exchange::shared_ptr& ex) {
    RWlock::ScopedWlock locker(lock);
    return exchanges.insert(ExchangeMap::value_type(ex->getName(), ex)).second;
}

void ExchangeRegistry::registerType(const std::string& type, FactoryFunction f)
{
    factory[type] = f;
}

void ExchangeRegistry::checkType(const std::string& type)
{
    if (type != TopicExchange::typeName && type != DirectExchange::typeName && type != FanOutExchange::typeName
        && type != HeadersExchange::typeName && type != ManagementDirectExchange::typeName
        && type != ManagementTopicExchange::typeName && type != Link::exchangeTypeName
        && factory.find(type) == factory.end()) {
        throw UnknownExchangeTypeException(type);
    }
}


namespace
{
const std::string EMPTY;
}

Exchange::shared_ptr ExchangeRegistry::getDefault()
{
    return get(EMPTY);
}
