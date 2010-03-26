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
 
#include "qpid/management/Manageable.h"
#include "qpid/management/ManagementObject.h"
//#include "qpid/framing/FieldTable.h"
#include "qpid/sys/Thread.h"

#include <stdlib.h>

using namespace qpid;
using namespace qpid::management;

void AgentAttachment::setBanks(uint32_t broker, uint32_t bank)
{
    first =
        ((uint64_t) (broker & 0x000fffff)) << 28 |
        ((uint64_t) (bank   & 0x0fffffff));
}

#if 0
// Deprecated
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
#endif

ObjectId::ObjectId(uint8_t flags, uint16_t seq, uint32_t broker)
    : agent(0), agentEpoch(seq)
{
    first =
        ((uint64_t) (flags  &       0x0f)) << 60 |
        ((uint64_t) (seq    &     0x0fff)) << 48 |
        ((uint64_t) (broker & 0x000fffff)) << 28;
}

ObjectId::ObjectId(AgentAttachment* _agent, uint8_t flags, uint16_t seq)
    : agent(_agent), agentEpoch(seq)
{

    first =
        ((uint64_t) (flags &   0x0f)) << 60 |
        ((uint64_t) (seq   & 0x0fff)) << 48;
}


ObjectId::ObjectId(std::istream& in) : agent(0)
{
    std::string text;
    in >> text;
    fromString(text);
}

ObjectId::ObjectId(const std::string& text) : agent(0)
{
    fromString(text);
}

void ObjectId::fromString(const std::string& text)
{
#define FIELDS 5
#if defined (_WIN32) && !defined (atoll)
#  define atoll(X) _atoi64(X)
#endif

    std::string copy(text.c_str());
    char* cText;
    char* field[FIELDS];
    bool  atFieldStart = true;
    int   idx = 0;

    cText = const_cast<char*>(copy.c_str());
    for (char* cursor = cText; *cursor; cursor++) {
        if (atFieldStart) {
            if (idx >= FIELDS)
                throw Exception("Invalid ObjectId format");
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
        throw Exception("Invalid ObjectId format");

    first = (atoll(field[0]) << 60) +
        (atoll(field[1]) << 48) +
        (atoll(field[2]) << 28);

    agentName = std::string(field[3]);
    v2Key = std::string(field[4]);
}


bool ObjectId::operator==(const ObjectId &other) const
{
    return v2Key == other.v2Key;
}

bool ObjectId::operator<(const ObjectId &other) const
{
    return v2Key < other.v2Key;
}

#if 0
bool ObjectId::equalV1(const ObjectId &other) const
{
    uint64_t otherFirst = agent == 0 ? other.first : other.first & 0xffff000000000000LL;
    return first == otherFirst && second == other.second;
}
#endif

// void ObjectId::encode(framing::Buffer& buffer) const
// {
//     if (agent == 0)
//         buffer.putLongLong(first);
//     else
//         buffer.putLongLong(first | agent->first);
//     buffer.putLongLong(second);
// }

// void ObjectId::decode(framing::Buffer& buffer)
// {
//     first  = buffer.getLongLong();
//     second = buffer.getLongLong();
// }

void ObjectId::setV2Key(const ManagementObject& object)
{
    std::stringstream oname;
    oname << object.getPackageName() << "." << object.getClassName() << ":" << object.getKey();
    v2Key = oname.str();
}

void ObjectId::mapEncode(messaging::VariantMap& map) const
{
    if (agent == 0)
        map["_first"] = first;
    else
        map["_first"] = (first | agent->first);

    map["_object_name"] = v2Key;
    if (!agentName.empty())
        map["_agent_name"] = agentName;
    if (agentEpoch)
        map["_agent_epoch"] = agentEpoch;
}

void ObjectId::mapDecode(const messaging::VariantMap& map)
{
    messaging::MapView::const_iterator i;

    if ((i = map.find("_object_name")) != map.end())
        v2Key = i->second.asString();
    else
        throw Exception("Required _object_name field missing.");

    if ((i = map.find("_agent_name")) != map.end())
        agentName = i->second.asString();

    if ((i = map.find("_agent_epoch")) != map.end())
        agentEpoch = i->second.asInt64();
}


ObjectId::operator messaging::VariantMap() const
{
    messaging::VariantMap m;
    mapEncode(m);
    return m;
}



namespace qpid {
namespace management {

std::ostream& operator<<(std::ostream& out, const ObjectId& i)
{
    uint64_t virtFirst = i.first;
    if (i.agent)
        virtFirst |= i.agent->getFirst();

    out << ((virtFirst & 0xF000000000000000LL) >> 60) <<
        "-" << ((virtFirst & 0x0FFF000000000000LL) >> 48) <<
        "-" << ((virtFirst & 0x0000FFFFF0000000LL) >> 28) <<
        "-" << i.agentName <<
        "-" << i.v2Key;
    return out;
}

}}

int ManagementObject::maxThreads = 1;
int ManagementObject::nextThreadIndex = 0;

// void ManagementObject::writeTimestamps (framing::Buffer& buf) const
// {
//     buf.putShortString (getPackageName ());
//     buf.putShortString (getClassName ());
//     buf.putBin128      (getMd5Sum ());
//     buf.putLongLong    (updateTime);
//     buf.putLongLong    (createTime);
//     buf.putLongLong    (destroyTime);
//     objectId.encode(buf);
// }

// void ManagementObject::readTimestamps (framing::Buffer& buf)
// {
//     std::string unused;
//     uint8_t unusedUuid[16];
//     ObjectId unusedObjectId;

//     buf.getShortString(unused);
//     buf.getShortString(unused);
//     buf.getBin128(unusedUuid);
//     updateTime = buf.getLongLong();
//     createTime = buf.getLongLong();
//     destroyTime = buf.getLongLong();
//     unusedObjectId.decode(buf);
// }

// uint32_t ManagementObject::writeTimestampsSize() const
// {
//     return 1 + getPackageName().length() +  // str8
//         1 + getClassName().length() +       // str8
//         16 +                                // bin128
//         8 +                                 // uint64
//         8 +                                 // uint64
//         8 +                                 // uint64
//         objectId.encodedSize();             // objectId
// }


void ManagementObject::writeTimestamps (messaging::VariantMap& map) const
{
    messaging::VariantMap oid, sid;

    sid["_package_name"] = getPackageName();
    sid["_class_name"] = getClassName();
    sid["_hash"] = qpid::messaging::Uuid(getMd5Sum());
    map["_schema_id"] = sid;

    objectId.mapEncode(oid);
    map["_object_id"] = oid;

    map["_update_ts"] = updateTime;
    map["_create_ts"] = createTime;
    map["_delete_ts"] = destroyTime;
}

void ManagementObject::readTimestamps (const ::qpid::messaging::VariantMap& map)
{
    messaging::MapView::const_iterator i;

    if ((i = map.find("_update_ts")) != map.end())
        updateTime = i->second.asUint64();
    if ((i = map.find("_create_ts")) != map.end())
        createTime = i->second.asUint64();
    if ((i = map.find("_delete_ts")) != map.end())
        destroyTime = i->second.asUint64();
}


void ManagementObject::setReference(ObjectId) {}

int ManagementObject::getThreadIndex() {
    static QPID_TSS int thisIndex = -1;
    if (thisIndex == -1) {
        sys::Mutex::ScopedLock mutex(accessLock);
        thisIndex = nextThreadIndex;
        if (nextThreadIndex < maxThreads - 1)
            nextThreadIndex++;
    }
    return thisIndex;
}


void ManagementObject::mapEncode(::qpid::messaging::VariantMap& map,
                                 bool includeProperties,
                                 bool includeStatistics)
{
    messaging::VariantMap values;

    writeTimestamps(map);

    mapEncodeValues(values, includeProperties, includeStatistics);
    map["_values"] = values;
}

void ManagementObject::mapDecode(const ::qpid::messaging::VariantMap& map)
{
    ::qpid::messaging::MapView::const_iterator i;

    readTimestamps(map);

    if ((i = map.find("_values")) != map.end())
        mapDecodeValues(i->second.asMap());
}
