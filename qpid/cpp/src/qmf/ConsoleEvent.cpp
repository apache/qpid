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

#include "qmf/ConsoleEventImpl.h"
#include "qmf/PrivateImplRef.h"
#include "qmf/exceptions.h"

using namespace std;
using namespace qmf;
using qpid::types::Variant;

typedef PrivateImplRef<ConsoleEvent> PI;

ConsoleEvent::ConsoleEvent(ConsoleEventImpl* impl) { PI::ctor(*this, impl); }
ConsoleEvent::ConsoleEvent(const ConsoleEvent& s) : qmf::Handle<ConsoleEventImpl>() { PI::copy(*this, s); }
ConsoleEvent::~ConsoleEvent() { PI::dtor(*this); }
ConsoleEvent& ConsoleEvent::operator=(const ConsoleEvent& s) { return PI::assign(*this, s); }

ConsoleEventCode ConsoleEvent::getType() const { return impl->getType(); }
uint32_t ConsoleEvent::getCorrelator() const { return impl->getCorrelator(); }
Agent ConsoleEvent::getAgent() const { return impl->getAgent(); }
AgentDelReason ConsoleEvent::getAgentDelReason() const { return impl->getAgentDelReason(); }
uint32_t ConsoleEvent::getSchemaIdCount() const { return impl->getSchemaIdCount(); }
SchemaId ConsoleEvent::getSchemaId(uint32_t i) const { return impl->getSchemaId(i); }
uint32_t ConsoleEvent::getDataCount() const { return impl->getDataCount(); }
Data ConsoleEvent::getData(uint32_t i) const { return impl->getData(i); }
bool ConsoleEvent::isFinal() const { return impl->isFinal(); }
const Variant::Map& ConsoleEvent::getArguments() const { return impl->getArguments(); }
int ConsoleEvent::getSeverity() const { return impl->getSeverity(); }
uint64_t ConsoleEvent::getTimestamp() const { return impl->getTimestamp(); }


SchemaId ConsoleEventImpl::getSchemaId(uint32_t i) const
{
    uint32_t count = 0;
    for (list<SchemaId>::const_iterator iter = newSchemaIds.begin(); iter != newSchemaIds.end(); iter++) {
        if (count++ == i)
            return *iter;
    }
    throw IndexOutOfRange();
}


Data ConsoleEventImpl::getData(uint32_t i) const
{
    uint32_t count = 0;
    for (list<Data>::const_iterator iter = dataList.begin(); iter != dataList.end(); iter++) {
        if (count++ == i)
            return *iter;
    }
    throw IndexOutOfRange();
}


ConsoleEventImpl& ConsoleEventImplAccess::get(ConsoleEvent& item)
{
    return *item.impl;
}


const ConsoleEventImpl& ConsoleEventImplAccess::get(const ConsoleEvent& item)
{
    return *item.impl;
}
