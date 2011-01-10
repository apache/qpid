#ifndef _QMF_AGENT_SUBSCRIPTION_H_
#define _QMF_AGENT_SUBSCRIPTION_H_
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

#include "qpid/sys/IntegerTypes.h"
#include "qpid/types/Variant.h"
#include "qmf/Query.h"
#include "qmf/Data.h"
#include <boost/shared_ptr.hpp>

namespace qmf {
    class AgentSubscription {
    public:
        AgentSubscription(uint64_t _id, uint64_t _interval, uint64_t _life, 
                          const std::string& _replyTo, const std::string& _cid, Query _query);
        ~AgentSubscription();
        bool tick(uint64_t seconds);
        void keepalive() { timeSinceKeepalive = 0; }

    private:
        uint64_t id;
        uint64_t interval;
        uint64_t lifetime;
        uint64_t timeSincePublish;
        uint64_t timeSinceKeepalive;
        const std::string replyTo;
        const std::string cid;
        Query query;
    };

}

#endif
