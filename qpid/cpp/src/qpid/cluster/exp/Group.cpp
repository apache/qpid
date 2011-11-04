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
#include "Group.h"
#include "MessageBuilders.h"
#include "MessageHolder.h"
#include "Multicaster.h"
#include "Ticker.h"
#include "qpid/broker/Broker.h"

namespace qpid {
namespace framing {
class AMQFrame;
class AMQBody;
}

namespace cluster {

Group::Group(Core& core) :
    eventHandler(
        new EventHandler(core.getBroker().getPoller(),
                         boost::bind(&Core::fatal, &core))),
    multicaster(
        new Multicaster(eventHandler->getCpg(),
                        core.getBroker().getPoller(),
                        boost::bind(&Core::fatal, &core))),
    messageHolder(new MessageHolder()),
    messageBuilders(new MessageBuilders(&core.getBroker().getStore())),
    ticker(new Ticker(core.getSettings().getTick(),
                      core.getBroker().getTimer(),
                      core.getBroker().getPoller()))
{}

Group::~Group() {}

void Group::mcast(const framing::AMQBody& b) { multicaster->mcast(b); }
void Group::mcast(const framing::AMQFrame& f) { multicaster->mcast(f); }
}} // namespace qpid::cluster::exp
