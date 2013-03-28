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
#include "Incoming.h"
#include "Outgoing.h"
#include "Message.h"
#include "Connection.h"
#include "Domain.h"
#include "Interconnects.h"
#include "Relay.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/DeliverableMessage.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/DirectExchange.h"
#include "qpid/broker/TopicExchange.h"
#include "qpid/broker/FanOutExchange.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/Selector.h"
#include "qpid/broker/TopicExchange.h"
#include "qpid/broker/amqp/Filter.h"
#include "qpid/broker/amqp/NodeProperties.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/FieldTable.h"
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

namespace {
bool is_capability_requested(const std::string& name, pn_data_t* capabilities)
{
    while (pn_data_next(capabilities)) {
        pn_bytes_t c = pn_data_get_symbol(capabilities);
        std::string s(c.start, c.size);
        if (s == name) return true;
    }
    return false;
}

const std::string CREATE_ON_DEMAND("create-on-demand");
}

class IncomingToQueue : public DecodingIncoming
{
  public:
    IncomingToQueue(Broker& b, Session& p, boost::shared_ptr<qpid::broker::Queue> q, pn_link_t* l, const std::string& source) : DecodingIncoming(l, b, p, source, q->getName(), pn_link_name(l)), queue(q) {}
    void handle(qpid::broker::Message& m);
  private:
    boost::shared_ptr<qpid::broker::Queue> queue;
};

class IncomingToExchange : public DecodingIncoming
{
  public:
    IncomingToExchange(Broker& b, Session& p, boost::shared_ptr<qpid::broker::Exchange> e, pn_link_t* l, const std::string& source) : DecodingIncoming(l, b, p, source, e->getName(), pn_link_name(l)), exchange(e) {}
    void handle(qpid::broker::Message& m);
  private:
    boost::shared_ptr<qpid::broker::Exchange> exchange;
};

Session::Session(pn_session_t* s, qpid::broker::Broker& b, Connection& c, qpid::sys::OutputControl& o)
    : ManagedSession(b, c, (boost::format("%1%") % s).str()), session(s), broker(b), connection(c), out(o), deleted(false) {}


Session::ResolvedNode Session::resolve(const std::string name, pn_terminus_t* terminus, bool incoming)
{
    ResolvedNode node;
    node.exchange = broker.getExchanges().find(name);
    node.queue = broker.getQueues().find(name);
    if (!node.queue && !node.exchange) {
        if (pn_terminus_is_dynamic(terminus)  || is_capability_requested(CREATE_ON_DEMAND, pn_terminus_capabilities(terminus))) {
            //is it a queue or an exchange?
            NodeProperties properties;
            properties.read(pn_terminus_properties(terminus));
            if (properties.isQueue()) {
                node.queue = broker.createQueue(name, properties.getQueueSettings(), this, properties.getAlternateExchange(), connection.getUserid(), connection.getId()).first;
            } else {
                qpid::framing::FieldTable args;
                node.exchange = broker.createExchange(name, properties.getExchangeType(), properties.isDurable(), properties.getAlternateExchange(),
                                                      args, connection.getUserid(), connection.getId()).first;
            }
        } else {
            size_t i = name.find('@');
            if (i != std::string::npos && (i+1) < name.length()) {
                std::string domain = name.substr(i+1);
                std::string local = name.substr(0, i);
                std::string id = (boost::format("%1%-%2%") % name % qpid::types::Uuid(true).str()).str();
                //does this domain exist?
                boost::shared_ptr<Domain> d = connection.getInterconnects().findDomain(domain);
                if (d) {
                    node.relay = boost::shared_ptr<Relay>(new Relay(1000));
                    if (incoming) {
                        d->connect(false, id, name, local, connection.getInterconnects(), node.relay);
                    } else {
                        d->connect(true, id, local, name, connection.getInterconnects(), node.relay);
                    }
                }
            }
        }
    } else if (node.queue && node.exchange) {
        QPID_LOG_CAT(warning, protocol, "Ambiguous node name; " << name << " could be queue or exchange, assuming queue");
        node.exchange.reset();
    }
    return node;
}

std::string Session::generateName(pn_link_t* link)
{
    std::stringstream s;
    s << qpid::types::Uuid(true) << "::" << pn_link_name(link);
    if (!connection.getDomain().empty()) {
        s << "@" << connection.getDomain();
    }
    return s.str();
}

void Session::attach(pn_link_t* link)
{
    if (pn_link_is_sender(link)) {
        pn_terminus_t* source = pn_link_remote_source(link);
        //i.e a subscription
        std::string name;
        if (pn_terminus_get_type(source) == PN_UNSPECIFIED) {
            throw qpid::Exception("No source specified!");/*invalid-field?*/
        } else if (pn_terminus_is_dynamic(source)) {
            name = generateName(link);
        } else {
            name = pn_terminus_get_address(source);
        }
        QPID_LOG(debug, "Received attach request for outgoing link from " << name);
        pn_terminus_set_address(pn_link_source(link), name.c_str());

        setupOutgoing(link, source, name);
    } else {
        pn_terminus_t* target = pn_link_remote_target(link);
        std::string name;
        if (pn_terminus_get_type(target) == PN_UNSPECIFIED) {
            throw qpid::Exception("No target specified!");/*invalid field?*/
        } else if (pn_terminus_is_dynamic(target)) {
            name = generateName(link);
        } else {
            name  = pn_terminus_get_address(target);
        }
        QPID_LOG(debug, "Received attach request for incoming link to " << name);
        pn_terminus_set_address(pn_link_target(link), name.c_str());

        setupIncoming(link, target, name);
    }
}

void Session::setupIncoming(pn_link_t* link, pn_terminus_t* target, const std::string& name)
{
    ResolvedNode node = resolve(name, target, true);
    const char* sourceAddress = pn_terminus_get_address(pn_link_remote_source(link));
    if (!sourceAddress) {
        sourceAddress = pn_terminus_get_address(pn_link_source(link));
    }
    std::string source;
    if (sourceAddress) {
        source = sourceAddress;
    }
    if (node.queue) {
        boost::shared_ptr<Incoming> q(new IncomingToQueue(broker, *this, node.queue, link, source));
        incoming[link] = q;
    } else if (node.exchange) {
        boost::shared_ptr<Incoming> e(new IncomingToExchange(broker, *this, node.exchange, link, source));
        incoming[link] = e;
    } else if (node.relay) {
        boost::shared_ptr<Incoming> in(new IncomingToRelay(link, broker, *this, source, name, pn_link_name(link), node.relay));
        incoming[link] = in;
    } else {
        pn_terminus_set_type(pn_link_target(link), PN_UNSPECIFIED);
        throw qpid::Exception("Node not found: " + name);/*not-found*/
    }
    QPID_LOG(debug, "Incoming link attached");
}

void Session::setupOutgoing(pn_link_t* link, pn_terminus_t* source, const std::string& name)
{
    ResolvedNode node = resolve(name, source, false);
    Filter filter;
    filter.read(pn_terminus_filter(source));
    const char* targetAddress = pn_terminus_get_address(pn_link_remote_target(link));
    if (!targetAddress) {
        targetAddress = pn_terminus_get_address(pn_link_target(link));
    }
    std::string target;
    if (targetAddress) {
        target = targetAddress;
    }


    if (node.queue) {
        boost::shared_ptr<Outgoing> q(new OutgoingFromQueue(broker, name, target, node.queue, link, *this, out, false));
        q->init();
        if (filter.hasSubjectFilter()) {
            q->setSubjectFilter(filter.getSubjectFilter());
        }
        if (filter.hasSelectorFilter()) {
            q->setSelectorFilter(filter.getSelectorFilter());
        }
        outgoing[link] = q;
    } else if (node.exchange) {
        QueueSettings settings(false, true);
        //TODO: populate settings from source details when available from engine
        boost::shared_ptr<qpid::broker::Queue> queue
            = broker.createQueue(name + qpid::types::Uuid(true).str(), settings, this, "", connection.getUserid(), connection.getId()).first;
        if (filter.hasSubjectFilter()) {
            filter.bind(node.exchange, queue);
            filter.write(pn_terminus_filter(pn_link_source(link)));
        } else if (node.exchange->getType() == TopicExchange::typeName) {
            node.exchange->bind(queue, "#", 0);
        } else {
            node.exchange->bind(queue, std::string(), 0);
        }
        boost::shared_ptr<Outgoing> q(new OutgoingFromQueue(broker, name, target, queue, link, *this, out, true));
        outgoing[link] = q;
        q->init();
    } else if (node.relay) {
        boost::shared_ptr<Outgoing> out(new OutgoingFromRelay(link, broker, *this, name, target, pn_link_name(link), node.relay));
        outgoing[link] = out;
        out->init();
    } else {
        pn_terminus_set_type(pn_link_source(link), PN_UNSPECIFIED);
        throw qpid::Exception("Node not found: " + name);/*not-found*/
    }
    QPID_LOG(debug, "Outgoing link attached");
}

/**
 * Called for links initiated by the broker
 */
void Session::attach(pn_link_t* link, const std::string& src, const std::string& tgt, boost::shared_ptr<Relay> relay)
{
    pn_terminus_t* source = pn_link_source(link);
    pn_terminus_t* target = pn_link_target(link);
    pn_terminus_set_address(source, src.c_str());
    pn_terminus_set_address(target, tgt.c_str());

    if (relay) {
        if (pn_link_is_sender(link)) {
            boost::shared_ptr<Outgoing> out(new OutgoingFromRelay(link, broker, *this, src, tgt, pn_link_name(link), relay));
            outgoing[link] = out;
            out->init();
        } else {
            boost::shared_ptr<Incoming> in(new IncomingToRelay(link, broker, *this, src, tgt, pn_link_name(link), relay));
            incoming[link] = in;
        }
    } else {
        if (pn_link_is_sender(link)) {
            setupOutgoing(link, source, src);
        } else {
            setupIncoming(link, target, tgt);
        }
    }
}

void Session::detach(pn_link_t* link)
{
    if (pn_link_is_sender(link)) {
        OutgoingLinks::iterator i = outgoing.find(link);
        if (i != outgoing.end()) {
            i->second->detached();
            outgoing.erase(i);
            QPID_LOG(debug, "Outgoing link detached");
        }
    } else {
        IncomingLinks::iterator i = incoming.find(link);
        if (i != incoming.end()) {
            i->second->detached();
            incoming.erase(i);
            QPID_LOG(debug, "Incoming link detached");
        }
    }
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

void Session::readable(pn_link_t* link, pn_delivery_t* delivery)
{
    pn_delivery_tag_t tag = pn_delivery_tag(delivery);
    QPID_LOG(debug, "received delivery: " << std::string(tag.bytes, tag.size));
    incomingMessageReceived();
    IncomingLinks::iterator target = incoming.find(link);
    if (target == incoming.end()) {
        QPID_LOG(error, "Received message on unknown link");
        pn_delivery_update(delivery, PN_REJECTED);
        pn_delivery_settle(delivery);//do we need to check settlement modes/orders?
        incomingMessageRejected();
    } else {
        target->second->readable(delivery);
        if (target->second->haveWork()) out.activateOutput();
    }
}
void Session::writable(pn_link_t* link, pn_delivery_t* delivery)
{
    OutgoingLinks::iterator sender = outgoing.find(link);
    if (sender == outgoing.end()) {
        QPID_LOG(error, "Delivery returned for unknown link");
    } else {
        sender->second->handle(delivery);
    }
}

bool Session::dispatch()
{
    bool output(false);
    for (OutgoingLinks::iterator s = outgoing.begin(); s != outgoing.end(); ++s) {
        if (s->second->doWork()) output = true;
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
    for (IncomingLinks::iterator i = incoming.begin(); i != incoming.end(); ++i) {
        if (i->second->doWork()) output = true;
    }

    return output;
}

void Session::close()
{
    for (OutgoingLinks::iterator i = outgoing.begin(); i != outgoing.end(); ++i) {
        i->second->detached();
    }
    for (IncomingLinks::iterator i = incoming.begin(); i != incoming.end(); ++i) {
        i->second->detached();
    }
    outgoing.clear();
    incoming.clear();
    QPID_LOG(debug, "Session closed, all links detached.");
    qpid::sys::Mutex::ScopedLock l(lock);
    deleted = true;
}

void Session::wakeup()
{
    out.activateOutput();
}

void IncomingToQueue::handle(qpid::broker::Message& message)
{
    queue->deliver(message);
}

void IncomingToExchange::handle(qpid::broker::Message& message)
{
    DeliverableMessage deliverable(message, 0);
    exchange->route(deliverable);
}

}}} // namespace qpid::broker::amqp
