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
namespace broker {
class Broker;
class Queue;
class Selector;
namespace amqp {
class ManagedSession;
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

/**
 *
 */
class Outgoing : public qpid::broker::Consumer, public boost::enable_shared_from_this<Outgoing>, public ManagedOutgoingLink
{
  public:
    Outgoing(Broker&,boost::shared_ptr<Queue> q, pn_link_t* l, ManagedSession&, qpid::sys::OutputControl& o, bool topic);
    void setSubjectFilter(const std::string&);
    void setSelectorFilter(const std::string&);
    void init();
    bool dispatch();
    void write(const char* data, size_t size);
    void handle(pn_delivery_t* delivery);
    bool canDeliver();
    void detached();

    //Consumer interface:
    bool deliver(const QueueCursor& cursor, const qpid::broker::Message& msg);
    void notify();
    bool accept(const qpid::broker::Message&);
    bool filter(const qpid::broker::Message&);
    void cancel();
    void acknowledged(const qpid::broker::DeliveryRecord&);
    qpid::broker::OwnershipToken* getSession();

  private:

    struct Record
    {
        QueueCursor cursor;
        qpid::broker::Message msg;
        pn_delivery_t* delivery;
        int disposition;
        size_t index;
        pn_delivery_tag_t tag;

        Record();
        void init(size_t i);
        void reset();
    };

    const bool exclusive;
    boost::shared_ptr<Queue> queue;
    CircularArray<Record> deliveries;
    pn_link_t* link;
    qpid::sys::OutputControl& out;
    size_t current;
    int outstanding;
    std::vector<char> buffer;
    std::string subjectFilter;
    boost::scoped_ptr<Selector> selector;
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP1_OUTGOING_H*/
