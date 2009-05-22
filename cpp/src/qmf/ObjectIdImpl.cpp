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

#include "ObjectIdImpl.h"
#include <stdlib.h>

using namespace std;
using namespace qmf;
using qpid::framing::Buffer;


void AgentAttachment::setBanks(uint32_t broker, uint32_t agent)
{
    first =
        ((uint64_t) (broker & 0x000fffff)) << 28 |
        ((uint64_t) (agent  & 0x0fffffff));
}

ObjectIdImpl::ObjectIdImpl(Buffer& buffer) : envelope(new ObjectId(this)), agent(0)
{
    decode(buffer);
}

ObjectIdImpl::ObjectIdImpl(AgentAttachment* a, uint8_t flags, uint16_t seq, uint64_t object) :
    envelope(new ObjectId(this)), agent(a)
{
    first =
        ((uint64_t) (flags &   0x0f)) << 60 |
        ((uint64_t) (seq   & 0x0fff)) << 48;
    second = object;
}

void ObjectIdImpl::decode(Buffer& buffer)
{
    first = buffer.getLongLong();
    second = buffer.getLongLong();
}

void ObjectIdImpl::encode(Buffer& buffer) const
{
    if (agent == 0)
        buffer.putLongLong(first);
    else
        buffer.putLongLong(first | agent->first);
    buffer.putLongLong(second);
}

void ObjectIdImpl::fromString(const std::string& repr)
{
#define FIELDS 5
#if defined (_WIN32) && !defined (atoll)
#  define atoll(X) _atoi64(X)
#endif

    std::string copy(repr.c_str());
    char* cText;
    char* field[FIELDS];
    bool  atFieldStart = true;
    int   idx = 0;

    cText = const_cast<char*>(copy.c_str());
    for (char* cursor = cText; *cursor; cursor++) {
        if (atFieldStart) {
            if (idx >= FIELDS)
                return; // TODO error
            field[idx++] = cursor;
            atFieldStart = false;
        } else {
            if (*cursor == '-') {
                *cursor = '\0';
                atFieldStart = true;
            }
        }
    }

    if (idx != FIELDS)
        return;  // TODO error

    first = (atoll(field[0]) << 60) +
        (atoll(field[1]) << 48) +
        (atoll(field[2]) << 28) +
        atoll(field[3]);
    second = atoll(field[4]);
    agent = 0;
}

bool ObjectIdImpl::operator==(const ObjectIdImpl& other) const
{
    uint64_t otherFirst = agent == 0 ? other.first : other.first & 0xffff000000000000LL;

    return first == otherFirst && second == other.second;
}

bool ObjectIdImpl::operator<(const ObjectIdImpl& other) const
{
    uint64_t otherFirst = agent == 0 ? other.first : other.first & 0xffff000000000000LL;

    return (first < otherFirst) || ((first == otherFirst) && (second < other.second));
}

bool ObjectIdImpl::operator>(const ObjectIdImpl& other) const
{
    uint64_t otherFirst = agent == 0 ? other.first : other.first & 0xffff000000000000LL;

    return (first > otherFirst) || ((first == otherFirst) && (second > other.second));
}


//==================================================================
// Wrappers
//==================================================================

ObjectId::ObjectId()
{
    impl = new ObjectIdImpl(this);
}

ObjectId::ObjectId(ObjectIdImpl* i)
{
    impl = i;
}

ObjectId::~ObjectId()
{
    delete impl;
}

uint64_t ObjectId::getObjectNum() const
{
    return impl->getObjectNum();
}

uint32_t ObjectId::getObjectNumHi() const
{
    return impl->getObjectNumHi();
}

uint32_t ObjectId::getObjectNumLo() const
{
    return impl->getObjectNumLo();
}

bool ObjectId::isDurable() const
{
    return impl->isDurable();
}

bool ObjectId::operator==(const ObjectId& other) const
{
    return *impl == *other.impl;
}

bool ObjectId::operator!=(const ObjectId& other) const
{
    return !(*impl == *other.impl);
}

bool ObjectId::operator<(const ObjectId& other) const
{
    return *impl < *other.impl;
}

bool ObjectId::operator>(const ObjectId& other) const
{
    return *impl > *other.impl;
}

bool ObjectId::operator<=(const ObjectId& other) const
{
    return !(*impl > *other.impl);
}

bool ObjectId::operator>=(const ObjectId& other) const
{
    return !(*impl < *other.impl);
}
