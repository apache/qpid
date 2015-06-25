#ifndef QPID_HA_STANDALONE_H
#define QPID_HA_STANDALONE_H

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
namespace qpid {
struct Url;

namespace ha {

/**
 * Stand-alone role: acts as a stand-alone broker, no clustering.
 * HA module needed to setting up replication via QMF methods.
 */
class StandAlone : public Role
{
  public:
    Role* promote() { return 0; }
    void setBrokerUrl(const Url&) {}
};
}} // namespace qpid::ha

#endif  /*!QPID_HA_STANDALONE_H*/
