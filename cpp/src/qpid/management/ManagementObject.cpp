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
 
#include "Manageable.h"
#include "ManagementObject.h"
#include "qpid/agent/ManagementAgent.h"
#include "qpid/framing/FieldTable.h"

using namespace qpid::framing;
using namespace qpid::management;
using namespace qpid::sys;

void AgentAttachment::setBanks(uint32_t broker, uint32_t bank)
{
    first =
        ((uint64_t) (broker & 0x000fffff)) << 28 |
        ((uint64_t) (bank   & 0x0fffffff));
}

ObjectId::ObjectId(uint8_t flags, uint16_t seq, uint32_t broker, uint32_t bank, uint64_t object)
    : agent(0)
{
    first =
        ((uint64_t) (flags  &       0x0f)) << 60 |
        ((uint64_t) (seq    &     0x0fff)) << 48 |
        ((uint64_t) (broker & 0x000fffff)) << 28 |
        ((uint64_t) (bank   & 0x0fffffff));
    second = object;
}

ObjectId::ObjectId(AgentAttachment* _agent, uint8_t flags, uint16_t seq, uint64_t object)
    : agent(_agent)
{
    first =
        ((uint64_t) (flags &   0x0f)) << 60 |
        ((uint64_t) (seq   & 0x0fff)) << 48;
    second = object;
}

bool ObjectId::operator==(const ObjectId &other) const
{
    uint64_t otherFirst = agent == 0 ? other.first : other.first & 0xffff000000000000LL;

    return first == otherFirst && second == other.second;
}

bool ObjectId::operator<(const ObjectId &other) const
{
    uint64_t otherFirst = agent == 0 ? other.first : other.first & 0xffff000000000000LL;

    return (first < otherFirst) || ((first == otherFirst) && (second < other.second));
}

void ObjectId::encode(framing::Buffer& buffer)
{
    if (agent == 0)
        buffer.putLongLong(first);
    else
        buffer.putLongLong(first | agent->first);
    buffer.putLongLong(second);
}

void ObjectId::decode(framing::Buffer& buffer)
{
    first  = buffer.getLongLong();
    second = buffer.getLongLong();
}

int ManagementObject::nextThreadIndex = 0;

void ManagementObject::writeTimestamps (Buffer& buf)
{
    buf.putShortString (getPackageName ());
    buf.putShortString (getClassName ());
    buf.putBin128      (getMd5Sum ());
    buf.putLongLong    (uint64_t (Duration (now ())));
    buf.putLongLong    (createTime);
    buf.putLongLong    (destroyTime);
    objectId.encode(buf);
}

void ManagementObject::setReference(ObjectId) {}

int ManagementObject::getThreadIndex() {
    static __thread int thisIndex = -1;
    if (thisIndex == -1) {
        sys::Mutex::ScopedLock mutex(accessLock);
        thisIndex = nextThreadIndex;
        if (nextThreadIndex < agent->getMaxThreads() - 1)
            nextThreadIndex++;
    }
    return thisIndex;
}

Mutex& ManagementObject::getMutex()
{
    return agent->getMutex();
}

Buffer* ManagementObject::startEventLH()
{
    return agent->startEventLH();
}

void ManagementObject::finishEventLH(Buffer* buf)
{
    agent->finishEventLH(buf);
}
