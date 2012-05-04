#ifndef QPID_HA_REPLICATELEVEL_H
#define QPID_HA_REPLICATELEVEL_H

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

#include <string>
#include <iosfwd>

namespace qpid {
namespace ha {

enum ReplicateLevel { RL_NONE, RL_CONFIGURATION, RL_ALL };

/**
 * If str is a valid replicate level, set out and return true.
 */
bool replicateLevel(const std::string& str, ReplicateLevel& out);

/**
 *@return enum corresponding to string level.
 *@throw qpid::Exception if level is not a valid replication level.
 */
ReplicateLevel replicateLevel(const std::string& level);

/**@return string form of replicate level */
std::string str(ReplicateLevel l);

std::ostream& operator<<(std::ostream&, ReplicateLevel);
std::istream& operator>>(std::istream&, ReplicateLevel&);

}} // namespaces qpid::ha

#endif  /*!QPID_HA_REPLICATELEVEL_H*/
