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
#include "JoiningHandler.h"
#include "Cluster.h"
#include "qpid/framing/ClusterDumpRequestBody.h"
#include "qpid/framing/ClusterReadyBody.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace cluster {

using namespace sys;
using namespace framing;

JoiningHandler::JoiningHandler(Cluster& c) : ClusterHandler(c), state(START), catchUpConnections(0) {}

void JoiningHandler::configChange(
    cpg_address *current, int nCurrent,
    cpg_address */*left*/, int nLeft,
    cpg_address */*joined*/, int /*nJoined*/)
{
    if (nLeft == 0 && nCurrent == 1 && *current == cluster.self) { // First in cluster.
        QPID_LOG(notice, cluster.self << " first in cluster.");
        cluster.map.ready(cluster.self, cluster.url);
        cluster.unstall();
    }
}

void JoiningHandler::deliver(Event& e) {
    // Discard connection events unless we are stalled to receive a  dump.
    if (state == STALLED) {
        cluster.connectionEventQueue.push(e);
    }
}

void JoiningHandler::update(const MemberId&, const framing::FieldTable& members, uint64_t dumper) {
    cluster.map.update(members, dumper);
    QPID_LOG(debug, "Cluster update: " << cluster.map);
    checkDumpRequest();
}

void JoiningHandler::checkDumpRequest() {
    if (state == START && !cluster.map.dumper) {
        cluster.broker.getPort(); // ensure the broker is listening.
        state = DUMP_REQUESTED;
        cluster.mcastControl(ClusterDumpRequestBody(framing::ProtocolVersion(), cluster.url.str()), 0);
    }
}

void JoiningHandler::dumpRequest(const MemberId& dumpee, const std::string& ) {
    if (cluster.map.dumper) {   // Already a dump in progress.
        if (dumpee == cluster.self && state == DUMP_REQUESTED)
            state = START;      // Need to make another request.
    }
    else {                      // Start a new dump
        cluster.map.dumper = cluster.map.first();
        QPID_LOG(debug, "Starting dump, dumper=" << cluster.map.dumper <<  " dumpee=" << dumpee);
        if (dumpee == cluster.self) { // My turn
            switch (state) {
              case START:
              case STALLED:
                assert(0); break;

              case DUMP_REQUESTED: 
                QPID_LOG(info, cluster.self << " stalling for dump from " << cluster.map.dumper);
                state = STALLED;
                cluster.stall();
                break;

              case DUMP_COMPLETE:
                cluster.ready();
                break;
            }
        }
    }
}

void JoiningHandler::ready(const MemberId& id, const std::string& url) {
    cluster.map.ready(id, Url(url));
    checkDumpRequest();
}

void JoiningHandler::insert(const boost::intrusive_ptr<Connection>& c) {
    if (c->isCatchUp()) {
        ++catchUpConnections;
        QPID_LOG(debug, "Catch-up connection " << *c << " started, total " << catchUpConnections);
    }
    cluster.connections.insert(Cluster::ConnectionMap::value_type(c->getId(), c));
}

void JoiningHandler::catchUpClosed(const boost::intrusive_ptr<Connection>& c) {
    QPID_LOG(debug, "Catch-up connection " << *c << " finished, remaining " << catchUpConnections-1);
    if (c->isShadow())
        cluster.connections.insert(Cluster::ConnectionMap::value_type(c->getId(), c));
    if (--catchUpConnections == 0)
        dumpComplete();
}

void JoiningHandler::dumpComplete() {
    // FIXME aconway 2008-09-18: need to detect incomplete dump.
    // 
    if (state == STALLED) {
        cluster.ready();
    }
    else {
        QPID_LOG(debug, "Dump complete, waiting for stall point.");
        assert(state == DUMP_REQUESTED);
        state = DUMP_COMPLETE;
    }
}

}} // namespace qpid::cluster
