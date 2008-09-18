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
#include "MemberHandler.h"
#include "Cluster.h"
#include "DumpClient.h"
#include "qpid/log/Statement.h"
#include "qpid/framing/ClusterUpdateBody.h"

namespace qpid {
namespace cluster {

using namespace sys;
using namespace framing;

MemberHandler::MemberHandler(Cluster& c) : ClusterHandler(c) {}

void MemberHandler::configChange(
    cpg_address */*current*/, int /*nCurrent*/,
    cpg_address */*left*/, int /*nLeft*/,
    cpg_address */*joined*/, int nJoined)
{
    if (nJoined && cluster.map.sendUpdate(cluster.self))  // New members need update
        cluster.mcastControl(cluster.map.toControl(), 0);
}

void MemberHandler::deliver(Event& e) {
    e.setConnection(cluster.getConnection(e.getConnectionId())); 
    cluster.connectionEventQueue.push(e);
}

void MemberHandler::update(const MemberId&, const framing::FieldTable& , uint64_t) {}

void MemberHandler::dumpRequest(const MemberId& dumpee, const std::string& urlStr) {
    if (cluster.map.dumper) return; // dump in progress, ignore request.

    cluster.map.dumper = cluster.map.first();
    if (cluster.map.dumper != cluster.self) return;
    
    QPID_LOG(info, cluster.self << " sending state dump to " << dumpee);
    assert(!cluster.connectionEventQueue.isStopped()); // Not currently stalled.
    cluster.stall();

    cluster.ready();            // FIXME aconway 2008-09-18: Bypass dump
    (void)urlStr;
//     dumpThread = Thread(new DumpClient(Url(urlStr), cluster.broker,
//                             boost::bind(&MemberHandler::dumpDone, this),
//                             boost::bind(&MemberHandler::dumpError, this, _1)));
}

void MemberHandler::ready(const MemberId& id, const std::string& url) {
    cluster.map.ready(id, Url(url));
}


void MemberHandler::dumpDone() {
    dumpThread.join();          // Clean up.
    cluster.ready();
}

void MemberHandler::dumpError(const std::exception& e) {
    QPID_LOG(error, "Error in state dump from " << cluster.self << ": " << e.what());
    dumpDone();
}

}} // namespace qpid::cluster
