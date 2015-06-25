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

#include "qmf/AgentEventImpl.h"
#include "qmf/PrivateImplRef.h"
#include "qmf/SchemaImpl.h"

using namespace std;
using namespace qmf;
using qpid::types::Variant;

typedef PrivateImplRef<AgentEvent> PI;

AgentEvent::AgentEvent(AgentEventImpl* impl) { PI::ctor(*this, impl); }
AgentEvent::AgentEvent(const AgentEvent& s) : qmf::Handle<AgentEventImpl>() { PI::copy(*this, s); }
AgentEvent::~AgentEvent() { PI::dtor(*this); }
AgentEvent& AgentEvent::operator=(const AgentEvent& s) { return PI::assign(*this, s); }

AgentEventCode AgentEvent::getType() const { return impl->getType(); }
const string& AgentEvent::getUserId() const { return impl->getUserId(); }
Query AgentEvent::getQuery() const { return impl->getQuery(); }
bool AgentEvent::hasDataAddr() const { return impl->hasDataAddr(); }
DataAddr AgentEvent::getDataAddr() const { return impl->getDataAddr(); }
const string& AgentEvent::getMethodName() const { return impl->getMethodName(); }
qpid::types::Variant::Map& AgentEvent::getArguments() { return impl->getArguments(); }
qpid::types::Variant::Map& AgentEvent::getArgumentSubtypes() { return impl->getArgumentSubtypes(); }
void AgentEvent::addReturnArgument(const std::string& k, const qpid::types::Variant& v, const std::string& s) { impl->addReturnArgument(k, v, s); }

uint32_t AgentEventImpl::enqueueData(const Data& data)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    dataQueue.push(data);
    return dataQueue.size();
}


Data AgentEventImpl::dequeueData()
{
    qpid::sys::Mutex::ScopedLock l(lock);
    if (dataQueue.empty())
        return Data();
    Data data(dataQueue.front());
    dataQueue.pop();
    return data;
}


void AgentEventImpl::addReturnArgument(const string& key, const Variant& val, const string& subtype)
{
    if (schema.isValid() && !SchemaImplAccess::get(schema).isValidMethodOutArg(methodName, key, val))
        throw QmfException("Output argument is unknown or the type is incompatible");
    outArguments[key] = val;
    if (!subtype.empty())
        outArgumentSubtypes[key] = subtype;
}


AgentEventImpl& AgentEventImplAccess::get(AgentEvent& item)
{
    return *item.impl;
}


const AgentEventImpl& AgentEventImplAccess::get(const AgentEvent& item)
{
    return *item.impl;
}
