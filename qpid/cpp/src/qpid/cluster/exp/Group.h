#ifndef QPID_CLUSTER_EXP_GROUP_H
#define QPID_CLUSTER_EXP_GROUP_H

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

#include "qpid/RefCounted.h"
#include <memory>

namespace qpid {
namespace framing {
class AMQBody;
class AMQFrame;
}

namespace cluster {

class Cpg;
class Core;
class EventHandler;
class Multicaster;

/**
 * A CPG instance with an event handler and a multi-caster, 
 * along with all the per-group handler objects.
 */
class Group : public RefCounted
{
  public:
    Group(Core& core);
    ~Group();

    EventHandler& getEventHandler() { return *eventHandler; }
    Multicaster& getMulticaster() { return *multicaster; }

    void mcast(const framing::AMQBody&);
    void mcast(const framing::AMQFrame&);
  private:
    std::auto_ptr<EventHandler> eventHandler;
    std::auto_ptr<Multicaster> multicaster;
};
}} // namespace qpid::cluster::exp

#endif  /*!QPID_CLUSTER_EXP_GROUP_H*/
