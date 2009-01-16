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

/** Header data for a multicast event */
class EventHeader {
  public:
    EventHeader(EventType t=DATA, const ConnectionId& c=ConnectionId(), size_t size=0);
    void decode(const MemberId& m, framing::Buffer&);
    void encode(framing::Buffer&) const;

    EventType getType() const { return type; }
    ConnectionId getConnectionId() const { return connectionId; }
    MemberId getMemberId() const { return connectionId.getMember(); }
    size_t getSize() const { return size; }

    bool isCluster() const { return connectionId.getPointer() == 0; }
    bool isConnection() const { return connectionId.getPointer() != 0; }

  protected:
    static const size_t HEADER_SIZE;
    
    EventType type;
    ConnectionId connectionId;
    size_t size;
};

/**
 * Events are sent to/received from the cluster.
 * Refcounted so they can be stored on queues.
 */
class Event : public EventHeader {
  public:
    /** Create an event with a buffer that can hold size bytes plus an event header. */
    Event(EventType t=DATA, const ConnectionId& c=ConnectionId(), size_t size=0);

    /** Create an event copied from delivered data. */
    static Event decodeCopy(const MemberId& m, framing::Buffer&);

    /** Create an event containing a control */
    static Event control(const framing::AMQBody&, const ConnectionId&);
    
    // Data excluding header.
    char* getData() { return store + HEADER_SIZE; }
    const char* getData() const { return store + HEADER_SIZE; }

    // Store including header
    char* getStore() { return store; }
    const char* getStore() const { return store; }
    size_t getStoreSize() { return size + HEADER_SIZE; }
    
    operator framing::Buffer() const;

  private:
    void encodeHeader();

    RefCountedBuffer::pointer store;
};

std::ostream& operator << (std::ostream&, const EventHeader&);
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_EVENT_H*/
