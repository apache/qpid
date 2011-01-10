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

#include "qmf/AgentSubscription.h"

using namespace qmf;

AgentSubscription::AgentSubscription(uint64_t _id, uint64_t _interval, uint64_t _life, 
                                     const std::string& _replyTo, const std::string& _cid, Query _query) :
    id(_id), interval(_interval), lifetime(_life), timeSincePublish(0), timeSinceKeepalive(0),
    replyTo(_replyTo), cid(_cid), query(_query)
{
}


AgentSubscription::~AgentSubscription()
{
}


bool AgentSubscription::tick(uint64_t seconds)
{
    timeSinceKeepalive += seconds;
    if (timeSinceKeepalive >= lifetime)
        return false;

    timeSincePublish += seconds;
    if (timeSincePublish >= interval) {
    }

    return true;
}

