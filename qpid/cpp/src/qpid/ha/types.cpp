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

#include "types.h"
#include "qpid/Msg.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/Queue.h"
#include "qpid/Exception.h"
#include <algorithm>
#include <iostream>
#include <iterator>
#include <assert.h>

namespace qpid {
namespace ha {

using namespace std;

const string QPID_REPLICATE("qpid.replicate");
const string QPID_HA_UUID("qpid.ha-uuid");

const char* QPID_HA_PREFIX = "qpid.ha-";
const char* QUEUE_REPLICATOR_PREFIX = "qpid.ha-q:";

bool startsWith(const string& name, const string& prefix) {
    return name.compare(0, prefix.size(), prefix) == 0;
}

string EnumBase::str() const {
    assert(value < count);
    return names[value];
}

void EnumBase::parse(const string& s) {
    if (!parseNoThrow(s))
        throw Exception(QPID_MSG("Invalid " << name << " value: " << s));
}

bool EnumBase::parseNoThrow(const string& s) {
    const char** i = find(names, names+count, s);
    value = i - names;
    return value < count;
}

template <> const char* Enum<ReplicateLevel>::NAME = "replication";
template <> const char* Enum<ReplicateLevel>::NAMES[] = { "none", "configuration", "all" };
template <> const size_t Enum<ReplicateLevel>::N = 3;

template <> const char* Enum<BrokerStatus>::NAME = "HA broker status";

// NOTE: Changing status names will  have an impact on qpid-ha and
// the qpidd-primary init script.
// Don't change them unless you are going to  update all dependent code.
//
template <> const char* Enum<BrokerStatus>::NAMES[] = {
    "joining", "catchup", "ready", "recovering", "active", "standalone"
};
template <> const size_t Enum<BrokerStatus>::N = 6;

ostream& operator<<(ostream& o, EnumBase e) {
    return o << e.str();
}

istream& operator>>(istream& i, EnumBase& e) {
    string s;
    i >> s;
    e.parse(s);
    return i;
}

ostream& operator<<(ostream& o, const UuidSet& ids) {
    ostream_iterator<qpid::types::Uuid> out(o, " ");
    o << "{ ";
    for (UuidSet::const_iterator i = ids.begin(); i != ids.end(); ++i)
        o << shortStr(*i) << " ";
    o << "}";
    return o;
}


std::string logMessageId(const std::string& q, QueuePosition pos, ReplicationId id) {
    return Msg() << q << "[" << pos << "]" << "=" << id;
}
std::string logMessageId(const std::string& q, ReplicationId id) {
    return Msg() << q << "[]" << "=" << id;
}
std::string logMessageId(const std::string& q, const broker::Message& m)  {
    return logMessageId(q, m.getSequence(), m.getReplicationId());
}
std::string logMessageId(const broker::Queue& q, QueuePosition pos, ReplicationId id) {
    return logMessageId(q.getName(), pos, id);
}
std::string logMessageId(const broker::Queue& q, ReplicationId id) {
    return logMessageId(q.getName(), id);
}
std::string logMessageId(const broker::Queue& q, const broker::Message& m) {
    return logMessageId(q.getName(), m);
}

void UuidSet::encode(framing::Buffer& b) const {
    b.putLong(size());
    for (const_iterator i = begin(); i != end(); ++i)
        b.putRawData(i->data(), i->size());
}

void UuidSet::decode(framing::Buffer& b) {
    size_t n = b.getLong();
    for ( ; n > 0; --n) {
        types::Uuid id;
        b.getRawData(const_cast<unsigned char*>(id.data()), id.size());
        insert(id);
    }
}

size_t UuidSet::encodedSize() const {
    return sizeof(uint32_t) + size()*16;
}


}} // namespace qpid::ha
