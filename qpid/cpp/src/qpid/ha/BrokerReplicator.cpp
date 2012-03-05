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
#include "BrokerReplicator.h"
#include "QueueReplicator.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/Link.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/log/Statement.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/broker/SessionHandler.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qmf/org/apache/qpid/broker/EventBind.h"
#include "qmf/org/apache/qpid/broker/EventUnbind.h"
#include "qmf/org/apache/qpid/broker/EventExchangeDeclare.h"
#include "qmf/org/apache/qpid/broker/EventExchangeDelete.h"
#include "qmf/org/apache/qpid/broker/EventQueueDeclare.h"
#include "qmf/org/apache/qpid/broker/EventQueueDelete.h"
#include "qmf/org/apache/qpid/broker/EventSubscribe.h"
#include <algorithm>

namespace qpid {
namespace ha {

using qmf::org::apache::qpid::broker::EventBind;
using qmf::org::apache::qpid::broker::EventUnbind;
using qmf::org::apache::qpid::broker::EventExchangeDeclare;
using qmf::org::apache::qpid::broker::EventExchangeDelete;
using qmf::org::apache::qpid::broker::EventQueueDeclare;
using qmf::org::apache::qpid::broker::EventQueueDelete;
using qmf::org::apache::qpid::broker::EventSubscribe;
using namespace framing;
using std::string;
using types::Variant;
using namespace broker;

namespace {

const string QPID_CONFIGURATION_REPLICATOR("qpid.configuration-replicator");
const string QPID_REPLICATE("qpid.replicate");

const string CLASS_NAME("_class_name");
const string EVENT("_event");
const string OBJECT_NAME("_object_name");
const string PACKAGE_NAME("_package_name");
const string QUERY_RESPONSE("_query_response");
const string SCHEMA_ID("_schema_id");
const string VALUES("_values");

const string ALTEX("altEx");
const string ARGS("args");
const string ARGUMENTS("arguments");
const string AUTODEL("autoDel");
const string AUTODELETE("autoDelete");
const string BIND("bind");
const string UNBIND("unbind");
const string BINDING("binding");
const string CREATED("created");
const string DISP("disp");
const string DURABLE("durable");
const string EXCHANGE("exchange");
const string EXNAME("exName");
const string EXTYPE("exType");
const string KEY("key");
const string NAME("name");
const string QNAME("qName");
const string QUEUE("queue");
const string RHOST("rhost");
const string TYPE("type");
const string USER("user");

const string AGENT_IND_EVENT_ORG_APACHE_QPID_BROKER("agent.ind.event.org_apache_qpid_broker.#");
const string QMF2("qmf2");
const string QMF_CONTENT("qmf.content");
const string QMF_DEFAULT_TOPIC("qmf.default.topic");
const string QMF_OPCODE("qmf.opcode");

const string _WHAT("_what");
const string _CLASS_NAME("_class_name");
const string _PACKAGE_NAME("_package_name");
const string _SCHEMA_ID("_schema_id");
const string OBJECT("OBJECT");
const string ORG_APACHE_QPID_BROKER("org.apache.qpid.broker");
const string QMF_DEFAULT_DIRECT("qmf.default.direct");
const string _QUERY_REQUEST("_query_request");
const string BROKER("broker");

bool isQMFv2(const Message& message) {
    const framing::MessageProperties* props = message.getProperties<framing::MessageProperties>();
    return props && props->getAppId() == QMF2;
}

template <class T> bool match(Variant::Map& schema) {
    return T::match(schema[CLASS_NAME], schema[PACKAGE_NAME]);
}

enum ReplicateLevel { RL_NONE=0, RL_CONFIGURATION, RL_MESSAGES };
const string S_NONE="none";
const string S_CONFIGURATION="configuration";
const string S_MESSAGES="messages";

ReplicateLevel replicateLevel(const string& level) {
    if (level == S_NONE) return RL_NONE;
    if (level == S_CONFIGURATION) return RL_CONFIGURATION;
    if (level == S_MESSAGES) return RL_MESSAGES;
    throw Exception("Invalid value for "+QPID_REPLICATE+": "+level);
}

ReplicateLevel replicateLevel(const framing::FieldTable& f) {
    if (f.isSet(QPID_REPLICATE)) return replicateLevel(f.getAsString(QPID_REPLICATE));
    else return RL_NONE;
}

ReplicateLevel replicateLevel(const Variant::Map& m) {
    Variant::Map::const_iterator i = m.find(QPID_REPLICATE);
    if (i != m.end()) return replicateLevel(i->second.asString());
    else return RL_NONE;
}

void sendQuery(const string className, const string& queueName, SessionHandler& sessionHandler) {
    framing::AMQP_ServerProxy peer(sessionHandler.out);
    Variant::Map request;
    request[_WHAT] = OBJECT;
    Variant::Map schema;
    schema[_CLASS_NAME] = className;
    schema[_PACKAGE_NAME] = ORG_APACHE_QPID_BROKER;
    request[_SCHEMA_ID] = schema;

    AMQFrame method((MessageTransferBody(ProtocolVersion(), QMF_DEFAULT_DIRECT, 0, 0)));
    method.setBof(true);
    method.setEof(false);
    method.setBos(true);
    method.setEos(true);
    AMQHeaderBody headerBody;
    MessageProperties* props = headerBody.get<MessageProperties>(true);
    props->setReplyTo(qpid::framing::ReplyTo("", queueName));
    props->setAppId(QMF2);
    props->getApplicationHeaders().setString(QMF_OPCODE, _QUERY_REQUEST);
    headerBody.get<qpid::framing::DeliveryProperties>(true)->setRoutingKey(BROKER);
    AMQFrame header(headerBody);
    header.setBof(false);
    header.setEof(false);
    header.setBos(true);
    header.setEos(true);
    AMQContentBody data;
    qpid::amqp_0_10::MapCodec::encode(request, data.getData());
    AMQFrame content(data);
    content.setBof(false);
    content.setEof(true);
    content.setBos(true);
    content.setEos(true);
    sessionHandler.out->handle(method);
    sessionHandler.out->handle(header);
    sessionHandler.out->handle(content);
}

// Like Variant::asMap but treat void value as an empty map.
Variant::Map asMapVoid(const Variant& value) {
    if (!value.isVoid()) return value.asMap();
    else return Variant::Map();
}

} // namespace

BrokerReplicator::~BrokerReplicator() {}

BrokerReplicator::BrokerReplicator(const boost::shared_ptr<Link>& l)
    : Exchange(QPID_CONFIGURATION_REPLICATOR), broker(*l->getBroker()), link(l)
{
    QPID_LOG(info, "HA: Backup replicating from " <<
             link->getTransport() << ":" << link->getHost() << ":" << link->getPort());
    broker.getLinks().declare(
        link->getHost(), link->getPort(),
        false,              // durable
        QPID_CONFIGURATION_REPLICATOR, // src
        QPID_CONFIGURATION_REPLICATOR, // dest
        "",                 // key
        false,              // isQueue
        false,              // isLocal
        "",                 // id/tag
        "",                 // excludes
        false,              // dynamic
        0,                  // sync?
        boost::bind(&BrokerReplicator::initializeBridge, this, _1, _2)
    );
}

// This is called in the connection IO thread when the bridge is started.
void BrokerReplicator::initializeBridge(Bridge& bridge, SessionHandler& sessionHandler) {
    framing::AMQP_ServerProxy peer(sessionHandler.out);
    string queueName = bridge.getQueueName();
    const qmf::org::apache::qpid::broker::ArgsLinkBridge& args(bridge.getArgs());

    //declare and bind an event queue
    peer.getQueue().declare(queueName, "", false, false, true, true, FieldTable());
    peer.getExchange().bind(queueName, QMF_DEFAULT_TOPIC, AGENT_IND_EVENT_ORG_APACHE_QPID_BROKER, FieldTable());
    //subscribe to the queue
    peer.getMessage().subscribe(queueName, args.i_dest, 1, 0, false, "", 0, FieldTable());
    peer.getMessage().flow(args.i_dest, 0, 0xFFFFFFFF);
    peer.getMessage().flow(args.i_dest, 1, 0xFFFFFFFF);

    //issue a query request for queues and another for exchanges using event queue as the reply-to address
    sendQuery(QUEUE, queueName, sessionHandler);
    sendQuery(EXCHANGE, queueName, sessionHandler);
    sendQuery(BINDING, queueName, sessionHandler);
    QPID_LOG(debug, "HA: Backup activated configuration bridge: " << queueName);
}

// FIXME aconway 2011-12-02: error handling in route.
void BrokerReplicator::route(Deliverable& msg) {
    const framing::FieldTable* headers = msg.getMessage().getApplicationHeaders();
    Variant::List list;
    try {
        if (!isQMFv2(msg.getMessage()) || !headers)
            throw Exception("Unexpected message, not QMF2 event or query response.");
        // decode as list
        string content = msg.getMessage().getFrames().getContent();
        amqp_0_10::ListCodec::decode(content, list);

        if (headers->getAsString(QMF_CONTENT) == EVENT) {
            for (Variant::List::iterator i = list.begin(); i != list.end(); ++i) {
                Variant::Map& map = i->asMap();
                Variant::Map& schema = map[SCHEMA_ID].asMap();
                Variant::Map& values = map[VALUES].asMap();
                if      (match<EventQueueDeclare>(schema)) doEventQueueDeclare(values);
                else if (match<EventQueueDelete>(schema)) doEventQueueDelete(values);
                else if (match<EventExchangeDeclare>(schema)) doEventExchangeDeclare(values);
                else if (match<EventExchangeDelete>(schema)) doEventExchangeDelete(values);
                else if (match<EventBind>(schema)) doEventBind(values);
                else if (match<EventUnbind>(schema)) doEventUnbind(values);
            }
        } else if (headers->getAsString(QMF_OPCODE) == QUERY_RESPONSE) {
            for (Variant::List::iterator i = list.begin(); i != list.end(); ++i) {
                string type = i->asMap()[SCHEMA_ID].asMap()[CLASS_NAME];
                Variant::Map& values = i->asMap()[VALUES].asMap();
                framing::FieldTable args;
                amqp_0_10::translate(asMapVoid(values[ARGUMENTS]), args);
                if      (type == QUEUE) doResponseQueue(values);
                else if (type == EXCHANGE) doResponseExchange(values);
                else if (type == BINDING) doResponseBind(values);
                else QPID_LOG(error, "HA: Backup received unknown response type=" << type
                              << " values=" << values);
            }
        } else QPID_LOG(error, "HA: Backup received unexpected message: " << *headers);
    } catch (const std::exception& e) {
        QPID_LOG(error, "HA: Backup replication error: " << e.what() << ": while handling: " << list);
    }
}

void BrokerReplicator::doEventQueueDeclare(Variant::Map& values) {
    QPID_LOG(debug, "HA: Backup queue declare event " << values);
    string name = values[QNAME].asString();
    Variant::Map argsMap = asMapVoid(values[ARGS]);
    if (values[DISP] == CREATED && replicateLevel(argsMap)) {
        framing::FieldTable args;
        amqp_0_10::translate(argsMap, args);
        std::pair<boost::shared_ptr<Queue>, bool> result =
            broker.createQueue(
                name,
                values[DURABLE].asBool(),
                values[AUTODEL].asBool(),
                0 /*i.e. no owner regardless of exclusivity on master*/,
                values[ALTEX].asString(),
                args,
                values[USER].asString(),
                values[RHOST].asString());
        if (result.second) {
            // FIXME aconway 2011-11-22: should delete old queue and
            // re-create from event.
            // Events are always up to date, whereas responses may be
            // out of date.
            QPID_LOG(debug, "HA: Backup created queue: " << name);
            startQueueReplicator(result.first);
        } else {
            // FIXME aconway 2011-12-02: what's the right way to handle this?
            QPID_LOG(warning, "HA: Backup queue already exists: " << name);
        }
    }
}

void BrokerReplicator::doEventQueueDelete(Variant::Map& values) {
    QPID_LOG(debug, "HA: Backup queue delete event " << values);
    // The remote queue has already been deleted so replicator
    // sessions may be closed by a "queue deleted" exception.
    string name = values[QNAME].asString();
    boost::shared_ptr<Queue> queue = broker.getQueues().find(name);
    if (queue && replicateLevel(queue->getSettings())) {
        QPID_LOG(debug, "HA: Backup deleting queue: " << name);
        string rname = QueueReplicator::replicatorName(name);
        boost::shared_ptr<broker::Exchange> ex = broker.getExchanges().find(rname);
        boost::shared_ptr<QueueReplicator> qr = boost::dynamic_pointer_cast<QueueReplicator>(ex);
        if (qr) qr->deactivate();
        // QueueReplicator's bridge is now queued for destruction but may not
        // actually be destroyed, deleting the exhange
        broker.getExchanges().destroy(rname);
        broker.deleteQueue(name, values[USER].asString(), values[RHOST].asString());
    }
}

void BrokerReplicator::doEventExchangeDeclare(Variant::Map& values) {
    QPID_LOG(debug, "HA: Backup exchange declare event " << values);
    Variant::Map argsMap(asMapVoid(values[ARGS]));
    if (values[DISP] == CREATED && replicateLevel(argsMap)) {
        string name = values[EXNAME].asString();
        framing::FieldTable args;
        amqp_0_10::translate(argsMap, args);
        if (broker.createExchange(
                name,
                values[EXTYPE].asString(),
                values[DURABLE].asBool(),
                values[ALTEX].asString(),
                args,
                values[USER].asString(),
                values[RHOST].asString()).second)
        {
                    QPID_LOG(debug, "HA: Backup created exchange: " << name);
        } else {
            // FIXME aconway 2011-11-22: should delete pre-exisitng exchange
            // and re-create from event. See comment in doEventQueueDeclare.
            QPID_LOG(warning, "HA: Backup exchange already exists: " << name);
        }
    }
}

void BrokerReplicator::doEventExchangeDelete(Variant::Map& values) {
    QPID_LOG(debug, "HA: Backup exchange delete event " << values);
    string name = values[EXNAME].asString();
    try {
        boost::shared_ptr<Exchange> exchange = broker.getExchanges().find(name);
        if (exchange && replicateLevel(exchange->getArgs())) {
            QPID_LOG(debug, "HA: Backup deleting exchange:" << name);
            broker.deleteExchange(
                name,
                values[USER].asString(),
                values[RHOST].asString());
        }
    } catch (const framing::NotFoundException&) {}
}

void BrokerReplicator::doEventBind(Variant::Map& values) {
    QPID_LOG(debug, "HA: Backup bind event " << values);
    boost::shared_ptr<Exchange> exchange =
        broker.getExchanges().find(values[EXNAME].asString());
    boost::shared_ptr<Queue> queue =
        broker.getQueues().find(values[QNAME].asString());
    // We only replicate binds for a replicated queue to replicated
    // exchange that both exist locally.
    if (exchange && replicateLevel(exchange->getArgs()) &&
        queue && replicateLevel(queue->getSettings()))
    {
        framing::FieldTable args;
        amqp_0_10::translate(asMapVoid(values[ARGS]), args);
        string key = values[KEY].asString();
        QPID_LOG(debug, "HA: Backup replicated binding exchange=" << exchange->getName()
                 << " queue=" << queue->getName()
                 << " key=" << key);
        exchange->bind(queue, key, &args);
    }
}

void BrokerReplicator::doEventUnbind(Variant::Map& values) {
    QPID_LOG(debug, "HA: Backup unbind event " << values);
    boost::shared_ptr<Exchange> exchange =
        broker.getExchanges().find(values[EXNAME].asString());
    boost::shared_ptr<Queue> queue =
        broker.getQueues().find(values[QNAME].asString());
    // We only replicate unbinds for a replicated queue to replicated
    // exchange that both exist locally.
    if (exchange && replicateLevel(exchange->getArgs()) &&
        queue && replicateLevel(queue->getSettings()))
    {
        framing::FieldTable args;
        amqp_0_10::translate(asMapVoid(values[ARGS]), args);
        string key = values[KEY].asString();
        QPID_LOG(debug, "HA: Backup replicated unbinding exchange=" << exchange->getName()
                 << " queue=" << queue->getName()
                 << " key=" << key);
        exchange->unbind(queue, key, &args);
    }
}

void BrokerReplicator::doResponseQueue(Variant::Map& values) {
    QPID_LOG(debug, "HA: Backup queue response " << values);
    // FIXME aconway 2011-11-22: more flexible ways & defaults to indicate replication
    Variant::Map argsMap(asMapVoid(values[ARGUMENTS]));
    if (!replicateLevel(argsMap)) return;
    framing::FieldTable args;
    amqp_0_10::translate(argsMap, args);
    string name(values[NAME].asString());
    std::pair<boost::shared_ptr<Queue>, bool> result =
        broker.createQueue(
            name,
            values[DURABLE].asBool(),
            values[AUTODELETE].asBool(),
            0 /*i.e. no owner regardless of exclusivity on master*/,
            ""/*TODO: need to include alternate-exchange*/,
            args,
            ""/*TODO: who is the user?*/,
            ""/*TODO: what should we use as connection id?*/);
    if (result.second) {
        QPID_LOG(debug, "HA: Backup created catch-up queue: " << values[NAME]);
        startQueueReplicator(result.first);
    } else {
        // FIXME aconway 2011-11-22: Normal to find queue already
        // exists if we're failing over.
        QPID_LOG(warning, "HA: Backup catch-up queue already exists: " << name);
    }
}

void BrokerReplicator::doResponseExchange(Variant::Map& values) {
    QPID_LOG(debug, "HA: Backup exchange response " << values);
    Variant::Map argsMap(asMapVoid(values[ARGUMENTS]));
    if (!replicateLevel(argsMap)) return;
    framing::FieldTable args;
    amqp_0_10::translate(argsMap, args);
    if (broker.createExchange(
            values[NAME].asString(),
            values[TYPE].asString(),
            values[DURABLE].asBool(),
            ""/*TODO: need to include alternate-exchange*/,
            args,
            ""/*TODO: who is the user?*/,
            ""/*TODO: what should we use as connection id?*/).second)
    {
        QPID_LOG(debug, "HA: Backup catch-up exchange: " << values[NAME]);
    } else {
        QPID_LOG(warning, "HA: Backup catch-up exchange already exists:  " << values[QNAME]);
    }
}

namespace {
const std::string QUEUE_REF_PREFIX("org.apache.qpid.broker:queue:");
const std::string EXCHANGE_REF_PREFIX("org.apache.qpid.broker:exchange:");

std::string getRefName(const std::string& prefix, const Variant& ref) {
    Variant::Map map(ref.asMap());
    Variant::Map::const_iterator i = map.find(OBJECT_NAME);
    if (i == map.end())
        throw Exception(QPID_MSG("Replicator: invalid object reference: " << ref));
    const std::string name = i->second.asString();
    if (name.compare(0, prefix.size(), prefix) != 0)
        throw Exception(QPID_MSG("Replicator: unexpected reference prefix: " << name));
    std::string ret = name.substr(prefix.size());
    return ret;
}

const std::string EXCHANGE_REF("exchangeRef");
const std::string QUEUE_REF("queueRef");

} // namespace

void BrokerReplicator::doResponseBind(Variant::Map& values) {
    QPID_LOG(debug, "HA: Backup bind response " << values);
    std::string exName = getRefName(EXCHANGE_REF_PREFIX, values[EXCHANGE_REF]);
    std::string qName = getRefName(QUEUE_REF_PREFIX, values[QUEUE_REF]);
    boost::shared_ptr<Exchange> exchange = broker.getExchanges().find(exName);
    boost::shared_ptr<Queue> queue = broker.getQueues().find(qName);
    // FIXME aconway 2011-11-24: more flexible configuration for binding replication.

    // Automatically replicate binding if queue and exchange exist and are replicated
    if (exchange && replicateLevel(exchange->getArgs()) &&
        queue && replicateLevel(queue->getSettings()))
    {
        framing::FieldTable args;
        amqp_0_10::translate(asMapVoid(values[ARGUMENTS]), args);
        string key = values[KEY].asString();
        exchange->bind(queue, key, &args);
        QPID_LOG(debug, "HA: Backup catch-up binding: exchange=" << exchange->getName()
                 << " queue=" << queue->getName()
                 << " key=" << key);
    }
}

void BrokerReplicator::startQueueReplicator(const boost::shared_ptr<Queue>& queue) {
    if (replicateLevel(queue->getSettings()) == RL_MESSAGES) {
        boost::shared_ptr<QueueReplicator> qr(new QueueReplicator(queue, link));
        broker.getExchanges().registerExchange(qr);
        qr->activate();
    }
}

bool BrokerReplicator::bind(boost::shared_ptr<Queue>, const string&, const framing::FieldTable*) { return false; }
bool BrokerReplicator::unbind(boost::shared_ptr<Queue>, const string&, const framing::FieldTable*) { return false; }
bool BrokerReplicator::isBound(boost::shared_ptr<Queue>, const string* const, const framing::FieldTable* const) { return false; }

string BrokerReplicator::getType() const { return QPID_CONFIGURATION_REPLICATOR; }

}} // namespace broker
