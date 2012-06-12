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
#include "ReplicationTest.h"
#include "qpid/broker/Queue.h"
#include "qpid/framing/FieldTable.h"

namespace qpid {
namespace ha {

using types::Variant;

ReplicateLevel ReplicationTest::replicateLevel(const std::string& str) {
    Enum<ReplicateLevel> rl;
    if (rl.parseNoThrow(str)) return ReplicateLevel(rl.get());
    else return replicateDefault;
}

ReplicateLevel ReplicationTest::replicateLevel(const framing::FieldTable& f) {
    if (f.isSet(QPID_REPLICATE))
        return replicateLevel(f.getAsString(QPID_REPLICATE));
    else
        return replicateDefault;
}

ReplicateLevel ReplicationTest::replicateLevel(const Variant::Map& m) {
    Variant::Map::const_iterator i = m.find(QPID_REPLICATE);
    if (i != m.end())
        return replicateLevel(i->second.asString());
    else
        return replicateDefault;
}

namespace {
const std::string AUTO_DELETE_TIMEOUT("qpid.auto_delete_timeout");
}

bool ReplicationTest::isReplicated(
    ReplicateLevel level, const Variant::Map& args, bool autodelete, bool exclusive)
{
    bool ignore = autodelete && exclusive && args.find(AUTO_DELETE_TIMEOUT) == args.end();
    return !ignore && replicateLevel(args) >= level;
}

bool ReplicationTest::isReplicated(
    ReplicateLevel level, const framing::FieldTable& args, bool autodelete, bool exclusive)
{
    bool ignore = autodelete && exclusive && !args.isSet(AUTO_DELETE_TIMEOUT);
    return !ignore && replicateLevel(args) >= level;
}

bool ReplicationTest::isReplicated(ReplicateLevel level, const broker::Queue& q)
{
    return isReplicated(level, q.getSettings(), q.isAutoDelete(), q.hasExclusiveOwner());
}


}} // namespace qpid::ha
