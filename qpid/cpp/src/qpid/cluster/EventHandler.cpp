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

#include "MessageHandler.h"
#include "EventHandler.h"
#include "Core.h"
#include "types.h"
#include "qpid/framing/Buffer.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/AllInvoker.h"
#include "qpid/broker/Broker.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace cluster {

EventHandler::EventHandler(Core& c) :
    core(c),
    cpg(*this),                 // FIXME aconway 2010-10-20: belongs on Core.
    dispatcher(cpg, core.getBroker().getPoller(), boost::bind(&Core::fatal, &core)),
    self(cpg.self()),
    messageHandler(new MessageHandler(*this))
{
    dispatcher.start();         // FIXME aconway 2010-10-20: later in initialization?
}

EventHandler::~EventHandler() {}

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
    while (buf.available()) {
        frame.decode(buf);
        assert(frame.getBody());
        QPID_LOG(trace, "cluster deliver: " << *frame.getBody());
        try {
            invoke(*frame.getBody());
        }
        catch (const std::exception& e) {
            // Note: exceptions are assumed to be survivable,
            // fatal errors should log a message and call Core::fatal.
            QPID_LOG(error, e.what());
        }
    }
}

void EventHandler::invoke(const framing::AMQBody& body) {
    if (framing::invoke(*messageHandler, body).wasHandled()) return;
}

// CPG config-change callback.
void EventHandler::configChange (
    cpg_handle_t /*handle*/,
    const cpg_name */*group*/,
    const cpg_address */*members*/, int /*nMembers*/,
    const cpg_address */*left*/, int /*nLeft*/,
    const cpg_address */*joined*/, int /*nJoined*/)
{
    // FIXME aconway 2010-10-20: TODO
}

}} // namespace qpid::cluster
