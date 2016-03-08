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
#include "DataReader.h"
#include "Domain.h"
#include "Exception.h"
#include "Interconnects.h"
#include "NodePolicy.h"
#include "Relay.h"
#include "Topic.h"
#include "qpid/amqp/Descriptor.h"
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
#include "qpid/amqp_0_10/Codecs.h"
#include "config.h"
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

using namespace qpid::amqp::transaction;

namespace {
pn_bytes_t convert(const std::string& s)
{
    pn_bytes_t result;
    result.start = const_cast<char*>(s.data());
    result.size = s.size();
    return result;
}
std::string convert(pn_bytes_t in)
{
    return std::string(in.start, in.size);
}
//capabilities
const std::string CREATE_ON_DEMAND("create-on-demand");
const std::string DURABLE("durable");
const std::string QUEUE("queue");
const std::string TOPIC("topic");
const std::string DIRECT_FILTER("legacy-amqp-direct-binding");
const std::string TOPIC_FILTER("legacy-amqp-topic-binding");
const std::string SHARED("shared");

void writeCapabilities(pn_data_t* out, const std::vector<std::string>& supported)
{
    if (supported.size() == 1) {
        pn_data_put_symbol(out, convert(supported.front()));
    } else if (supported.size() > 1) {
        pn_data_put_array(out, false, PN_SYMBOL);
        pn_data_enter(out);
        for (std::vector<std::string>::const_iterator i = supported.begin(); i != supported.end(); ++i) {
            pn_data_put_symbol(out, convert(*i));
        }
        pn_data_exit(out);
    }
}

template <class F>
void readCapabilities(pn_data_t* data, F f)
{
    pn_data_rewind(data);
    if (pn_data_next(data)) {
        pn_type_t type = pn_data_type(data);
        if (type == PN_ARRAY) {
            pn_data_enter(data);
            while (pn_data_next(data)) {
	      std::string s = convert(pn_data_get_symbol(data));
	      f(s);
            }
            pn_data_exit(data);
        } else if (type == PN_SYMBOL) {
	  std::string s = convert(pn_data_get_symbol(data));
	  f(s);
        } else {
            QPID_LOG(error, "Skipping capabilities field of type " << pn_type_name(type));
        }
    }
}

void matchCapability(const std::string& name, bool* result, const std::string& s)
{
    if (s == name) *result = true;
}

bool is_capability_requested(const std::string& name, pn_data_t* capabilities)
{
    bool result(false);
    readCapabilities(capabilities, boost::bind(&matchCapability, name, &result, _1));
    return result;
}

void collectQueueCapabilities(boost::shared_ptr<Queue> node, std::vector<std::string>* supported, const std::string& s)
{
    if (s == DURABLE) {
        if (node->isDurable()) supported->push_back(s);
    } else if (s == CREATE_ON_DEMAND || s == QUEUE || s == DIRECT_FILTER || s == TOPIC_FILTER) {
        supported->push_back(s);
    }
}

void collectExchangeCapabilities(boost::shared_ptr<Exchange> node, std::vector<std::string>* supported, const std::string& s)
{
    if (s == DURABLE) {
        if (node->isDurable()) supported->push_back(s);
    } else if (s == SHARED) {
        supported->push_back(s);
    } else if (s == CREATE_ON_DEMAND || s == TOPIC) {
        supported->push_back(s);
    } else if (s == DIRECT_FILTER) {
        if (node->getType() == DirectExchange::typeName) supported->push_back(s);
    } else if (s == TOPIC_FILTER) {
        if (node->getType() == TopicExchange::typeName) supported->push_back(s);
    }
}

void setCapabilities(pn_data_t* in, pn_data_t* out, boost::shared_ptr<Queue> node)
{
    std::vector<std::string> supported;
    readCapabilities(in, boost::bind(&collectQueueCapabilities, node, &supported, _1));
    writeCapabilities(out, supported);
}

void setCapabilities(pn_data_t* in, pn_data_t* out, boost::shared_ptr<Exchange> node)
{
    std::vector<std::string> supported;
    readCapabilities(in, boost::bind(&collectExchangeCapabilities, node, &supported, _1));
    writeCapabilities(out, supported);
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
    void handle(qpid::broker::Message& m, qpid::broker::TxBuffer*);
  private:
    boost::shared_ptr<qpid::broker::Queue> queue;
    bool isControllingLink;
};

class IncomingToExchange : public DecodingIncoming
{
  public:
    IncomingToExchange(Broker& b, Session& p, boost::shared_ptr<qpid::broker::Exchange> e, pn_link_t* l, const std::string& source, bool icl)
        : DecodingIncoming(l, b, p, source, e->getName(), pn_link_name(l)), exchange(e), authorise(p.getAuthorise()), isControllingLink(icl)
    {
        exchange->incOtherUsers();
    }
    ~IncomingToExchange()
    {
        exchange->decOtherUsers(isControllingLink);
    }
    void handle(qpid::broker::Message& m, qpid::broker::TxBuffer*);
  private:
    boost::shared_ptr<qpid::broker::Exchange> exchange;
    Authorise& authorise;
    bool isControllingLink;
};

class AnonymousRelay : public DecodingIncoming
{
  public:
    AnonymousRelay(Broker& b, Connection& c, Session& p, pn_link_t* l)
        : DecodingIncoming(l, b, p, std::string(), "ANONYMOUS-RELAY", pn_link_name(l)), authorise(p.getAuthorise()), context(c)
    {}
    void handle(qpid::broker::Message& m, qpid::broker::TxBuffer*);
  private:
    boost::shared_ptr<qpid::broker::Exchange> exchange;
    Authorise& authorise;
    BrokerContext& context;
};

class IncomingToCoordinator : public DecodingIncoming
{
  public:
    IncomingToCoordinator(pn_link_t* link, Broker& broker, Session& parent)
        : DecodingIncoming(link, broker, parent, std::string(), "txn-ctrl", pn_link_name(link)) {}

    ~IncomingToCoordinator() { session.abort(); }
    void deliver(boost::intrusive_ptr<qpid::broker::amqp::Message>, pn_delivery_t*);
    void handle(qpid::broker::Message&, qpid::broker::TxBuffer*) {}
  private:
};

bool Session::ResolvedNode::trackControllingLink() const
{
    return created && (properties.trackControllingLink() || (queue && queue->getSettings().lifetime == QueueSettings::DELETE_ON_CLOSE));
}

Session::Session(pn_session_t* s, Connection& c, qpid::sys::OutputControl& o)
    : ManagedSession(c.getBroker(), c, (boost::format("%1%") % s).str()), session(s), connection(c), out(o), deleted(false),
      authorise(connection.getUserId(), connection.getBroker().getAcl()),
      detachRequested(),
      tx(*this)
{}


Session::ResolvedNode Session::resolve(const std::string name, pn_terminus_t* terminus, bool incoming)
{
    bool isQueueRequested = is_capability_requested(QUEUE, pn_terminus_capabilities(terminus));
    bool isTopicRequested = is_capability_requested(TOPIC, pn_terminus_capabilities(terminus));
    if (isTopicRequested && isQueueRequested) {
        //requesting both renders each request meaningless
        isQueueRequested = false;
        isTopicRequested = false;
    }
    //check whether user is even allowed access to queues/topics before resolving
    authorise.access(name, isQueueRequested, isTopicRequested);
    ResolvedNode node(pn_terminus_is_dynamic(terminus));
    if (isTopicRequested || !isQueueRequested) {
        node.topic = connection.getTopics().get(name);
        if (node.topic) node.exchange = node.topic->getExchange();
        else node.exchange = connection.getBroker().getExchanges().find(name);
    }
    if (isQueueRequested || !isTopicRequested) {
        node.queue = connection.getBroker().getQueues().find(name);
    }
    bool createOnDemand = is_capability_requested(CREATE_ON_DEMAND, pn_terminus_capabilities(terminus));
    //Strictly speaking, properties should only be specified when the
    //terminus is dynamic. However we will not enforce that here. If
    //properties are set on the attach request, we will set them on
    //our reply. This allows the 'create' and 'assert' options in the
    //qpid messaging API to be implemented over 1.0.
    node.properties.read(pn_terminus_properties(terminus));

    if (node.exchange && createOnDemand && isTopicRequested) {
        if (!node.properties.getSpecifiedExchangeType().empty() && node.properties.getExchangeType() != node.exchange->getType()) {
            //emulate 0-10 exchange-declare behaviour
            throw Exception(qpid::amqp::error_conditions::PRECONDITION_FAILED, "Exchange of different type already exists");
        }
    }
    bool isCreateRequested = pn_terminus_is_dynamic(terminus)  || createOnDemand;
    bool isCreateQueueRequested = isCreateRequested && isQueueRequested;
    bool isCreateTopicRequested = isCreateRequested && isTopicRequested;
    if ((!node.queue && !node.exchange) || (!node.queue && isCreateQueueRequested) || (!node.exchange && isCreateTopicRequested)) {
        if (isCreateRequested) {
            //is it a queue or an exchange?
            if (isTopicRequested) {
                if (node.queue) {
                    QPID_LOG_CAT(warning, model, "Node name will be ambiguous, creation of exchange named " << name << " requested when queue of the same name already exists");
                }
                qpid::framing::FieldTable args;
                qpid::amqp_0_10::translate(node.properties.getProperties(), args);
                std::pair<boost::shared_ptr<Exchange>, bool> result
                    = connection.getBroker().createExchange(name, node.properties.getExchangeType(), node.properties.isDurable(), node.properties.isAutodelete(),
                                                            node.properties.getAlternateExchange(),
                                                            args, connection.getUserId(), connection.getId());
                node.exchange = result.first;
                node.created = result.second;
            } else {
                if (node.exchange) {
                    QPID_LOG_CAT(warning, model, "Node name will be ambiguous, creation of queue named " << name << " requested when exchange of the same name already exists");
                }
                std::pair<boost::shared_ptr<Queue>, bool> result
                    = connection.getBroker().createQueue(name, node.properties.getQueueSettings(), node.properties.isExclusive() ? this:0, node.properties.getAlternateExchange(), connection.getUserId(), connection.getId());
                node.queue = result.first;
                node.created = result.second;
            }
        } else {
            boost::shared_ptr<NodePolicy> nodePolicy = connection.getNodePolicies().match(name);
            if (nodePolicy) {
                std::pair<boost::shared_ptr<Queue>, boost::shared_ptr<Topic> > result = nodePolicy->create(name, connection);
                node.queue = result.first;
                node.topic = result.second;
                node.created = node.queue || node.topic;
                if (node.topic) node.exchange = node.topic->getExchange();

                if (node.queue) {
                    QPID_LOG(info, "Created queue " << name << " from policy with pattern " << nodePolicy->getPattern());
                } else if (node.topic) {
                    QPID_LOG(info, "Created topic " << name << " from policy with pattern " << nodePolicy->getPattern());
                } else {
                    QPID_LOG(debug, "Created neither a topic nor a queue for " << name << " from policy with pattern " << nodePolicy->getPattern());
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
                            d->connect(false, id, name, local, connection, node.relay);
                        } else {
                            d->connect(true, id, local, name, connection, node.relay);
                        }
                    }
                }
            }
        }
    } else if (node.queue && node.topic) {
        if (isTopicRequested) {
            QPID_LOG_CAT(info, protocol, "Ambiguous node name; " << name << " could be queue or topic, topic requested");
            node.queue.reset();
        } else if (isQueueRequested) {
            QPID_LOG_CAT(info, protocol, "Ambiguous node name; " << name << " could be queue or topic, queue requested");
            node.exchange.reset();
            node.topic.reset();
        } else {
            QPID_LOG_CAT(warning, protocol, "Ambiguous node name; " << name << " could be queue or topic, assuming topic");
            node.queue.reset();
        }
    } else if (node.queue && node.exchange) {
        if (isTopicRequested) {
            QPID_LOG_CAT(info, protocol, "Ambiguous node name; " << name << " could be queue or topic, topic requested");
            node.queue.reset();
        } else if (isQueueRequested) {
            QPID_LOG_CAT(info, protocol, "Ambiguous node name; " << name << " could be queue or topic, queue requested");
            node.exchange.reset();
            node.topic.reset();
        } else {
            QPID_LOG_CAT(warning, protocol, "Ambiguous node name; " << name << " could be queue or exchange, assuming queue");
            node.exchange.reset();
        }
    }

    if (node.properties.isExclusive() && node.queue) {
        if (node.queue->setExclusiveOwner(this)) {
            exclusiveQueues.insert(node.queue);
        } else {
            throw Exception(qpid::amqp::error_conditions::PRECONDITION_FAILED, std::string("Cannot grant exclusive access to ") + node.queue->getName());
        }
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
        pn_link_set_snd_settle_mode(link, pn_link_remote_snd_settle_mode(link));
        //i.e a subscription
        std::string name;
        if (pn_terminus_get_type(source) == PN_UNSPECIFIED) {
            pn_terminus_set_type(pn_link_source(link), PN_UNSPECIFIED);
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

        try {
            setupOutgoing(link, source, name);
        } catch (const std::exception&) {
            pn_terminus_set_type(pn_link_source(link), PN_UNSPECIFIED);
            throw;
        }
    } else {
        pn_terminus_t* target = pn_link_remote_target(link);
        std::string name;
        if (pn_terminus_get_type(target) == PN_UNSPECIFIED) {
            pn_terminus_set_type(pn_link_target(link), PN_UNSPECIFIED);
            throw Exception(qpid::amqp::error_conditions::PRECONDITION_FAILED, "No target specified!");
        } else if (pn_terminus_get_type(target) == PN_COORDINATOR) {
            QPID_LOG(debug, "Received attach request for incoming link to transaction coordinator on " << this);
            boost::shared_ptr<Incoming> i(new IncomingToCoordinator(link, connection.getBroker(), *this));
            incoming[link] = i;
            return;
        } else if (pn_terminus_is_dynamic(target)) {
            name = generateName(link);
            QPID_LOG(debug, "Received attach request for incoming link to " << name);
            pn_terminus_set_address(pn_link_target(link), qualifyName(name).c_str());
        } else if (pn_terminus_get_type(target) == PN_TARGET && !pn_terminus_get_address(target)) {
            authorise.access("ANONYMOUS-RELAY");
            boost::shared_ptr<Incoming> r(new AnonymousRelay(connection.getBroker(), connection, *this, link));
            incoming[link] = r;
            if (connection.getBroker().isAuthenticating() && !connection.isLink())
                r->verify(connection.getUserId(), connection.getBroker().getRealm());
            QPID_LOG(debug, "Incoming link attached for ANONYMOUS-RELAY");
            return;
        } else {
            name  = pn_terminus_get_address(target);
            QPID_LOG(debug, "Received attach request for incoming link to " << name);
            pn_terminus_set_address(pn_link_target(link), name.c_str());
        }

        try {
            setupIncoming(link, target, name);
        } catch (const std::exception&) {
            pn_terminus_set_type(pn_link_target(link), PN_UNSPECIFIED);
            throw;
        }
    }
}

void Session::setupIncoming(pn_link_t* link, pn_terminus_t* target, const std::string& name)
{
    ResolvedNode node = resolve(name, target, true);
    //set capabilities
    if (node.queue) {
        setCapabilities(pn_terminus_capabilities(target), pn_terminus_capabilities(pn_link_target(link)), node.queue);
        authorise.incoming(node.queue);
        node.properties.write(pn_terminus_properties(pn_link_target(link)), node.queue);
    } else if (node.exchange) {
        setCapabilities(pn_terminus_capabilities(target), pn_terminus_capabilities(pn_link_target(link)), node.exchange);
        authorise.incoming(node.exchange);
        node.properties.write(pn_terminus_properties(pn_link_target(link)), node.exchange);
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
        boost::shared_ptr<Incoming> q(new IncomingToQueue(connection.getBroker(), *this, node.queue, link, source, node.trackControllingLink()));
        incoming[link] = q;
    } else if (node.exchange) {
        boost::shared_ptr<Incoming> e(new IncomingToExchange(connection.getBroker(), *this, node.exchange, link, source, node.trackControllingLink()));
        incoming[link] = e;
    } else if (node.relay) {
        boost::shared_ptr<Incoming> in(new IncomingToRelay(link, connection.getBroker(), *this, source, name, pn_link_name(link), node.relay));
        incoming[link] = in;
    } else {
        pn_terminus_set_type(pn_link_target(link), PN_UNSPECIFIED);
        throw Exception(qpid::amqp::error_conditions::NOT_FOUND, std::string("Node not found: ") + name);
    }
    if (connection.getBroker().isAuthenticating() && !connection.isLink())
        incoming[link]->verify(connection.getUserId(), connection.getBroker().getRealm());
    QPID_LOG(debug, "Incoming link attached");
}

void Session::setupOutgoing(pn_link_t* link, pn_terminus_t* source, const std::string& name)
{
    ResolvedNode node = resolve(name, source, false);
    if (node.queue) {
        setCapabilities(pn_terminus_capabilities(source), pn_terminus_capabilities(pn_link_source(link)), node.queue);
        node.properties.write(pn_terminus_properties(pn_link_source(link)), node.queue);
    } else if (node.exchange) {
        setCapabilities(pn_terminus_capabilities(source), pn_terminus_capabilities(pn_link_source(link)), node.exchange);
        node.properties.write(pn_terminus_properties(pn_link_source(link)), node.exchange);
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
        SubscriptionType type = (pn_terminus_get_distribution_mode(source) == PN_DIST_MODE_COPY) || (node.queue->isBrowseOnly()) ? BROWSER : CONSUMER;
        if (type == CONSUMER && node.queue->hasExclusiveOwner() && !node.queue->isExclusiveOwner(this)) {
            throw Exception(qpid::amqp::error_conditions::PRECONDITION_FAILED, std::string("Cannot consume from exclusive queue ") + node.queue->getName());
        }
        boost::shared_ptr<Outgoing> q(new OutgoingFromQueue(connection.getBroker(), name, target, node.queue, link, *this, out, type, false, node.trackControllingLink()));
        q->init();
        filter.apply(q);
        outgoing[link] = q;
        pn_terminus_set_distribution_mode(pn_link_source(link), type == BROWSER ? PN_DIST_MODE_COPY : PN_DIST_MODE_MOVE);
    } else if (node.exchange) {
        authorise.access(node.exchange);//do separate access check before trying to create the queue
        bool shared = is_capability_requested(SHARED, pn_terminus_capabilities(source));
        bool durable = pn_terminus_get_durability(source);
        bool autodelete = !durable && pn_link_remote_snd_settle_mode(link) != PN_SND_UNSETTLED;
        QueueSettings settings(durable, autodelete);
        std::string altExchange;
        if (node.topic) {
            settings = node.topic->getPolicy();
            settings.durable = durable;
            //only determine autodelete from link details if the policy did not imply autodeletion
            if (!settings.autodelete) settings.autodelete = autodelete;
            altExchange = node.topic->getAlternateExchange();
        }
        if (settings.original.find("qpid.auto_delete_timeout") == settings.original.end()) {
            //only use delay from link if policy didn't specify one
            settings.autoDeleteDelay = pn_terminus_get_timeout(source);
            if (settings.autoDeleteDelay)
                settings.original["qpid.auto_delete_timeout"] = settings.autoDeleteDelay;
        }
        if (settings.autoDeleteDelay) {
            settings.autodelete = true;
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
            = connection.getBroker().createQueue(queueName.str(), settings, this, altExchange, connection.getUserId(), connection.getId()).first;
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

void Session::detach(pn_link_t* link, bool closed)
{
    if (pn_link_is_sender(link)) {
        OutgoingLinks::iterator i = outgoing.find(link);
        if (i != outgoing.end()) {
            i->second->detached(closed);
            boost::shared_ptr<Queue> q = OutgoingFromQueue::getExclusiveSubscriptionQueue(i->second.get());
            if (q && !q->isAutoDelete() && !q->isDeleted()) {
                connection.getBroker().deleteQueue(q->getName(), connection.getUserId(), connection.getMgmtId());
            }
            outgoing.erase(i);
            QPID_LOG(debug, "Outgoing link detached");
        }
    } else {
        IncomingLinks::iterator i = incoming.find(link);
        if (i != incoming.end()) {
            abort_pending(link);
            i->second->detached(closed);
            incoming.erase(i);
            QPID_LOG(debug, "Incoming link detached");
        }
    }
}

void Session::pending_accept(pn_delivery_t* delivery)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    pending.insert(delivery);
}

bool Session::clear_pending(pn_delivery_t* delivery)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    std::set<pn_delivery_t*>::iterator i = pending.find(delivery);
    if (i != pending.end()) {
        pending.erase(i);
        return true;
    } else {
        return false;
    }
}

void Session::abort_pending(pn_link_t* link)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    for (std::set<pn_delivery_t*>::iterator i = pending.begin(); i != pending.end();) {
        if (pn_delivery_link(*i) == link) {
            pn_delivery_settle(*i);
            pending.erase(i++);
        } else {
            ++i;
        }
    }
}

void Session::accepted(pn_delivery_t* delivery, bool sync)
{
    if (sync) {
        if (clear_pending(delivery))
        {
            //this is on IO thread
            pn_delivery_update(delivery, PN_ACCEPTED);
            pn_delivery_settle(delivery);//do we need to check settlement modes/orders?
            incomingMessageAccepted();
        }
    } else {
        //this is not on IO thread, need to delay processing until on IO thread
        qpid::sys::Mutex::ScopedLock l(lock);
        if (!deleted && pending.find(delivery) != pending.end()) {
            completed.push_back(delivery);
            out.activateOutput();
        }
    }
}

void Session::readable(pn_link_t* link, pn_delivery_t* delivery)
{
    pn_delivery_tag_t tag = pn_delivery_tag(delivery);
#ifdef NO_PROTON_DELIVERY_TAG_T
    QPID_LOG(debug, "received delivery: " << std::string(tag.start, tag.size));
#else
    QPID_LOG(debug, "received delivery: " << std::string(tag.bytes, tag.size));
#endif
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
        QPID_LOG(error, "Delivery returned for unknown link " << pn_link_name(link));
    } else {
        sender->second->handle(delivery);
    }
}

bool Session::dispatch()
{
    bool output(false);
    if (tx.commitPending.boolCompareAndSwap(true, false)) {
        committed(true);
    }
    for (OutgoingLinks::iterator s = outgoing.begin(); s != outgoing.end();) {
        try {
            if (s->second->doWork()) output = true;
            ++s;
        } catch (const Exception& e) {
            pn_condition_t* error = pn_link_condition(s->first);
            pn_condition_set_name(error, e.symbol());
            pn_condition_set_description(error, e.what());
            pn_link_close(s->first);
            s->second->detached(true);
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
            i->second->detached(true);
            incoming.erase(i++);
            output = true;
        }
    }

    return output;
}

void Session::close()
{
    for (OutgoingLinks::iterator i = outgoing.begin(); i != outgoing.end(); ++i) {
        i->second->detached(false);
    }
    for (IncomingLinks::iterator i = incoming.begin(); i != incoming.end(); ++i) {
        i->second->detached(false);
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

bool Session::endedByManagement() const
{
    return detachRequested;
}

void Session::detachedByManagement()
{
    detachRequested = true;
    wakeup();
}

TxBuffer* Session::getTransaction(const std::string& id)
{
    return  (tx.buffer.get() && id == tx.id) ? tx.buffer.get() : 0;
}

TxBuffer* Session::getTransaction(pn_delivery_t* delivery)
{
    return getTransactionalState(delivery).first;
}

std::pair<TxBuffer*,uint64_t> Session::getTransactionalState(pn_delivery_t* delivery)
{
    std::pair<TxBuffer*,uint64_t> result((TxBuffer*)0, 0);
    if (pn_delivery_remote_state(delivery) == TRANSACTIONAL_STATE_CODE) {
        pn_data_t* data = pn_disposition_data(pn_delivery_remote(delivery));
        pn_data_rewind(data);
        size_t count = 0;
        if (data && pn_data_next(data) && (count = pn_data_get_list(data)) > 0) {
            pn_data_enter(data);
            pn_data_next(data);
            std::string id = convert(pn_data_get_binary(data));
            result.first = getTransaction(id);
            if (!result.first) {
                QPID_LOG(error, "Transaction not found for id: " << id);
            }
            if (count > 1 && pn_data_next(data)) {
                pn_data_enter(data);
                pn_data_next(data);
                result.second = pn_data_get_ulong(data);
            }
        }
        else
            QPID_LOG(error, "Transactional delivery " << delivery << " appears to have no data");
    }
    return result;
}

std::string Session::declare()
{
    if (tx.buffer.get()) {
        //not sure what the error code should be; none in spec really fit well.
        throw Exception(qpid::amqp::error_conditions::transaction::ROLLBACK,
                        "Session only supports one transaction active at a time");
    }
    tx.buffer = boost::intrusive_ptr<TxBuffer>(new TxBuffer());
    connection.getBroker().getBrokerObservers().startTx(tx.buffer);
    txStarted();
    return tx.id;
}

namespace {
    class AsyncCommit : public qpid::broker::AsyncCompletion::Callback
    {
      public:
        AsyncCommit(boost::shared_ptr<Session> s) : session(s) {}
        void completed(bool sync) { session->committed(sync); }
        boost::intrusive_ptr<qpid::broker::AsyncCompletion::Callback> clone()
        {
            boost::intrusive_ptr<qpid::broker::AsyncCompletion::Callback> copy(new AsyncCommit(session));
            return copy;
        }

      private:
        boost::shared_ptr<Session> session;
    };
}

void Session::discharge(const std::string& id, bool failed, pn_delivery_t* delivery)
{
    QPID_LOG(debug, "Coordinator " <<  (failed ? " rollback" : " commit")
             << " transaction " << id);
    if (!tx.buffer.get() || id != tx.id) {
        throw Exception(qpid::amqp::error_conditions::transaction::UNKNOWN_ID,
                        Msg() << "Cannot discharge transaction " << id
                        << (tx.buffer.get() ? Msg() << ", current transaction is " << tx.id :
                            Msg() << ", no current transaction"));
    }
    tx.discharge = delivery;
    if (failed) {
        abort();
    } else {
        tx.buffer->begin();
        tx.buffer->startCommit(&connection.getBroker().getStore());
        AsyncCommit callback(shared_from_this());
        tx.buffer->end(callback);
    }
}

void Session::abort()
{
    if (tx.buffer) {
        tx.dischargeComplete();
        tx.buffer->rollback();
        txAborted();
        tx.buffer = boost::intrusive_ptr<TxBuffer>();
        QPID_LOG(debug, "Transaction " << tx.id << " rolled back");
    }
}

void Session::committed(bool sync)
{
    if (sync) {
        //this is on IO thread
        tx.dischargeComplete();
        if (tx.buffer.get()) {
            tx.buffer->endCommit(&connection.getBroker().getStore());
            txCommitted();
	    tx.buffer = boost::intrusive_ptr<TxBuffer>();
            QPID_LOG(debug, "Transaction " << tx.id << " comitted");
        } else {
            throw Exception(qpid::amqp::error_conditions::transaction::ROLLBACK, "tranaction vanished during async commit");
        }
    } else {
        //this is not on IO thread, need to delay processing until on IO thread
        if (tx.commitPending.boolCompareAndSwap(false, true)) {
            qpid::sys::Mutex::ScopedLock l(lock);
            if (!deleted) {
                out.activateOutput();
            }
        }
    }
}

void IncomingToQueue::handle(qpid::broker::Message& message, qpid::broker::TxBuffer* transaction)
{
    if (queue->isDeleted()) {
        std::stringstream msg;
        msg << " Queue " << queue->getName() << " has been deleted";
        throw Exception(qpid::amqp::error_conditions::RESOURCE_DELETED, msg.str());
    }
    try {
        queue->deliver(message, transaction);
    } catch (const qpid::SessionException& e) {
        throw Exception(qpid::amqp::error_conditions::PRECONDITION_FAILED, e.what());
    }
}

void IncomingToExchange::handle(qpid::broker::Message& message, qpid::broker::TxBuffer* transaction)
{
    if (exchange->isDestroyed())
        throw qpid::framing::ResourceDeletedException(QPID_MSG("Exchange " << exchange->getName() << " has been deleted."));
    authorise.route(exchange, message);
    DeliverableMessage deliverable(message, transaction);
    exchange->route(deliverable);
    if (!deliverable.delivered) {
        if (exchange->getAlternate()) {
            exchange->getAlternate()->route(deliverable);
        }
    }
}

void AnonymousRelay::handle(qpid::broker::Message& message, qpid::broker::TxBuffer* transaction)
{
    // need to retrieve AMQP 1.0 'to' field and resolve it to a queue or exchange
    std::string dest = message.getTo();
    authorise.access(dest, false, false);
    QPID_LOG(debug, "AnonymousRelay received message for " << dest);
    boost::shared_ptr<qpid::broker::Exchange> exchange;
    boost::shared_ptr<qpid::broker::Queue> queue;
    boost::shared_ptr<qpid::broker::amqp::Topic> topic;

    queue = context.getBroker().getQueues().find(dest);
    if (!queue) {
        topic = context.getTopics().get(dest);
        if (topic) {
            exchange = topic->getExchange();
        } else {
            exchange = context.getBroker().getExchanges().find(dest);
        }
    }

    try {
        if (queue) {
            authorise.incoming(queue);
            queue->deliver(message, transaction);
        } else if (exchange) {
            authorise.route(exchange, message);
            DeliverableMessage deliverable(message, transaction);
            exchange->route(deliverable);
        } else {
            QPID_LOG(info, "AnonymousRelay dropping message for " << dest);
        }
    } catch (const qpid::SessionException& e) {
        throw Exception(qpid::amqp::error_conditions::PRECONDITION_FAILED, e.what());
    }

}

void IncomingToCoordinator::deliver(boost::intrusive_ptr<qpid::broker::amqp::Message> message, pn_delivery_t* delivery)
{
    if (message && message->isTypedBody()) {
        QPID_LOG(debug, "Coordinator got message: @" << message->getBodyDescriptor() << " " << message->getTypedBody());
        if (message->getBodyDescriptor().match(DECLARE_SYMBOL, DECLARE_CODE)) {
            std::string id = session.declare();
            //encode the txn id in a 'declared' list on the disposition
            pn_data_t* data = pn_disposition_data(pn_delivery_local(delivery));
            pn_data_put_list(data);
            pn_data_enter(data);
            pn_data_put_binary(data, convert(id));
            pn_data_exit(data);
            pn_data_exit(data);
            pn_delivery_update(delivery, DECLARED_CODE);
            pn_delivery_settle(delivery);
            session.incomingMessageAccepted();
            QPID_LOG(debug, "Coordinator declared transaction " << id);
        } else if (message->getBodyDescriptor().match(DISCHARGE_SYMBOL, DISCHARGE_CODE)) {
            if (message->getTypedBody().getType() == qpid::types::VAR_LIST) {
                qpid::types::Variant::List args = message->getTypedBody().asList();
                qpid::types::Variant::List::const_iterator i = args.begin();
                if (i != args.end()) {
                    std::string id = *i;
                    bool failed = ++i != args.end() ? i->asBool() : false;
                    session.pending_accept(delivery);
                    session.discharge(id, failed, delivery);
                }

            } else {
                throw framing::IllegalArgumentException(
                    Msg() << "Coordinator unknown message: @" <<
                    message->getBodyDescriptor() << " " << message->getTypedBody());
            }
        }
    }
}

Session::Transaction::Transaction(Session& s) :
    session(s), id((boost::format("%1%") % &s).str()), discharge(0) {}

// Called in IO thread to signal completion of dischage by settling discharge message.
void Session::Transaction::dischargeComplete() {
    if (buffer.get() && discharge) {
        session.accepted(discharge, false); // Queue up accept and activate output.
        discharge = 0;
    }
}

}}} // namespace qpid::broker::amqp
