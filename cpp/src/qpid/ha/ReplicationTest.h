#ifndef QPID_HA_REPLICATIONTEST_H
#define QPID_HA_REPLICATIONTEST_H

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
#include "qpid/types/Variant.h"
#include <string>

namespace qpid {

namespace broker {
class Queue;
class Exchange;
}

namespace framing {
class FieldTable;
}

namespace ha {
/**
 * Test whether something is replicated, taking into account the
 * default replication level.
 *
 * The primary uses a ReplicationTest with default based on configuration
 * settings, and marks objects to be replicated with an explict replication
 * argument.
 *
 * The backup uses a default of NONE, so it always accepts what the primary has
 * marked on the object.
 */
class ReplicationTest
{
  public:
    ReplicationTest(ReplicateLevel replicateDefault_) :
        replicateDefault(replicateDefault_) {}

    // Get the replication level set on an object, or default if not set.
    ReplicateLevel getLevel(const std::string& str) const;
    ReplicateLevel getLevel(const framing::FieldTable& f) const;
    ReplicateLevel getLevel(const types::Variant::Map& m) const;
    ReplicateLevel getLevel(const broker::Queue&) const;
    ReplicateLevel getLevel(const broker::Exchange&) const;

    // Calculate level for objects that may not have replication set,
    // including auto-delete/exclusive settings.
    ReplicateLevel useLevel(const broker::Queue&) const;
    ReplicateLevel useLevel(const broker::Exchange&) const;

  private:
    ReplicateLevel replicateDefault;
};

}} // namespace qpid::ha

#endif  /*!QPID_HA_REPLICATIONTEST_H*/
