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

#include "Core.h"
#include "EventHandler.h"
#include "HandlerBase.h"
#include "PrettyId.h"
#include "qpid/broker/Broker.h"
#include "qpid/cluster/types.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/AllInvoker.h"
#include "qpid/framing/Buffer.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace cluster {

EventHandler::EventHandler(boost::shared_ptr<sys::Poller> poller,
                           boost::function<void()> onError) :
    cpg(*this),
    dispatcher(cpg, poller, onError),
    self(cpg.self())
{}

EventHandler::~EventHandler() {}

void EventHandler::add(const boost::intrusive_ptr<HandlerBase>& handler) {
    handlers.push_back(handler);
}

void EventHandler::start() {
    dispatcher.start();
}

// Deliver CPG message.
void EventHandler::deliver(
    cpg_handle_t /*handle*/,
    const cpg_name* /*group*/,
    uint32_t nodeid,
    uint32_t pid,
    void* msg,
    int msg_len)
{
    sender = MemberId(nodeid, pid);
    framing::Buffer buf(static_cast<char*>(msg), msg_len);
    framing::AMQFrame frame;
    // FIXME aconway 2011-09-29: don't decode own frame bodies. Ignore based on channel.
    while (buf.available()) {
        // FIXME aconway 2011-10-19: multi-version, skip unrecognized frames.
        frame.decode(buf);
        QPID_LOG(trace, "cluster: deliver on " << cpg.getName()
                 << " from "<< PrettyId(sender, self) << ": " << frame);
        try {
            handle(frame);
        } catch (const std::exception& e) {
            // FIXME aconway 2011-10-19: error handling.
            QPID_LOG(error, "cluster event: " << e.what()
                     << " (sender=" << PrettyId(sender, self) << " group=" << cpg.getName()
                     << " " << frame << ")");

        }
    }
}

void EventHandler::handle(const framing::AMQFrame& frame) {
    for (Handlers::iterator i = handlers.begin(); i != handlers.end(); ++i)
        if ((*i)->handle(frame)) return;
    QPID_LOG(error, "Cluster received unknown frame: " << frame );
    assert(0);             // FIXME aconway 2011-09-29: Error handling
}

struct PrintAddrs {
    PrintAddrs(const cpg_address* a, int n ) : addrs(a), count(n) {}
    const cpg_address* addrs;
    int count;
};

std::ostream& operator<<(std::ostream& o, const PrintAddrs& pa) {
    for (const cpg_address* a = pa.addrs; a != pa.addrs+pa.count; ++a)
        o << MemberId(*a) << " ";
    return o;
}

// CPG config-change callback.
void EventHandler::configChange (
    cpg_handle_t /*handle*/,
    const cpg_name */*group*/,
    const cpg_address *members, int nMembers,
    const cpg_address *left, int nLeft,
    const cpg_address *joined, int nJoined)
{
    QPID_LOG(notice, "cluster: new membership: " << PrintAddrs(members, nMembers));
    QPID_LOG_IF(notice, nLeft, "cluster:    left: " << PrintAddrs(left, nLeft));
    QPID_LOG_IF(notice, nJoined, "cluster:    joined: " << PrintAddrs(joined, nJoined));
    for (Handlers::iterator i = handlers.begin(); i != handlers.end(); ++i) {
        for (int l = 0; l < nLeft; ++l) (*i)->left(left[l]);
        for (int j = 0; j < nJoined; ++j) (*i)->joined(joined[j]);
    }
}

}} // namespace qpid::cluster
