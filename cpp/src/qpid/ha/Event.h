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

//// Transaction events

struct TxEnqueueEvent : public EventBase<TxEnqueueEvent> {
    static const std::string KEY;
    framing::LongString queue;
    ReplicationId id;

    TxEnqueueEvent(std::string q=std::string(), ReplicationId i=ReplicationId())
        : queue(q), id(i) {}
    void encode(framing::Buffer& b) const { b.put(queue); b.put(id); }
    void decode(framing::Buffer& b) { b.get(queue); b.get(id); }
    virtual size_t encodedSize() const { return queue.encodedSize()+id.encodedSize(); }
    void print(std::ostream& o) const { o << queue.value << " " << id; }
};

struct TxDequeueEvent : public EventBase<TxDequeueEvent> {
    static const std::string KEY;
    framing::LongString queue;
    ReplicationId id;

    TxDequeueEvent(std::string q=std::string(), ReplicationId r=0) :
        queue(q), id(r) {}
    void encode(framing::Buffer& b) const { b.put(queue);b.put(id); }
    void decode(framing::Buffer& b) { b.get(queue);b.get(id); }
    virtual size_t encodedSize() const { return queue.encodedSize()+id.encodedSize(); }
    void print(std::ostream& o) const { o << queue.value << " " << id; }
};

struct TxPrepareEvent : public EventBase<TxPrepareEvent> {
    static const std::string KEY;
    void encode(framing::Buffer&) const {}
    void decode(framing::Buffer&) {}
    virtual size_t encodedSize() const { return 0; }
    void print(std::ostream&) const {}
};

struct TxCommitEvent : public EventBase<TxCommitEvent> {
    static const std::string KEY;
    void encode(framing::Buffer&) const {}
    void decode(framing::Buffer&) {}
    virtual size_t encodedSize() const { return 0; }
    void print(std::ostream&) const {}
};

struct TxRollbackEvent : public EventBase<TxRollbackEvent> {
    static const std::string KEY;
    void encode(framing::Buffer&) const {}
    void decode(framing::Buffer&) {}
    virtual size_t encodedSize() const { return 0; }
    void print(std::ostream&) const {}
};

struct TxPrepareOkEvent : public EventBase<TxPrepareOkEvent> {
    static const std::string KEY;
    types::Uuid broker;
    TxPrepareOkEvent(const types::Uuid& b=types::Uuid()) : broker(b) {}

    void encode(framing::Buffer& b) const {
        b.putRawData(broker.data(), broker.size());
    }

    void decode(framing::Buffer& b) {
        std::string s;
        b.getRawData(s, broker.size());
        broker = types::Uuid(&s[0]);
    }
    virtual size_t encodedSize() const { return broker.size(); }
    void print(std::ostream& o) const { o << broker; }
};

struct TxPrepareFailEvent : public EventBase<TxPrepareFailEvent> {
    static const std::string KEY;
    types::Uuid broker;
    TxPrepareFailEvent(const types::Uuid& b=types::Uuid()) : broker(b) {}
    void encode(framing::Buffer& b) const { b.putRawData(broker.data(), broker.size()); }
    void decode(framing::Buffer& b) {
        std::string s;
        b.getRawData(s, broker.size());
        broker = types::Uuid(&s[0]);
    }
    virtual size_t encodedSize() const { return broker.size(); }
    void print(std::ostream& o) const { o << broker; }
};

struct TxMembersEvent : public EventBase<TxMembersEvent> {
    static const std::string KEY;
    UuidSet members;
    TxMembersEvent(const UuidSet& s=UuidSet()) : members(s) {}
    void encode(framing::Buffer& b) const { b.put(members); }
    void decode(framing::Buffer& b) { b.get(members); }
    size_t encodedSize() const { return members.encodedSize(); }
    void print(std::ostream& o) const { o << members; }
};

}} // namespace qpid::ha

#endif  /*!QPID_HA_EVENT_H*/
