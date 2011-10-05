#ifndef QPID_CLUSTER_EVENTHANDLER_H
#define QPID_CLUSTER_EVENTHANDLER_H

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

// TODO aconway 2010-10-19: experimental cluster code.

#include "qpid/cluster/Cpg.h"
#include "qpid/cluster/PollerDispatch.h"
#include "qpid/cluster/types.h"
#include <boost/intrusive_ptr.hpp>
#include <vector>

namespace qpid {

namespace framing {
class AMQBody;
class AMQFrame;
}

namespace cluster {
class HandlerBase;

/**
 * Dispatch events received from a CPG group.
 * A container for Handler objects that handle specific cluster.xml classes.
 * Thread unsafe: only called in its own CPG deliver thread context.
 */
class EventHandler : public Cpg::Handler
{
  public:
    EventHandler(boost::shared_ptr<sys::Poller> poller,
                 boost::function<void()> onError);
    ~EventHandler();

    /** Add a handler */
    void add(const boost::intrusive_ptr<HandlerBase>&);

    /** Start polling */
    void start();

    void deliver( // CPG deliver callback.
        cpg_handle_t /*handle*/,
        const struct cpg_name *group,
        uint32_t /*nodeid*/,
        uint32_t /*pid*/,
        void* /*msg*/,
        int /*msg_len*/);

    void configChange( // CPG config change callback.
        cpg_handle_t /*handle*/,
        const struct cpg_name */*group*/,
        const struct cpg_address */*members*/, int /*nMembers*/,
        const struct cpg_address */*left*/, int /*nLeft*/,
        const struct cpg_address */*joined*/, int /*nJoined*/
    );

    MemberId getSender() { return sender; }
    MemberId getSelf() { return self; }
    Cpg& getCpg() { return cpg; }

  private:
    void handle(const framing::AMQFrame&);

    Cpg cpg;
    PollerDispatch dispatcher;
    MemberId sender;              // sender of current event.
    MemberId self;

    typedef std::vector<boost::intrusive_ptr<HandlerBase> > Handlers;
    Handlers handlers;
};
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_EVENTHANDLER_H*/
