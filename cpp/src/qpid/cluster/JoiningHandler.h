#ifndef QPID_CLUSTER_JOININGHANDLER_H
#define QPID_CLUSTER_JOININGHANDLER_H

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
#include "ClusterHandler.h"

namespace qpid {
namespace cluster {

/**
 * Cluster handler for the "joining" phase, before the process is a
 * full cluster member.
 */
class JoiningHandler : public ClusterHandler
{
  public:
    JoiningHandler(Cluster& c);
    
    void configChange(struct cpg_address */*members*/, int /*nMembers*/,
                      struct cpg_address */*left*/, int /*nLeft*/,
                      struct cpg_address */*joined*/, int /*nJoined*/
    );

    void deliver(Event& e);
    
    void update(const MemberId&, const framing::FieldTable& members, uint64_t dumping);
    void dumpRequest(const MemberId&, const std::string& url);
    void ready(const MemberId&, const std::string& url);

    void insert(const boost::intrusive_ptr<Connection>& c);
    
  private:
    void checkDumpRequest();
    void dumpComplete();

    enum { START, DUMP_REQUESTED, STALLED, DUMP_COMPLETE } state;
    size_t catchUpConnections;

};
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_JOININGHANDLER_H*/
