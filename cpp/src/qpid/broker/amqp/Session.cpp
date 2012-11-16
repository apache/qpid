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
#include "Session.h"
#include "Outgoing.h"
#include "Message.h"
#include "ManagedConnection.h"
#include "qpid/amqp/CharSequence.h"
#include "qpid/amqp/Descriptor.h"
#include "qpid/amqp/descriptors.h"
#include "qpid/broker/AsyncCompletion.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/DeliverableMessage.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/DirectExchange.h"
#include "qpid/broker/TopicExchange.h"
#include "qpid/broker/FanOutExchange.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/TopicExchange.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/log/Statement.h"
#include <boost/intrusive_ptr.hpp>
#include <boost/format.hpp>
#include <map>
#include <sstream>
extern "C" {
#include <proton/engine.h>
}

namespace qpid {
namespace broker {
namespace amqp {

class Target
{
  public:
    virtual ~Target() {}
    virtual void flow() = 0;
    virtual void handle(qpid::broker::Message& m) = 0;//TODO: revise this for proper message
  private:
};

class Queue : public Target
{
  public:
    Queue(boost::shared_ptr<qpid::broker::Queue> q, pn_link_t* l) : queue(q), link(l) {}
    void flow();
    void handle(qpid::broker::Message& m);
  private:
    boost::shared_ptr<qpid::broker::Queue> queue;
    pn_link_t* link;
};

class Exchange : public Target
{
  public:
    Exchange(boost::shared_ptr<qpid::broker::Exchange> e, pn_link_t* l) : exchange(e), link(l) {}
    void flow();
    void handle(qpid::broker::Message& m);
  private:
    boost::shared_ptr<qpid::broker::Exchange> exchange;
    pn_link_t* link;
};

Session::Session(pn_session_t* s, qpid::broker::Broker& b, ManagedConnection& c, qpid::sys::OutputControl& o)
    : ManagedSession(b, c, (boost::format("%1%") % s).str()), session(s), broker(b), connection(c), out(o), deleted(false) {}

void Session::attach(pn_link_t* link)
{
    if (pn_link_is_sender(link)) {
        pn_terminus_t* source = pn_link_remote_source(link);
        //i.e a subscription
        if (pn_terminus_get_type(source) == PN_UNSPECIFIED) {
            throw qpid::Exception("No source specified!");/*invalid-field?*/
        }
        std::string name = pn_terminus_get_address(source);
        QPID_LOG(debug, "Received attach request for outgoing link from " << name);
        pn_terminus_set_address(pn_link_source(link), name.c_str());

        boost::shared_ptr<qpid::broker::Exchange> exchange = broker.getExchanges().find(name);
        boost::shared_ptr<qpid::broker::Queue> queue = broker.getQueues().find(name);
        if (queue) {
            if (exchange) {
                QPID_LOG_CAT(warning, protocol, "Ambiguous node name; " << name << " could be queue or exchange, assuming queue");
            }
            boost::shared_ptr<Outgoing> q(new Outgoing(broker, queue, link, *this, out, false));
            q->init();
            senders[link] = q;
        } else if (exchange) {
            QueueSettings settings(false, true);
            //TODO: populate settings from source details when available from engine
            queue = broker.createQueue(name + qpid::types::Uuid(true).str(), settings, this, "", connection.getUserid(), connection.getId()).first;
            pn_data_t* filter = pn_terminus_filter(source);
            pn_data_next(filter);
            if (filter && !pn_data_is_null(filter)) {
                if (pn_data_type(filter) == PN_MAP) {
                    pn_data_t* echo = pn_terminus_filter(pn_link_source(link));
                    pn_data_put_map(echo);
                    pn_data_enter(echo);
                    size_t count = pn_data_get_map(filter)/2;
                    QPID_LOG(debug, "Got filter map with " << count << " entries");
                    pn_data_enter(filter);
                    for (size_t i = 0; i < count; i++) {
                        pn_bytes_t fname = pn_data_get_symbol(filter);
                        pn_data_next(filter);
                        bool isDescribed = pn_data_is_described(filter);
                        qpid::amqp::Descriptor descriptor(0);
                        if (isDescribed) {
                            pn_data_enter(filter);
                            pn_data_next(filter);
                            //TODO: use or at least verify descriptor
                            if (pn_data_type(filter) == PN_ULONG) {
                                descriptor = qpid::amqp::Descriptor(pn_data_get_ulong(filter));
                            } else if (pn_data_type(filter) == PN_SYMBOL) {
                                pn_bytes_t d = pn_data_get_symbol(filter);
                                qpid::amqp::CharSequence c;
                                c.data = d.start;
                                c.size = d.size;
                                descriptor = qpid::amqp::Descriptor(c);
                            } else {
                                QPID_LOG(notice, "Ignoring filter " << std::string(fname.start, fname.size) << " with descriptor of type " << pn_data_type(filter));
                                continue;
                            }
                            if (descriptor.match(qpid::amqp::filters::LEGACY_TOPIC_FILTER_SYMBOL, qpid::amqp::filters::LEGACY_TOPIC_FILTER_CODE)) {
                                if (exchange->getType() == qpid::broker::DirectExchange::typeName) {
                                    QPID_LOG(info, "Interpreting legacy topic filter as direct binding key for " << exchange->getName());
                                } else if (exchange->getType() == qpid::broker::FanOutExchange::typeName) {
                                    QPID_LOG(info, "Ignoring legacy topic filter on fanout exchange " << exchange->getName());
                                    for (int i = 0; i < 3; ++i) pn_data_next(filter);//move off descriptor, then skip key and value
                                    continue;
                                }
                            } else if (descriptor.match(qpid::amqp::filters::LEGACY_DIRECT_FILTER_SYMBOL, qpid::amqp::filters::LEGACY_DIRECT_FILTER_CODE)) {
                                if (exchange->getType() == qpid::broker::TopicExchange::typeName) {
                                    QPID_LOG(info, "Interpreting legacy direct filter as topic binding key for " << exchange->getName());
                                } else if (exchange->getType() == qpid::broker::FanOutExchange::typeName) {
                                    QPID_LOG(info, "Ignoring legacy direct filter on fanout exchange " << exchange->getName());
                                    for (int i = 0; i < 3; ++i) pn_data_next(filter);//move off descriptor, then skip key and value
                                    continue;
                                }
                            } else {
                                QPID_LOG(notice, "Ignoring filter with unsupported descriptor " << descriptor);
                                for (int i = 0; i < 3; ++i) pn_data_next(filter);//move off descriptor, then skip key and value
                                continue;
                            }
                            pn_data_next(filter);
                        } else {
                            QPID_LOG(info, "Got undescribed filter of type " << pn_data_type(filter));
                        }
                        if (pn_data_type(filter) == PN_STRING) {
                            pn_bytes_t value = pn_data_get_string(filter);
                            pn_data_next(filter);
                            exchange->bind(queue, std::string(value.start, value.size), 0);
                            pn_data_put_symbol(echo, fname);
                            if (isDescribed) {
                                pn_data_put_described(echo);
                                pn_data_enter(echo);
                                pn_bytes_t symbol;
                                switch (descriptor.type) {
                                  case qpid::amqp::Descriptor::NUMERIC:
                                    pn_data_put_ulong(echo, descriptor.value.code);
                                    break;
                                  case qpid::amqp::Descriptor::SYMBOLIC:
                                    symbol.start = const_cast<char*>(descriptor.value.symbol.data);
                                    symbol.size = descriptor.value.symbol.size;
                                    pn_data_put_symbol(echo, symbol);
                                    break;
                                }
                            }
                            pn_data_put_string(echo, value);
                            if (isDescribed) pn_data_exit(echo);

                            QPID_LOG(debug, "Binding using filter " << std::string(fname.start, fname.size) << ":" << std::string(value.start, value.size));
                        } else {
                            //TODO: handle headers exchange filters
                            QPID_LOG(warning, "Ignoring unsupported filter type with key " << std::string(fname.start, fname.size) << " and type " << pn_data_type(filter));
                        }
                    }
                    pn_data_exit(echo);
                } else {
                    QPID_LOG(warning, "Filter should be map, got type: " << pn_data_type(filter));
                }
            } else if (exchange->getType() == FanOutExchange::typeName) {
                exchange->bind(queue, std::string(), 0);
            } else if (exchange->getType() == TopicExchange::typeName) {
                exchange->bind(queue, "#", 0);
            } else {
                throw qpid::Exception("Exchange type requires a filter: " + exchange->getType());/*not-supported?*/
            }
            boost::shared_ptr<Outgoing> q(new Outgoing(broker, queue, link, *this, out, true));
            senders[link] = q;
            q->init();
        } else {
            //TODO: handle dynamic creation
            pn_terminus_set_type(pn_link_source(link), PN_UNSPECIFIED);
            throw qpid::Exception("Node not found: " + name);/*not-found*/
        }
        QPID_LOG(debug, "Outgoing link attached");
    } else {
        pn_terminus_t* target = pn_link_remote_target(link);
        if (pn_terminus_get_type(target) == PN_UNSPECIFIED) {
            throw qpid::Exception("No target specified!");/*invalid field?*/
        }
        std::string name = pn_terminus_get_address(target);
        QPID_LOG(debug, "Received attach request for incoming link to " << name);
        pn_terminus_set_address(pn_link_target(link), name.c_str());


        boost::shared_ptr<qpid::broker::Queue> queue = broker.getQueues().find(name);
        boost::shared_ptr<qpid::broker::Exchange> exchange = broker.getExchanges().find(name);
        if (queue) {
            if (exchange) {
                QPID_LOG_CAT(warning, protocol, "Ambiguous node name; " << name << " could be queue or exchange, assuming queue");
            }
            boost::shared_ptr<Target> q(new Queue(queue, link));
            targets[link] = q;
            q->flow();
        } else if (exchange) {
            boost::shared_ptr<Target> e(new Exchange(exchange, link));
            targets[link] = e;
            e->flow();
        } else {
            //TODO: handle dynamic creation
            pn_terminus_set_type(pn_link_target(link), PN_UNSPECIFIED);
            throw qpid::Exception("Node not found: " + name);/*not-found*/
        }
        QPID_LOG(debug, "Incoming link attached");
    }
}

void Session::detach(pn_link_t* link)
{
    if (pn_link_is_sender(link)) {
        Senders::iterator i = senders.find(link);
        if (i != senders.end()) {
            i->second->detached();
            senders.erase(i);
            QPID_LOG(debug, "Outgoing link detached");
        }
    } else {
        targets.erase(link);
        QPID_LOG(debug, "Incoming link detached");
    }
}
namespace {
    class Transfer : public qpid::broker::AsyncCompletion::Callback
    {
      public:
        Transfer(pn_delivery_t* d, boost::shared_ptr<Session> s) : delivery(d), session(s) {}
        void completed(bool sync) { session->accepted(delivery, sync); }
        boost::intrusive_ptr<qpid::broker::AsyncCompletion::Callback> clone()
        {
            boost::intrusive_ptr<qpid::broker::AsyncCompletion::Callback> copy(new Transfer(delivery, session));
            return copy;
        }
      private:
        pn_delivery_t* delivery;
        boost::shared_ptr<Session> session;
    };
}

void Session::accepted(pn_delivery_t* delivery, bool sync)
{
    if (sync) {
        //this is on IO thread
        pn_delivery_update(delivery, PN_ACCEPTED);
        pn_delivery_settle(delivery);//do we need to check settlement modes/orders?
        incomingMessageAccepted();
    } else {
        //this is not on IO thread, need to delay processing until on IO thread
        qpid::sys::Mutex::ScopedLock l(lock);
        if (!deleted) {
            completed.push_back(delivery);
            out.activateOutput();
        }
    }
}

void Session::incoming(pn_link_t* link, pn_delivery_t* delivery)
{
    pn_delivery_tag_t tag = pn_delivery_tag(delivery);
    QPID_LOG(debug, "received delivery: " << std::string(tag.bytes, tag.size));
    boost::intrusive_ptr<Message> received(new Message(pn_delivery_pending(delivery)));
    /*ssize_t read = */pn_link_recv(link, received->getData(), received->getSize());
    received->scan();
    pn_link_advance(link);

    qpid::broker::Message message(received, received);

    incomingMessageReceived();
    Targets::iterator target = targets.find(link);
    if (target == targets.end()) {
        QPID_LOG(error, "Received message on unknown link");
        pn_delivery_update(delivery, PN_REJECTED);
        pn_delivery_settle(delivery);//do we need to check settlement modes/orders?
        incomingMessageRejected();
    } else {
        target->second->handle(message);
        received->begin();
        Transfer t(delivery, shared_from_this());
        received->end(t);
        target->second->flow();
    }
}
void Session::outgoing(pn_link_t* link, pn_delivery_t* delivery)
{
    Senders::iterator sender = senders.find(link);
    if (sender == senders.end()) {
        QPID_LOG(error, "Delivery returned for unknown link");
    } else {
        sender->second->handle(delivery);
    }
}

bool Session::dispatch()
{
    bool output(false);
    for (Senders::iterator s = senders.begin(); s != senders.end(); ++s) {
        if (s->second->dispatch()) output = true;
    }
    if (completed.size()) {
        output = true;
        std::deque<pn_delivery_t*> copy;
        {
            qpid::sys::Mutex::ScopedLock l(lock);
            completed.swap(copy);
        }
        for (std::deque<pn_delivery_t*>::iterator i = copy.begin(); i != copy.end(); ++i) {
            accepted(*i, true);
        }
    }

    return output;
}

void Session::close()
{
    for (Senders::iterator i = senders.begin(); i != senders.end(); ++i) {
        i->second->detached();
    }
    senders.clear();
    targets.clear();//at present no explicit cleanup required for targets
    QPID_LOG(debug, "Session closed, all senders cancelled.");
    qpid::sys::Mutex::ScopedLock l(lock);
    deleted = true;
}

void Queue::flow()
{
    pn_link_flow(link, 1);//TODO: proper flow control
}

void Queue::handle(qpid::broker::Message& message)
{
    queue->deliver(message);
}

void Exchange::flow()
{
    pn_link_flow(link, 1);//TODO: proper flow control
}

void Exchange::handle(qpid::broker::Message& message)
{
    DeliverableMessage deliverable(message, 0);
    exchange->route(deliverable);
}
}}} // namespace qpid::broker::amqp
