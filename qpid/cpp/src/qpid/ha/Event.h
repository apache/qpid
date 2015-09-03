#ifndef QPID_HA_EVENT_H
#define QPID_HA_EVENT_H

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
#include "qpid/broker/Message.h"
#include "qpid/framing/BufferTypes.h"

/**@file Defines event messages used to pass transaction information from
 * primary observers to backup replicators.
 */

namespace qpid {
namespace ha {

broker::Message makeMessage(
    const std::string& content,
    const std::string& destination,
    const std::string& routingKey);


/** Test if a string is an event key */
bool isEventKey(const std::string& key);

/** Base class for encodable events */
class Event {
  public:
    virtual ~Event() {}
    virtual void encode(framing::Buffer& buffer) const = 0;
    virtual void decode(framing::Buffer& buffer) = 0;
    virtual size_t encodedSize() const = 0;
    virtual std::string key() const = 0; // Routing key
    virtual void print(std::ostream& o) const = 0;
    broker::Message message(const std::string& destination=std::string()) const {
        return makeMessage(framing::encodeStr(*this), destination, key()); }
};


inline std::ostream& operator<<(std::ostream& o, const Event& e) {
    o << "<" << e.key() << ":";
    e.print(o);
    return o << ">";
}

/** Event base template */
template <class Derived> class EventBase : public Event {
  public:
    std::string key() const { return Derived::KEY; }
};

//////////////// Specific event type

//// QueueReplicator events

struct DequeueEvent : public EventBase<DequeueEvent> {
    static const std::string KEY;
    ReplicationIdSet ids;

    DequeueEvent(ReplicationIdSet ids_=ReplicationIdSet()) :  ids(ids_) {}
    void encode(framing::Buffer& b) const { b.put(ids); }
    void decode(framing::Buffer& b) { b.get(ids); }
    virtual size_t encodedSize() const { return ids.encodedSize(); }
    void print(std::ostream& o) const { o << ids; }
};

struct IdEvent : public EventBase<IdEvent> {
    static const std::string KEY;
    ReplicationId id;

    IdEvent(ReplicationId id_=0) : id(id_) {}
    void encode(framing::Buffer& b) const { b.put(id); }
    void decode(framing::Buffer& b) { b.get(id); }
    virtual size_t encodedSize() const { return id.encodedSize(); }
    void print(std::ostream& o) const { o << id; }
};

}} // namespace qpid::ha

#endif  /*!QPID_HA_EVENT_H*/
