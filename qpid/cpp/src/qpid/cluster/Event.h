#ifndef QPID_CLUSTER_EVENT_H
#define QPID_CLUSTER_EVENT_H

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

#include "types.h"
#include "Cpg.h"
#include "Connection.h"
#include "qpid/RefCountedBuffer.h"
#include "qpid/framing/Buffer.h"
#include <iosfwd>

namespace qpid {
namespace cluster {

// TODO aconway 2008-09-03: more efficient solution for shared
// byte-stream data.
// 

/**
 * Events are sent to/received from the cluster.
 * Refcounted so they can be stored on queues.
 */
class Event {
  public:
    /** Create an event to mcast with a buffer of size bytes. */
    Event(EventType t=DATA, const ConnectionId& c=ConnectionId(), size_t size=0, size_t id=0);

    /** Create an event copied from delivered data. */
    static Event delivered(const MemberId& m, void* data, size_t size);
    
    void mcast(const Cpg::Name& name, Cpg& cpg) const;
    
    EventType getType() const { return type; }
    ConnectionId getConnectionId() const { return connectionId; }
    size_t getSize() const { return size; }
    char* getData() { return data; }
    const char* getData() const { return data; }
    size_t getId() const { return id; }

    operator framing::Buffer() const;

  private:
    static const size_t OVERHEAD;
    EventType type;
    ConnectionId connectionId;
    size_t size;
    RefCountedBuffer::pointer data;
    size_t id;
};

std::ostream& operator << (std::ostream&, const Event&);
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_EVENT_H*/
