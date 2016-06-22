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
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/Buffer.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/sys/Time.h"
#include "qpid/sys/Thread.h"
#include "qpid/log/Statement.h"
#include <boost/lexical_cast.hpp>

#include <stdlib.h>

using namespace std;
using namespace qpid;
using namespace qpid::management;

void AgentAttachment::setBanks(uint32_t broker, uint32_t bank)
{
    first =
        ((uint64_t) (broker & 0x000fffff)) << 28 |
        ((uint64_t) (bank   & 0x0fffffff));
}

// Deprecated
ObjectId::ObjectId(uint8_t flags, uint16_t seq, uint32_t broker, uint64_t object)
    : agent(0), agentEpoch(seq)
{
    first =
        ((uint64_t) (flags  &       0x0f)) << 60 |
        ((uint64_t) (seq    &     0x0fff)) << 48 |
      ((uint64_t) (broker & 0x000fffff)) << 28;
    second = object;
}


ObjectId::ObjectId(uint8_t flags, uint16_t seq, uint32_t broker)
    : agent(0), second(0), agentEpoch(seq)
{
    first =
        ((uint64_t) (flags  &       0x0f)) << 60 |
        ((uint64_t) (seq    &     0x0fff)) << 48 |
        ((uint64_t) (broker & 0x000fffff)) << 28;
}

ObjectId::ObjectId(AgentAttachment* _agent, uint8_t flags, uint16_t seq)
    : agent(_agent), second(0), agentEpoch(seq)
{

    first =
        ((uint64_t) (flags &   0x0f)) << 60 |
        ((uint64_t) (seq   & 0x0fff)) << 48;
}


ObjectId::ObjectId(istream& in) : agent(0)
{
    string text;
    in >> text;
    fromString(text);
}

ObjectId::ObjectId(const string& text) : agent(0)
{
    fromString(text);
}

void ObjectId::fromString(const string& text)
{
#define FIELDS 5
#if defined (_WIN32) && !defined (atoll)
#  define atoll(X) _atoi64(X)
#endif

    // format:
    // V1: <flags>-<sequence>-<broker-bank>-<agent-bank>-<uint64-app-id>
    // V2: Not used

    string copy(text.c_str());
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

    agentEpoch = atoll(field[1]);

    first = (atoll(field[0]) << 60) +
        (atoll(field[1]) << 48) +
        (atoll(field[2]) << 28);

    agentName = string(field[3]);
    second = atoll(field[4]);
}


bool ObjectId::operator==(const ObjectId &other) const
{
    return v2Key == other.v2Key;
}

bool ObjectId::operator<(const ObjectId &other) const
{
    return v2Key < other.v2Key;
}

bool ObjectId::equalV1(const ObjectId &other) const
{
    uint64_t otherFirst = agent == 0 ? other.first : other.first & 0xffff000000000000LL;
    return first == otherFirst && second == other.second;
}

// encode as V1-format binary
void ObjectId::encode(string& buffer) const
{
    const uint32_t len = 16;
    char _data[len];
    qpid::framing::Buffer body(_data, len);

    if (agent == 0)
        body.putLongLong(first);
    else
        body.putLongLong(first | agent->first);
    body.putLongLong(second);

    body.reset();
    body.getRawData(buffer, len);
}

// decode as V1-format binary
void ObjectId::decode(const string& buffer)
{
    const uint32_t len = 16;
    char _data[len];
    qpid::framing::Buffer body(_data, len);

    body.checkAvailable(buffer.length());
    body.putRawData(buffer);
    body.reset();
    first  = body.getLongLong();
    second = body.getLongLong();
    v2Key = boost::lexical_cast<string>(second);
}

// generate the V2 key from the index fields defined
// in the schema.
void ObjectId::setV2Key(const ManagementObject& object)
{
    stringstream oname;
    oname << object.getPackageName() << ":" << object.getClassName() << ":" << object.getKey();
    v2Key = oname.str();
}


// encode as V2-format map
void ObjectId::mapEncode(types::Variant::Map& map) const
{
    map["_object_name"] = v2Key;
    if (!agentName.empty())
        map["_agent_name"] = agentName;
    if (agentEpoch)
        map["_agent_epoch"] = agentEpoch;
}

// decode as v2-format map
void ObjectId::mapDecode(const types::Variant::Map& map)
{
    types::Variant::Map::const_iterator i;

    if ((i = map.find("_object_name")) != map.end())
        v2Key = i->second.asString();
    else
        throw Exception("Required _object_name field missing.");

    if ((i = map.find("_agent_name")) != map.end())
        agentName = i->second.asString();

    if ((i = map.find("_agent_epoch")) != map.end())
        agentEpoch = i->second.asInt64();
}


ObjectId::operator types::Variant::Map() const
{
    types::Variant::Map m;
    mapEncode(m);
    return m;
}



namespace qpid {
namespace management {

ostream& operator<<(ostream& out, const ObjectId& i)
{
    uint64_t virtFirst = i.first;
    if (i.agent)
        virtFirst |= i.agent->getFirst();

    out << ((virtFirst & 0xF000000000000000LL) >> 60) <<
        "-" << ((virtFirst & 0x0FFF000000000000LL) >> 48) <<
        "-" << ((virtFirst & 0x0000FFFFF0000000LL) >> 28) <<
        "-" << i.agentName <<
        "-" << i.second <<
        "(" << i.v2Key << ")";
    return out;
}

}}

// Called with lock held
Manageable* ManagementObject::ManageablePtr::get() const {
    if (ptr == 0)
        throw framing::ResourceDeletedException("managed object deleted");
    return ptr;
}

void ManagementObject::ManageablePtr::reset() {
    Mutex::ScopedLock l(lock);
    ptr = 0;
}

uint32_t ManagementObject::ManageablePtr::ManagementMethod(
    uint32_t methodId, Args& args, std::string& text)
{
    Mutex::ScopedLock l(lock);
    return get()->ManagementMethod(methodId, args, text);
}

bool ManagementObject::ManageablePtr:: AuthorizeMethod(
    uint32_t methodId, Args& args, const std::string& userId)
{
    Mutex::ScopedLock l(lock);
    return get()->AuthorizeMethod(methodId, args, userId);
}

ManagementObject::ManagementObject(Manageable* _core) :
    createTime(qpid::sys::Duration::FromEpoch()),
    destroyTime(0), updateTime(createTime), configChanged(true),
    instChanged(true), deleted(false),
    manageable(_core), flags(0), forcePublish(false) {}

void ManagementObject::setUpdateTime()
{
    updateTime = sys::Duration::FromEpoch();
}

void ManagementObject::resourceDestroy()
{
    QPID_LOG(trace, "Management object marked deleted: " << getObjectId().getV2Key());
    destroyTime = sys::Duration::FromEpoch();
    deleted     = true;
    manageable.reset();
}

int ManagementObject::maxThreads = 1;
int ManagementObject::nextThreadIndex = 0;

void ManagementObject::writeTimestamps (string& buf) const
{
    char _data[4000];
    qpid::framing::Buffer body(_data, 4000);

    body.putShortString (getPackageName ());
    body.putShortString (getClassName ());
    body.putBin128      (getMd5Sum ());
    body.putLongLong    (updateTime);
    body.putLongLong    (createTime);
    body.putLongLong    (destroyTime);

    uint32_t len = body.getPosition();
    body.reset();
    body.getRawData(buf, len);

    string oid;
    objectId.encode(oid);
    buf += oid;
}

void ManagementObject::readTimestamps (const string& buf)
{
    char _data[4000];
    qpid::framing::Buffer body(_data, 4000);
    string unused;
    uint8_t unusedUuid[16];

    body.checkAvailable(buf.length());
    body.putRawData(buf);
    body.reset();

    body.getShortString(unused);
    body.getShortString(unused);
    body.getBin128(unusedUuid);
    updateTime = body.getLongLong();
    createTime = body.getLongLong();
    destroyTime = body.getLongLong();
}

uint32_t ManagementObject::writeTimestampsSize() const
{
    return 1 + getPackageName().length() +  // str8
      1 + getClassName().length() +       // str8
      16 +                                // bin128
      8 +                                 // uint64
      8 +                                 // uint64
      8 +                                 // uint64
      objectId.encodedSize();             // objectId
}


void ManagementObject::writeTimestamps (types::Variant::Map& map) const
{
    // types::Variant::Map oid, sid;

    // sid["_package_name"] = getPackageName();
    // sid["_class_name"] = getClassName();
    // sid["_hash"] = qpid::types::Uuid(getMd5Sum());
    // map["_schema_id"] = sid;

    // objectId.mapEncode(oid);
    // map["_object_id"] = oid;

    map["_update_ts"] = updateTime;
    map["_create_ts"] = createTime;
    map["_delete_ts"] = destroyTime;
}

void ManagementObject::readTimestamps (const types::Variant::Map& map)
{
    types::Variant::Map::const_iterator i;

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
        Mutex::ScopedLock mutex(accessLock);
        thisIndex = nextThreadIndex;
        if (nextThreadIndex < maxThreads - 1)
            nextThreadIndex++;
    }
    return thisIndex;
}


// void ManagementObject::mapEncode(types::Variant::Map& map,
//                                  bool includeProperties,
//                                  bool includeStatistics)
// {
//     types::Variant::Map values;

//     writeTimestamps(map);

//     mapEncodeValues(values, includeProperties, includeStatistics);
//     map["_values"] = values;
// }

// void ManagementObject::mapDecode(const types::Variant::Map& map)
// {
//     types::Variant::Map::const_iterator i;

//     readTimestamps(map);

//     if ((i = map.find("_values")) != map.end())
//         mapDecodeValues(i->second.asMap());
// }
