#ifndef QPID_CLUSTER_HANDLERBASE_H
#define QPID_CLUSTER_HANDLERBASE_H

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
#include "qpid/cluster/types.h"

namespace qpid {

namespace framing {
class AMQBody;
class AMQFrame;
}

namespace cluster {
class EventHandler;

/**
 * Base class for handlers of events, children of the EventHandler.
 */
class HandlerBase : public RefCounted
{
  public:
    HandlerBase(EventHandler&);
    virtual ~HandlerBase();

    virtual bool handle(const framing::AMQFrame&) = 0;
    virtual void left(const MemberId&) {}
    virtual void joined(const MemberId&) {}

  protected:
    EventHandler& eventHandler;
    MemberId sender();
    MemberId self();
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_HANDLERBASE_H*/
