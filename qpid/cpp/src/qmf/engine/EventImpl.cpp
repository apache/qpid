/*
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
 */

#include <qmf/engine/EventImpl.h>
#include <qmf/engine/ValueImpl.h>

#include <sstream>

using namespace std;
using namespace qmf::engine;
using qpid::framing::Buffer;

EventImpl::EventImpl(const SchemaEventClass* type) : eventClass(type)
{
    int argCount = eventClass->getPropertyCount();
    int idx;

    for (idx = 0; idx < argCount; idx++) {
        const SchemaProperty* arg = eventClass->getProperty(idx);
        properties[arg->getName()] = ValuePtr(new Value(arg->getType()));
    }
}


EventImpl::EventImpl(const SchemaEventClass* type, Buffer&) :
    eventClass(type)
{
}


Event* EventImpl::factory(const SchemaEventClass* type, Buffer& buffer)
{
    EventImpl* impl(new EventImpl(type, buffer));
    return new Event(impl);
}


Value* EventImpl::getValue(const char* key) const
{
    map<string, ValuePtr>::const_iterator iter;

    iter = properties.find(key);
    if (iter != properties.end())
        return iter->second.get();

    return 0;
}


void EventImpl::encodeSchemaKey(Buffer& buffer) const
{
    buffer.putShortString(eventClass->getClassKey()->getPackageName());
    buffer.putShortString(eventClass->getClassKey()->getClassName());
    buffer.putBin128(const_cast<uint8_t*>(eventClass->getClassKey()->getHashData()));
}


void EventImpl::encode(Buffer& buffer) const
{
    buffer.putOctet((uint8_t) eventClass->getSeverity());

    int argCount = eventClass->getPropertyCount();
    for (int idx = 0; idx < argCount; idx++) {
        const SchemaProperty* arg = eventClass->getProperty(idx);
        ValuePtr value = properties[arg->getName()];
        value->impl->encode(buffer);
    }
}


string EventImpl::getRoutingKey(uint32_t brokerBank, uint32_t agentBank) const
{
    stringstream key;

    key << "console.event." << brokerBank << "." << agentBank << "." <<
        eventClass->getClassKey()->getPackageName() << "." <<
        eventClass->getClassKey()->getClassName();
    return key.str();
}


//==================================================================
// Wrappers
//==================================================================

Event::Event(const SchemaEventClass* type) : impl(new EventImpl(type)) {}
Event::Event(EventImpl* i) : impl(i) {}
Event::Event(const Event& from) : impl(new EventImpl(*(from.impl))) {}
Event::~Event() { delete impl; }
const SchemaEventClass* Event::getClass() const { return impl->getClass(); }
Value* Event::getValue(const char* key) const { return impl->getValue(key); }

