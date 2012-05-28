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
#include "HaBroker.h"
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
#include <sstream>
#include <assert.h>

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
const string EXCL("excl");
const string EXCLUSIVE("exclusive");
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
const string HA_BROKER("habroker");

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
const string ORG_APACHE_QPID_HA("org.apache.qpid.ha");
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

void sendQuery(const string& packageName, const string& className, const string& queueName, SessionHandler& sessionHandler) {
    framing::AMQP_ServerProxy peer(sessionHandler.out);
    Variant::Map request;
    request[_WHAT] = OBJECT;
    Variant::Map schema;
    schema[_CLASS_NAME] = className;
    schema[_PACKAGE_NAME] = packageName;
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

BrokerReplicator::BrokerReplicator(HaBroker& hb, const boost::shared_ptr<Link>& l)
    : Exchange(QPID_CONFIGURATION_REPLICATOR),
      logPrefix(hb),
      haBroker(hb), broker(hb.getBroker()), link(l)
{
    framing::Uuid uuid(true);
    const std::string name(QPID_CONFIGURATION_REPLICATOR + ".bridge." + uuid.str());
    broker.getLinks().declare(
        name,               // name for bridge
        *link,              // parent
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

// FIXME aconway 2012-05-07: reference cycled between Link and BrokerReplicator.
BrokerReplicator::~BrokerReplicator() { link->close(); }

// This is called in the connection IO thread when the bridge is started.
void BrokerReplicator::initializeBridge(Bridge& bridge, SessionHandler& sessionHandler) {

    switch (haBroker.getStatus()) {
      case JOINING:
        haBroker.setStatus(CATCHUP);
      case CATCHUP:
        // FIXME aconway 2012-04-27: distinguish catchup case, below.
        break;
      case READY:
        // FIXME aconway 2012-04-27: distinguish ready case, reconnect to other backup.
        break;
      case RECOVERING:
      case ACTIVE:
        // FIXME aconway 2012-04-27: link is connected to self!
        // Promotion should close the link before allowing connections.
        return;
        break;
      case STANDALONE:
        return;
    }

    framing::AMQP_ServerProxy peer(sessionHandler.out);
    string queueName = bridge.getQueueName();
    const qmf::org::apache::qpid::broker::ArgsLinkBridge& args(bridge.getArgs());

    //declare and bind an event queue
    FieldTable declareArgs;
    declareArgs.setString(QPID_REPLICATE, printable(NONE).str());
    peer.getQueue().declare(queueName, "", false, false, true, true, declareArgs);
    peer.getExchange().bind(queueName, QMF_DEFAULT_TOPIC, AGENT_IND_EVENT_ORG_APACHE_QPID_BROKER, FieldTable());
    //subscribe to the queue
    peer.getMessage().subscribe(queueName, args.i_dest, 1, 0, false, "", 0, FieldTable());
    peer.getMessage().flow(args.i_dest, 0, 0xFFFFFFFF);
    peer.getMessage().flow(args.i_dest, 1, 0xFFFFFFFF);

    // Issue a query request for queues, exchanges, bindings and the habroker
    // using event queue as the reply-to address
    sendQuery(ORG_APACHE_QPID_HA, HA_BROKER, queueName, sessionHandler);
    sendQuery(ORG_APACHE_QPID_BROKER, QUEUE, queueName, sessionHandler);
    sendQuery(ORG_APACHE_QPID_BROKER, EXCHANGE, queueName, sessionHandler);
    sendQuery(ORG_APACHE_QPID_BROKER, BINDING, queueName, sessionHandler);
    QPID_LOG(debug, logPrefix << "Opened configuration bridge: " << queueName);
}

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
                Variant::Map& map = i->asMap();
                string type = map[SCHEMA_ID].asMap()[CLASS_NAME].asString();
                Variant::Map& values = map[VALUES].asMap();
                framing::FieldTable args;
                amqp_0_10::translate(asMapVoid(values[ARGUMENTS]), args);
                if      (type == QUEUE) doResponseQueue(values);
                else if (type == EXCHANGE) doResponseExchange(values);
                else if (type == BINDING) doResponseBind(values);
                else if (type == HA_BROKER) doResponseHaBroker(values);
            }
        }
    } catch (const std::exception& e) {
        QPID_LOG(critical, logPrefix << "Configuration failed: " << e.what()
                 << ": while handling: " << list);
        throw;
    }
}

void BrokerReplicator::doEventQueueDeclare(Variant::Map& values) {
    string name = values[QNAME].asString();
    Variant::Map argsMap = asMapVoid(values[ARGS]);
    if (!isReplicated(
            values[ARGS].asMap(), values[AUTODEL].asBool(), values[EXCL].asBool()))
        return;
    if (values[DISP] == CREATED && haBroker.replicateLevel(argsMap)) {
        framing::FieldTable args;
        amqp_0_10::translate(argsMap, args);
        // If we already have a queue with this name, replace it.
        // The queue was definitely created on the primary.
        if (broker.getQueues().find(name)) {
            broker.getQueues().destroy(name);
            QPID_LOG(warning, logPrefix << "Queue declare event, replaced exsiting: "
                     << name);
        }
        std::pair<boost::shared_ptr<Queue>, bool> result =
            broker.createQueue(
                name,
                values[DURABLE].asBool(),
                values[AUTODEL].asBool(),
                0, // no owner regardless of exclusivity on primary
                values[ALTEX].asString(),
                args,
                values[USER].asString(),
                values[RHOST].asString());
        assert(result.second);  // Should be true since we destroyed existing queue above
        QPID_LOG(debug, logPrefix << "Queue declare event: " << name);
        startQueueReplicator(result.first);
    }
}

boost::shared_ptr<QueueReplicator> BrokerReplicator::findQueueReplicator(
    const std::string& qname)
{
    string rname = QueueReplicator::replicatorName(qname);
    boost::shared_ptr<broker::Exchange> ex = broker.getExchanges().find(rname);
    return boost::dynamic_pointer_cast<QueueReplicator>(ex);
}

void BrokerReplicator::doEventQueueDelete(Variant::Map& values) {
    // The remote queue has already been deleted so replicator
    // sessions may be closed by a "queue deleted" exception.
    string name = values[QNAME].asString();
    boost::shared_ptr<Queue> queue = broker.getQueues().find(name);
    if (!queue) {
        QPID_LOG(warning, logPrefix << "Queue delete event, does not exist: " << name);
    } else if (!haBroker.replicateLevel(queue->getSettings())) {
        QPID_LOG(warning, logPrefix << "Queue delete event, not replicated: " << name);
    } else {
        boost::shared_ptr<QueueReplicator> qr = findQueueReplicator(name);
        if (qr) {
            qr->deactivate();
            haBroker.deactivatedBackup(name);
            // QueueReplicator's bridge is now queued for destruction but may not
            // actually be destroyed.
            broker.getExchanges().destroy(qr->getName());
        }
        broker.deleteQueue(name, values[USER].asString(), values[RHOST].asString());
        QPID_LOG(debug, logPrefix << "Queue delete event: " << name);
    }
}

void BrokerReplicator::doEventExchangeDeclare(Variant::Map& values) {
    Variant::Map argsMap(asMapVoid(values[ARGS]));
    if (!haBroker.replicateLevel(argsMap)) return; // Not a replicated exchange.
    if (values[DISP] == CREATED && haBroker.replicateLevel(argsMap)) {
        string name = values[EXNAME].asString();
        framing::FieldTable args;
        amqp_0_10::translate(argsMap, args);
        // If we already have a exchange with this name, replace it.
        // The exchange was definitely created on the primary.
        if (broker.getExchanges().find(name)) {
            broker.getExchanges().destroy(name);
            QPID_LOG(warning, logPrefix << "Exchange declare event, replaced exsiting: " << name)
                }
        std::pair<boost::shared_ptr<Exchange>, bool> result =
            broker.createExchange(
                name,
                values[EXTYPE].asString(),
                values[DURABLE].asBool(),
                values[ALTEX].asString(),
                args,
                values[USER].asString(),
                values[RHOST].asString());
        assert(result.second);
        QPID_LOG(debug, logPrefix << "Exchange declare event: " << name);
    }
}

void BrokerReplicator::doEventExchangeDelete(Variant::Map& values) {
    string name = values[EXNAME].asString();
    boost::shared_ptr<Exchange> exchange = broker.getExchanges().find(name);
    if (!exchange) {
        QPID_LOG(warning, logPrefix << "Exchange delete event, does not exist: " << name);
    } else if (!haBroker.replicateLevel(exchange->getArgs())) {
        QPID_LOG(warning, logPrefix << "Exchange delete event, not replicated: " << name);
    } else {
        QPID_LOG(debug, logPrefix << "Exchange delete event:" << name);
        broker.deleteExchange(
            name,
            values[USER].asString(),
            values[RHOST].asString());
    }
}

void BrokerReplicator::doEventBind(Variant::Map& values) {
    boost::shared_ptr<Exchange> exchange =
        broker.getExchanges().find(values[EXNAME].asString());
    boost::shared_ptr<Queue> queue =
        broker.getQueues().find(values[QNAME].asString());
    // We only replicate binds for a replicated queue to replicated
    // exchange that both exist locally.
    if (exchange && haBroker.replicateLevel(exchange->getArgs()) &&
        queue && haBroker.replicateLevel(queue->getSettings()))
    {
        framing::FieldTable args;
        amqp_0_10::translate(asMapVoid(values[ARGS]), args);
        string key = values[KEY].asString();
        exchange->bind(queue, key, &args);
        QPID_LOG(debug, logPrefix << "Bind event: exchange=" << exchange->getName()
                 << " queue=" << queue->getName()
                 << " key=" << key);
    }
}

void BrokerReplicator::doEventUnbind(Variant::Map& values) {
    boost::shared_ptr<Exchange> exchange =
        broker.getExchanges().find(values[EXNAME].asString());
    boost::shared_ptr<Queue> queue =
        broker.getQueues().find(values[QNAME].asString());
    // We only replicate unbinds for a replicated queue to replicated
    // exchange that both exist locally.
    if (exchange && haBroker.replicateLevel(exchange->getArgs()) &&
        queue && haBroker.replicateLevel(queue->getSettings()))
    {
        framing::FieldTable args;
        amqp_0_10::translate(asMapVoid(values[ARGS]), args);
        string key = values[KEY].asString();
        exchange->unbind(queue, key, &args);
        QPID_LOG(debug, logPrefix << "Unbind event: exchange=" << exchange->getName()
                 << " queue=" << queue->getName()
                 << " key=" << key);
    }
}

void BrokerReplicator::doResponseQueue(Variant::Map& values) {
    Variant::Map argsMap(asMapVoid(values[ARGUMENTS]));
    if (!isReplicated(values[ARGUMENTS].asMap(),
                      values[AUTODELETE].asBool(),
                      values[EXCLUSIVE].asBool()))
        return;
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
    // It is normal for the queue to already exist if we are failing over.
    if (result.second) startQueueReplicator(result.first);
    QPID_LOG(debug, logPrefix << "Queue response: " << name);
}

void BrokerReplicator::doResponseExchange(Variant::Map& values) {
    Variant::Map argsMap(asMapVoid(values[ARGUMENTS]));
    if (!haBroker.replicateLevel(argsMap)) return;
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
        QPID_LOG(debug, logPrefix << "Exchange response: " << values[NAME].asString());
    } else {
        QPID_LOG(warning, logPrefix << "Exchange response, already exists: " <<
                 values[NAME].asString());
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
    std::string exName = getRefName(EXCHANGE_REF_PREFIX, values[EXCHANGE_REF]);
    std::string qName = getRefName(QUEUE_REF_PREFIX, values[QUEUE_REF]);
    boost::shared_ptr<Exchange> exchange = broker.getExchanges().find(exName);
    boost::shared_ptr<Queue> queue = broker.getQueues().find(qName);

    // Automatically replicate binding if queue and exchange exist and are replicated
    if (exchange && haBroker.replicateLevel(exchange->getArgs()) &&
        queue && haBroker.replicateLevel(queue->getSettings()))
    {
        framing::FieldTable args;
        amqp_0_10::translate(asMapVoid(values[ARGUMENTS]), args);
        string key = values[KEY].asString();
        exchange->bind(queue, key, &args);
        QPID_LOG(debug, logPrefix << "Bind response: exchange=" << exchange->getName()
                 << " queue=" << queue->getName()
                 << " key=" << key);
    }
}

namespace {
const string REPLICATE_DEFAULT="replicateDefault";
}

// Received the ha-broker configuration object for the primary broker.
void BrokerReplicator::doResponseHaBroker(Variant::Map& values) {
    try {
        ReplicateLevel mine = haBroker.getSettings().replicateDefault.get();
        ReplicateLevel primary = haBroker.replicateLevel(values[REPLICATE_DEFAULT].asString());
        if (mine != primary) {
            QPID_LOG(critical, logPrefix << "Replicate default on backup (" << mine
                     << ") does not match primary (" <<  primary << ")");
            haBroker.shutdown();
        }
    } catch (const std::exception& e) {
        QPID_LOG(critical, logPrefix << "Invalid replicate default from primary: "
                 << e.what());
        haBroker.shutdown();
    }
}

namespace {
const std::string AUTO_DELETE_TIMEOUT("qpid.auto_delete_timeout");
}

bool BrokerReplicator::isReplicated(
    const Variant::Map& args, bool autodelete, bool exclusive)
{
    bool ignore = autodelete && exclusive && args.find(AUTO_DELETE_TIMEOUT) == args.end();
    return haBroker.replicateLevel(args) && !ignore;
}

void BrokerReplicator::startQueueReplicator(const boost::shared_ptr<Queue>& queue)
{
    if (haBroker.replicateLevel(queue->getSettings()) == ALL) {
        boost::shared_ptr<QueueReplicator> qr(
            new QueueReplicator(haBroker, queue, link));
        if (!broker.getExchanges().registerExchange(qr))
            throw Exception(QPID_MSG("Duplicate queue replicator " << qr->getName()));
        qr->activate();
        haBroker.activatedBackup(queue->getName());
    }
}

bool BrokerReplicator::bind(boost::shared_ptr<Queue>, const string&, const framing::FieldTable*) { return false; }
bool BrokerReplicator::unbind(boost::shared_ptr<Queue>, const string&, const framing::FieldTable*) { return false; }
bool BrokerReplicator::isBound(boost::shared_ptr<Queue>, const string* const, const framing::FieldTable* const) { return false; }

string BrokerReplicator::getType() const { return QPID_CONFIGURATION_REPLICATOR; }

void BrokerReplicator::ready() {
    assert(haBroker.getStatus() == CATCHUP);
    haBroker.setStatus(READY);
}

}} // namespace broker
