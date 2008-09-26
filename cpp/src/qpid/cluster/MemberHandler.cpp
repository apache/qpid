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
#include "qpid/framing/enum.h"

namespace qpid {
namespace cluster {

using namespace sys;
using namespace framing;

MemberHandler::MemberHandler(Cluster& c) : ClusterHandler(c) {}

MemberHandler::~MemberHandler() { 
    if (dumpThread.id()) 
        dumpThread.join(); // Join the last dumpthread.
}

void MemberHandler::configChange(
    cpg_address */*current*/, int /*nCurrent*/,
    cpg_address */*left*/, int /*nLeft*/,
    cpg_address */*joined*/, int nJoined)
{
    // FIXME aconway 2008-09-24: Called with lock held - volatile
    if (nJoined && cluster.map.sendUpdate(cluster.self))  // New members need update
        cluster.mcastControl(cluster.map.toControl(), 0);
}

void MemberHandler::deliver(Event& e) {
    cluster.connectionEventQueue.push(e);
}

// Updates are for new joiners.
void MemberHandler::update(const MemberId&, const framing::FieldTable& , uint64_t) {}

void MemberHandler::dumpRequest(const MemberId& dumpee, const std::string& urlStr) {
    Mutex::ScopedLock l(cluster.lock);
    if (cluster.map.dumper) return; // dump in progress, ignore request.

    cluster.map.dumper = cluster.map.first();
    if (cluster.map.dumper != cluster.self) return;
    
    QPID_LOG(info, cluster.self << " sending state dump to " << dumpee);
    assert(!cluster.connectionEventQueue.isStopped()); // Not currently stalled.
    cluster.stall();

    if (dumpThread.id()) 
        dumpThread.join(); // Join the previous dumpthread.
    dumpThread = Thread(new DumpClient(Url(urlStr), cluster,
                            boost::bind(&MemberHandler::dumpSent, this),
                            boost::bind(&MemberHandler::dumpError, this, _1)));
}

void MemberHandler::ready(const MemberId& id, const std::string& urlStr) {
    if (cluster.map.ready(id, Url(urlStr)))
        cluster.updateMemberStats();
}


void MemberHandler::dumpSent() {
    Mutex::ScopedLock l(cluster.lock);
    QPID_LOG(debug, "Finished sending state dump.");
    cluster.ready();
}

void MemberHandler::dumpError(const std::exception& e) {
    QPID_LOG(error, cluster.self << " error sending state dump: " << e.what());
    dumpSent();
}

void MemberHandler::insert(const boost::intrusive_ptr<Connection>& c) {
    Mutex::ScopedLock l(cluster.lock);
    if (c->isCatchUp())         // Not allowed in member mode
        c->getBrokerConnection().close(execution::ERROR_CODE_ILLEGAL_STATE, "Not in catch-up mode.");
    else
        cluster.connections[c->getId()] = c;
}

void MemberHandler::catchUpClosed(const boost::intrusive_ptr<Connection>& c) {
    Mutex::ScopedLock l(cluster.lock);
    QPID_LOG(warning, "Catch-up connection " << c << " closed in member mode");
    assert(0);
}

}} // namespace qpid::cluster
