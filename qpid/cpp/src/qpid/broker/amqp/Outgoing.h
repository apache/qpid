#ifndef QPID_BROKER_AMQP1_OUTGOING_H
#define QPID_BROKER_AMQP1_OUTGOING_H

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
#include "qpid/broker/amqp/Message.h"
#include "qpid/broker/amqp/ManagedOutgoingLink.h"
#include "qpid/broker/Consumer.h"
#include "qpid/broker/QueueObserver.h"

#include <boost/shared_ptr.hpp>
#include <boost/scoped_ptr.hpp>
#include <boost/enable_shared_from_this.hpp>
extern "C" {
#include <proton/engine.h>
}

namespace qpid {
namespace sys {
class OutputControl;
}

namespace framing {
class SequenceSet;
}

namespace broker {
class Broker;
class Queue;
class Selector;

namespace amqp {
class Session;

template <class T>
class CircularArray
{
  public:
    CircularArray(size_t l) : limit(l), data(new T[limit]) {}
    T& operator[](size_t i) { return data[i]; }
    size_t capacity() { return limit; }
    ~CircularArray() { delete [] data; }
  private:
    const size_t limit;
    T* const data;
    size_t next;
};

class Outgoing : public ManagedOutgoingLink
{
  public:
    Outgoing(Broker& broker, Session& parent, const std::string& source, const std::string& target, const std::string& name);
    virtual void setSubjectFilter(const std::string&) = 0;
    virtual void setSelectorFilter(const std::string&) = 0;
    virtual void init() = 0;
    /**
     * Allows the link to initiate any outgoing transfers
     */
    virtual bool doWork() = 0;
    /**
     * Signals that this link has been detached
     */
    virtual void detached(bool closed) = 0;
    /**
     * Called when a delivery is writable
     */
    virtual void handle(pn_delivery_t* delivery) = 0;

    void wakeup();
    virtual ~Outgoing() {}
  protected:
    Session& session;
};

/**
 * Logic for handling an outgoing link from a queue (even if it is a
 * subscription pseduo-queue created by the broker)
 */
class OutgoingFromQueue : public Outgoing, public qpid::broker::Consumer,
                          public boost::enable_shared_from_this<OutgoingFromQueue>,
                          public qpid::broker::QueueObserver
{
  public:
    OutgoingFromQueue(Broker&, const std::string& source, const std::string& target, boost::shared_ptr<Queue> q, pn_link_t* l, Session&,
                      qpid::sys::OutputControl& o, SubscriptionType type, bool exclusive, bool isControllingUser);
    ~OutgoingFromQueue();
    void setSubjectFilter(const std::string&);
    void setSelectorFilter(const std::string&);
    void init();
    bool doWork();
    void write(const char* data, size_t size);
    void handle(pn_delivery_t* delivery);
    bool canDeliver();
    void detached(bool closed);

    // Consumer interface:
    bool deliver(const QueueCursor& cursor, const qpid::broker::Message& msg);
    void notify();
    bool accept(const qpid::broker::Message&);
    bool filter(const qpid::broker::Message&);
    void cancel();
    void acknowledged(const qpid::broker::DeliveryRecord&);
    qpid::broker::OwnershipToken* getSession();
    static boost::shared_ptr<Queue> getExclusiveSubscriptionQueue(Outgoing*);

    // QueueObserver interface
    virtual void enqueued(const qpid::broker::Message&) {};
    virtual void acquired(const qpid::broker::Message&) {};
    virtual void requeued(const qpid::broker::Message&) {};
    virtual void dequeued(const qpid::broker::Message&);
    virtual void consumerAdded(const qpid::broker::Consumer&) {};
    virtual void consumerRemoved(const qpid::broker::Consumer&) {};

  private:

    struct Record
    {
        QueueCursor cursor;
        qpid::broker::Message msg;
        pn_delivery_t* delivery;
        int disposition;
        size_t index;
        pn_delivery_tag_t tag;
        //The delivery tag is a 4 byte value representing the
        //index. It is encoded separately to avoid alignment issues.
        //The number of deliveries held here is always strictly
        //bounded, so 4 bytes is more than enough.
        static const size_t TAG_WIDTH = sizeof(uint32_t);
        char tagData[TAG_WIDTH];

        Record();
        void init(size_t i);
        void reset();
        static size_t getIndex(pn_delivery_tag_t);
    };

    void mergeMessageAnnotationsIfRequired(const Record &r);

    const bool exclusive;
    const bool isControllingUser;
    boost::shared_ptr<Queue> queue;
    CircularArray<Record> deliveries;
    pn_link_t* link;
    qpid::sys::OutputControl& out;
    size_t current;
    std::vector<char> buffer;
    std::string subjectFilter;
    boost::scoped_ptr<Selector> selector;
    bool unreliable;
    bool cancelled;

    bool trackingUndeliverableMessages;
    qpid::framing::SequenceSet undeliverableMessages;
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP1_OUTGOING_H*/
