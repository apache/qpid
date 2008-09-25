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

#include "Cluster.h"
#include "ClusterHandler.h"

#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/AllInvoker.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/log/Statement.h"



namespace qpid {
namespace cluster {

struct Operations : public framing::AMQP_AllOperations::ClusterHandler {
    qpid::cluster::ClusterHandler& handler;
    MemberId member;
    Operations(qpid::cluster::ClusterHandler& c, const MemberId& id) : handler(c), member(id) {}

    void update(const framing::FieldTable& members, uint64_t dumping) { handler.update(member, members, dumping); }
    void dumpRequest(const std::string& url) { handler.dumpRequest(member, url); }
    void ready(const std::string& url) { handler.ready(member, url); }
    void shutdown() { handler.shutdown(member); }
};

ClusterHandler::~ClusterHandler() {}

ClusterHandler::ClusterHandler(Cluster& c) : cluster (c) {}

bool ClusterHandler::invoke(const MemberId& id, framing::AMQFrame& frame) {
    Operations ops(*this, id);
    return framing::invoke(ops, *frame.getBody()).wasHandled(); 
}

void ClusterHandler::shutdown(const MemberId& id) {
    QPID_LOG(notice, cluster.self << " received shutdown from " << id);
    cluster.leave();
}


}} // namespace qpid::cluster

