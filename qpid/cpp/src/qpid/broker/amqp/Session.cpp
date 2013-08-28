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
#include "Exception.h"
#include "Interconnects.h"
#include "Relay.h"
#include "Topic.h"
#include "qpid/amqp/descriptors.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/DeliverableMessage.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/DirectExchange.h"
#include "qpid/broker/TopicExchange.h"
#include "qpid/broker/FanOutExchange.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueueCursor.h"
#include "qpid/broker/Selector.h"
#include "qpid/broker/TopicExchange.h"
#include "qpid/broker/amqp/Filter.h"
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
    pn_data_rewind(capabilities);
    while (pn_data_next(capabilities)) {
        pn_bytes_t c = pn_data_get_symbol(capabilities);
        std::string s(c.start, c.size);
        if (s == name) return true;
    }
    return false;
}
//capabilities
const std::string CREATE_ON_DEMAND("create-on-demand");
const std::string DURABLE("durable");
const std::string QUEUE("queue");
const std::string TOPIC("topic");
const std::string DIRECT_FILTER("legacy-amqp-direct-binding");
const std::string TOPIC_FILTER("legacy-amqp-topic-binding");
const std::string SHARED("shared");

void setCapabilities(pn_data_t* in, pn_data_t* out, boost::shared_ptr<Queue> node)
{
    pn_data_rewind(in);
    while (pn_data_next(in)) {
        pn_bytes_t c = pn_data_get_symbol(in);
        std::string s(c.start, c.size);
        if (s == DURABLE) {
            if (node->isDurable()) pn_data_put_symbol(out, c);
        } else if (s == CREATE_ON_DEMAND || s == QUEUE || s == DIRECT_FILTER || s == TOPIC_FILTER) {
            pn_data_put_symbol(out, c);
        }
    }
}

void setCapabilities(pn_data_t* in, pn_data_t* out, boost::shared_ptr<Exchange> node)
{
    pn_data_rewind(in);
    while (pn_data_next(in)) {
        pn_bytes_t c = pn_data_get_symbol(in);
        std::string s(c.start, c.size);
        if (s == DURABLE) {
            if (node->isDurable()) pn_data_put_symbol(out, c);
        } else if (s == SHARED) {
            pn_data_put_symbol(out, c);
        } else if (s == CREATE_ON_DEMAND || s == TOPIC) {
            pn_data_put_symbol(out, c);
        } else if (s == DIRECT_FILTER) {
            if (node->getType() == DirectExchange::typeName) pn_data_put_symbol(out, c);
        } else if (s == TOPIC_FILTER) {
            if (node->getType() == TopicExchange::typeName) pn_data_put_symbol(out, c);
        }
    }
}

}

class IncomingToQueue : public DecodingIncoming
{
  public:
    IncomingToQueue(Broker& b, Session& p, boost::shared_ptr<qpid::broker::Queue> q, pn_link_t* l, const std::string& source, bool icl)
        : DecodingIncoming(l, b, p, source, q->getName(), pn_link_name(l)), queue(q), isControllingLink(icl)
    {
        queue->markInUse(isControllingLink);
    }
    ~IncomingToQueue() { queue->releaseFromUse(isControllingLink); }
    void handle(qpid::broker::Message& m);
  private:
    boost::shared_ptr<qpid::broker::Queue> queue;
    bool isControllingLink;
};

class IncomingToExchange : public DecodingIncoming
{
  public:
    IncomingToExchange(Broker& b, Session& p, boost::shared_ptr<qpid::broker::Exchange> e, pn_link_t* l, const std::string& source)
        : DecodingIncoming(l, b, p, source, e->getName(), pn_link_name(l)), exchange(e), authorise(p.getAuthorise()) {}
    void handle(qpid::broker::Message& m);
  private:
    boost::shared_ptr<qpid::broker::Exchange> exchange;
    Authorise& authorise;
};

Session::Session(pn_session_t* s, Connection& c, qpid::sys::OutputControl& o)
    : ManagedSession(c.getBroker(), c, (boost::format("%1%") % s).str()), session(s), connection(c), out(o), deleted(false),
      authorise(connection.getUserId(), connection.getBroker().getAcl()) {}


Session::ResolvedNode Session::resolve(const std::string name, pn_terminus_t* terminus, bool incoming)
{
    ResolvedNode node;
    node.exchange = connection.getBroker().getExchanges().find(name);
    node.queue = connection.getBroker().getQueues().find(name);
    node.topic = connection.getTopics().get(name);
    if (node.topic) node.exchange = node.topic->getExchange();
    if (!node.queue && !node.exchange) {
        if (pn_terminus_is_dynamic(terminus)  || is_capability_requested(CREATE_ON_DEMAND, pn_terminus_capabilities(terminus))) {
            //is it a queue or an exchange?
            node.properties.read(pn_terminus_properties(terminus));
            if (node.properties.isQueue()) {
                node.queue = connection.getBroker().createQueue(name, node.properties.getQueueSettings(), this, node.properties.getAlternateExchange(), connection.getUserId(), connection.getId()).first;
            } else {
                qpid::framing::FieldTable args;
                node.exchange = connection.getBroker().createExchange(name, node.properties.getExchangeType(), node.properties.isDurable(), node.properties.getAlternateExchange(),
                                                      args, connection.getUserId(), connection.getId()).first;
            }
            node.created = true;
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
                        d->connect(false, id, name, local, connection, node.relay);
                    } else {
                        d->connect(true, id, local, name, connection, node.relay);
                    }
                }
            }
        }
    } else if (node.queue && node.topic) {
        QPID_LOG_CAT(warning, protocol, "Ambiguous node name; " << name << " could be queue or topic, assuming topic");
        node.queue.reset();
    } else if (node.queue && node.exchange) {
        QPID_LOG_CAT(warning, protocol, "Ambiguous node name; " << name << " could be queue or exchange, assuming queue");
        node.exchange.reset();
    }

    if (node.properties.isExclusive() && node.queue && node.queue->setExclusiveOwner(this)) {
        exclusiveQueues.insert(node.queue);
    }

    return node;
}

std::string Session::generateName(pn_link_t* link)
{
    std::stringstream s;
    if (connection.getContainerId().empty()) {
        s << qpid::types::Uuid(true);
    } else {
        s << connection.getContainerId();
    }
    s << "_" << pn_link_name(link);
    return s.str();
}

std::string Session::qualifyName(const std::string& name)
{
    if (connection.getDomain().empty()) {
        return name;
    } else {
        std::stringstream s;
        s << name << "@" << connection.getDomain();
        return s.str();
    }
}

void Session::attach(pn_link_t* link)
{
    if (pn_link_is_sender(link)) {
        pn_terminus_t* source = pn_link_remote_source(link);
        //i.e a subscription
        std::string name;
        if (pn_terminus_get_type(source) == PN_UNSPECIFIED) {
            throw Exception(qpid::amqp::error_conditions::PRECONDITION_FAILED, "No source specified!");
        } else if (pn_terminus_is_dynamic(source)) {
            name = generateName(link);
            QPID_LOG(debug, "Received attach request for outgoing link from " << name);
            pn_terminus_set_address(pn_link_source(link), qualifyName(name).c_str());
        } else {
            name = pn_terminus_get_address(source);
            QPID_LOG(debug, "Received attach request for outgoing link from " << name);
            pn_terminus_set_address(pn_link_source(link), name.c_str());
        }

        setupOutgoing(link, source, name);
    } else {
        pn_terminus_t* target = pn_link_remote_target(link);
        std::string name;
        if (pn_terminus_get_type(target) == PN_UNSPECIFIED) {
            throw Exception(qpid::amqp::error_conditions::PRECONDITION_FAILED, "No target specified!");
        } else if (pn_terminus_is_dynamic(target)) {
            name = generateName(link);
            QPID_LOG(debug, "Received attach request for incoming link to " << name);
            pn_terminus_set_address(pn_link_target(link), qualifyName(name).c_str());
        } else {
            name  = pn_terminus_get_address(target);
            QPID_LOG(debug, "Received attach request for incoming link to " << name);
            pn_terminus_set_address(pn_link_target(link), name.c_str());
        }

        setupIncoming(link, target, name);
    }
}

void Session::setupIncoming(pn_link_t* link, pn_terminus_t* target, const std::string& name)
{
    ResolvedNode node = resolve(name, target, true);
    //set capabilities
    if (node.queue) {
        setCapabilities(pn_terminus_capabilities(target), pn_terminus_capabilities(pn_link_target(link)), node.queue);
        authorise.incoming(node.queue);
    } else if (node.exchange) {
        setCapabilities(pn_terminus_capabilities(target), pn_terminus_capabilities(pn_link_target(link)), node.exchange);
        authorise.incoming(node.exchange);
    }
    if (node.created) {
        node.properties.write(pn_terminus_properties(pn_link_target(link)));
    }

    const char* sourceAddress = pn_terminus_get_address(pn_link_remote_source(link));
    if (!sourceAddress) {
        sourceAddress = pn_terminus_get_address(pn_link_source(link));
    }
    std::string source;
    if (sourceAddress) {
        source = sourceAddress;
    }
    if (node.queue) {
        boost::shared_ptr<Incoming> q(new IncomingToQueue(connection.getBroker(), *this, node.queue, link, source, node.properties.trackControllingLink()));
        incoming[link] = q;
    } else if (node.exchange) {
        boost::shared_ptr<Incoming> e(new IncomingToExchange(connection.getBroker(), *this, node.exchange, link, source));
        incoming[link] = e;
    } else if (node.relay) {
        boost::shared_ptr<Incoming> in(new IncomingToRelay(link, connection.getBroker(), *this, source, name, pn_link_name(link), node.relay));
        incoming[link] = in;
    } else {
        pn_terminus_set_type(pn_link_target(link), PN_UNSPECIFIED);
        throw Exception(qpid::amqp::error_conditions::NOT_FOUND, std::string("Node not found: ") + name);
    }
    if (connection.getBroker().getOptions().auth && !connection.isLink())
        incoming[link]->verify(connection.getUserId(), connection.getBroker().getOptions().realm);
    QPID_LOG(debug, "Incoming link attached");
}

void Session::setupOutgoing(pn_link_t* link, pn_terminus_t* source, const std::string& name)
{
    ResolvedNode node = resolve(name, source, false);
    if (node.queue) setCapabilities(pn_terminus_capabilities(source), pn_terminus_capabilities(pn_link_source(link)), node.queue);
    else if (node.exchange) setCapabilities(pn_terminus_capabilities(source), pn_terminus_capabilities(pn_link_source(link)), node.exchange);
    if (node.created) {
        node.properties.write(pn_terminus_properties(pn_link_source(link)));
    }

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
        authorise.outgoing(node.queue);
        SubscriptionType type = pn_terminus_get_distribution_mode(source) == PN_DIST_MODE_COPY ? BROWSER : CONSUMER;
        boost::shared_ptr<Outgoing> q(new OutgoingFromQueue(connection.getBroker(), name, target, node.queue, link, *this, out, type, false, node.properties.trackControllingLink()));
        q->init();
        filter.apply(q);
        outgoing[link] = q;
    } else if (node.exchange) {
        authorise.access(node.exchange);//do separate access check before trying to create the queue
        bool shared = is_capability_requested(SHARED, pn_terminus_capabilities(source));
        bool durable = pn_terminus_get_durability(source);
        QueueSettings settings(durable, !durable);
        if (node.topic) {
            settings = node.topic->getPolicy();
            settings.durable = durable;
            settings.autodelete = !durable;
        }
        settings.autoDeleteDelay = pn_terminus_get_timeout(source);
        if (settings.autoDeleteDelay) {
            settings.autodelete = true;
            settings.original["qpid.auto_delete_timeout"] = settings.autoDeleteDelay;
        }
        filter.configure(settings);
        std::stringstream queueName;
        if (shared) {
            //just use link name (TODO: could allow this to be
            //overridden when access to link properties is provided
            //(PROTON-335))
            queueName << pn_link_name(link);
        } else {
            //combination of container id and link name is unique
            queueName << connection.getContainerId() << "_" << pn_link_name(link);
        }
        boost::shared_ptr<qpid::broker::Queue> queue
            = connection.getBroker().createQueue(queueName.str(), settings, this, "", connection.getUserId(), connection.getId()).first;
        if (!shared) queue->setExclusiveOwner(this);
        authorise.outgoing(node.exchange, queue, filter);
        filter.bind(node.exchange, queue);
        boost::shared_ptr<Outgoing> q(new OutgoingFromQueue(connection.getBroker(), name, target, queue, link, *this, out, CONSUMER, !shared, false));
        q->init();
        outgoing[link] = q;
    } else if (node.relay) {
        boost::shared_ptr<Outgoing> out(new OutgoingFromRelay(link, connection.getBroker(), *this, name, target, pn_link_name(link), node.relay));
        outgoing[link] = out;
        out->init();
    } else {
        pn_terminus_set_type(pn_link_source(link), PN_UNSPECIFIED);
        throw Exception(qpid::amqp::error_conditions::NOT_FOUND, std::string("Node not found: ") + name);/*not-found*/
    }
    filter.write(pn_terminus_filter(pn_link_source(link)));
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
            boost::shared_ptr<Outgoing> out(new OutgoingFromRelay(link, connection.getBroker(), *this, src, tgt, pn_link_name(link), relay));
            outgoing[link] = out;
            out->init();
        } else {
            boost::shared_ptr<Incoming> in(new IncomingToRelay(link, connection.getBroker(), *this, src, tgt, pn_link_name(link), relay));
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
    for (OutgoingLinks::iterator s = outgoing.begin(); s != outgoing.end();) {
        try {
            if (s->second->doWork()) output = true;
            ++s;
        } catch (const Exception& e) {
            pn_condition_t* error = pn_link_condition(s->first);
            pn_condition_set_name(error, e.symbol());
            pn_condition_set_description(error, e.what());
            pn_link_close(s->first);
            s->second->detached();
            outgoing.erase(s++);
            output = true;
        }
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
    for (IncomingLinks::iterator i = incoming.begin(); i != incoming.end();) {
        try {
            if (i->second->doWork()) output = true;
            ++i;
        } catch (const Exception& e) {
            pn_condition_t* error = pn_link_condition(i->first);
            pn_condition_set_name(error, e.symbol());
            pn_condition_set_description(error, e.what());
            pn_link_close(i->first);
            i->second->detached();
            incoming.erase(i++);
            output = true;
        }
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
    QPID_LOG(debug, "Session " << session << " closed, all links detached.");
    for (std::set< boost::shared_ptr<Queue> >::const_iterator i = exclusiveQueues.begin(); i != exclusiveQueues.end(); ++i) {
        (*i)->releaseExclusiveOwnership();
    }
    exclusiveQueues.clear();
    qpid::sys::Mutex::ScopedLock l(lock);
    deleted = true;
}

void Session::wakeup()
{
    out.activateOutput();
}

Authorise& Session::getAuthorise()
{
    return authorise;
}

void IncomingToQueue::handle(qpid::broker::Message& message)
{
    if (queue->isDeleted()) {
        std::stringstream msg;
        msg << " Queue " << queue->getName() << " has been deleted";
        throw Exception(qpid::amqp::error_conditions::RESOURCE_DELETED, msg.str());
    }
    queue->deliver(message);
}

void IncomingToExchange::handle(qpid::broker::Message& message)
{
    authorise.route(exchange, message);
    DeliverableMessage deliverable(message, 0);
    exchange->route(deliverable);
}

}}} // namespace qpid::broker::amqp
