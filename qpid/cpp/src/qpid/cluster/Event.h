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
#include "qpid/RefCountedBuffer.h"
#include "qpid/sys/LatencyMetric.h"
#include <sys/uio.h>            // For iovec
#include <iosfwd>

#include "types.h"

namespace qpid {

namespace framing {
class AMQBody;
class AMQFrame;
class Buffer;
}

namespace cluster {

/** Header data for a multicast event */
class EventHeader : public ::qpid::sys::LatencyMetricTimestamp {
  public:
    EventHeader(EventType t=DATA, const ConnectionId& c=ConnectionId(), size_t size=0);
    void decode(const MemberId& m, framing::Buffer&);
    void encode(framing::Buffer&) const;

    EventType getType() const { return type; }
    ConnectionId getConnectionId() const { return connectionId; }
    MemberId getMemberId() const { return connectionId.getMember(); }

    /** Size of payload data, excluding header. */
    size_t getSize() const { return size; }
    /** Size of header + payload. */ 
    size_t getStoreSize() { return size + HEADER_SIZE; }

    uint64_t getSequence() const { return sequence; }
    void setSequence(uint64_t n) { sequence = n; }

    bool isCluster() const { return connectionId.getPointer() == 0; }
    bool isConnection() const { return connectionId.getPointer() != 0; }

  protected:
    static const size_t HEADER_SIZE;
    
    EventType type;
    ConnectionId connectionId;
    size_t size;
    uint64_t sequence;
};

/**
 * Events are sent to/received from the cluster.
 * Refcounted so they can be stored on queues.
 */
class Event : public EventHeader {
  public:
    Event();
    /** Create an event with a buffer that can hold size bytes plus an event header. */
    Event(EventType t, const ConnectionId& c, size_t);

    /** Create an event copied from delivered data. */
    static Event decodeCopy(const MemberId& m, framing::Buffer&);

    /** Create a control event. */
    static Event control(const framing::AMQBody&, const ConnectionId&);

    /** Create a control event. */
    static Event control(const framing::AMQFrame&, const ConnectionId&);
    
    // Data excluding header.
    char* getData() { return store + HEADER_SIZE; }
    const char* getData() const { return store + HEADER_SIZE; }

    // Store including header
    char* getStore() { return store; }
    const char* getStore() const { return store; }
    
    operator framing::Buffer() const;

    iovec toIovec();
    
  private:
    void encodeHeader();

    RefCountedBuffer::pointer store;
};

std::ostream& operator << (std::ostream&, const EventHeader&);

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_EVENT_H*/
