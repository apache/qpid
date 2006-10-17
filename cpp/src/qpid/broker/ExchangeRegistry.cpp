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
#include "qpid/broker/ExchangeRegistry.h"
#include "qpid/concurrent/MonitorImpl.h"

using namespace qpid::broker;
using namespace qpid::concurrent;

ExchangeRegistry::ExchangeRegistry() : lock(new MonitorImpl()){}

ExchangeRegistry::~ExchangeRegistry(){
    for (ExchangeMap::iterator i = exchanges.begin(); i != exchanges.end(); ++i)
    {
        delete i->second;
    }
    delete lock;
}

void ExchangeRegistry::declare(Exchange* exchange){
    exchanges[exchange->getName()] = exchange;
}

void ExchangeRegistry::destroy(const string& name){
    if(exchanges[name]){
        delete exchanges[name];
        exchanges.erase(name);
    }
}

Exchange* ExchangeRegistry::get(const string& name){
    return exchanges[name];
}

namespace 
{
const std::string empty;
}

Exchange* ExchangeRegistry::getDefault()
{
    return get(empty);
}
