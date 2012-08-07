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
#include "qpid/broker/Connection.h"
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
#include "qmf/org/apache/qpid/ha/EventMembersUpdate.h"
#include <algorithm>
#include <sstream>
#include <iostream>
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
using qmf::org::apache::qpid::ha::EventMembersUpdate;
using namespace framing;
using std::string;
using std::ostream;
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
const string ALTEXCHANGE("altExchange");
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
const string TYPE("type");
const string HA_BROKER("habroker");
const string PARTIAL("partial");

const string AGENT_EVENT_BROKER("agent.ind.event.org_apache_qpid_broker.#");
const string AGENT_EVENT_HA("agent.ind.event.org_apache_qpid_ha.#");
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
const string MEMBERS("members");

bool isQMFv2(const Message& message) {
    const framing::MessageProperties* props = message.getProperties<framing::MessageProperties>();
    return props && props->getAppId() == QMF2;
}

template <class T> bool match(Variant::Map& schema) {
    return T::match(schema[CLASS_NAME], schema[PACKAGE_NAME]);
}

void sendQuery(const string& packageName, const string& className, const string& queueName,
               SessionHandler& sessionHandler)
{
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
    headerBody.get<qpid::framing::MessageProperties>(true)->setCorrelationId(className);
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
      logPrefix("Backup: "), replicationTest(hb.getReplicationTest()),
      haBroker(hb), broker(hb.getBroker()), link(l),
      initialized(false),
      alternates(hb.getBroker().getExchanges())
{}

void BrokerReplicator::initialize() {
    // Can't do this in the constructor because we need a shared_ptr to this.
    types::Uuid uuid(true);
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
        // shared_ptr keeps this in memory until outstanding initializeBridge
        // calls are run.
        boost::bind(&BrokerReplicator::initializeBridge, shared_from_this(), _1, _2)
    );
}

BrokerReplicator::~BrokerReplicator() { }

// This is called in the connection IO thread when the bridge is started.
void BrokerReplicator::initializeBridge(Bridge& bridge, SessionHandler& sessionHandler) {
    // Use the credentials of the outgoing Link connection for creating queues,
    // exchanges etc. We know link->getConnection() is non-zero because we are
    // being called in the connections thread context.
    //
    assert(link->getConnection());
    userId = link->getConnection()->getUserId();
    remoteHost = link->getConnection()->getUrl();

    link->getRemoteAddress(primary);
    string queueName = bridge.getQueueName();

    QPID_LOG(info, logPrefix << (initialized ? "Failing over" : "Connecting")
             << " to primary " << primary
             << " status:" << printable(haBroker.getStatus()));
    initialized = true;

    framing::AMQP_ServerProxy peer(sessionHandler.out);
    const qmf::org::apache::qpid::broker::ArgsLinkBridge& args(bridge.getArgs());

    //declare and bind an event queue
    FieldTable declareArgs;
    declareArgs.setString(QPID_REPLICATE, printable(NONE).str());
    peer.getQueue().declare(queueName, "", false, false, true, true, declareArgs);
    peer.getExchange().bind(queueName, QMF_DEFAULT_TOPIC, AGENT_EVENT_BROKER, FieldTable());
    peer.getExchange().bind(queueName, QMF_DEFAULT_TOPIC, AGENT_EVENT_HA, FieldTable());
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
}

void BrokerReplicator::route(Deliverable& msg) {
    // We transition from JOINING->CATCHUP on the first message received from the primary.
    // Until now we couldn't be sure if we had a good connection to the primary.
    if (haBroker.getStatus() == JOINING) {
        haBroker.setStatus(CATCHUP);
        QPID_LOG(notice, logPrefix << "Connected to primary " << primary);
    }

    const framing::FieldTable* headers = msg.getMessage().getApplicationHeaders();
    const MessageProperties* messageProperties = msg.getMessage().getProperties<MessageProperties>();
    Variant::List list;
    try {
        if (!isQMFv2(msg.getMessage()) || !headers || !messageProperties)
            throw Exception("Unexpected message, not QMF2 event or query response.");
        // decode as list
        string content = msg.getMessage().getFrames().getContent();
        amqp_0_10::ListCodec::decode(content, list);
        QPID_LOG(trace, "Broker replicator received: " << *messageProperties);
        if (headers->getAsString(QMF_CONTENT) == EVENT) {
            for (Variant::List::iterator i = list.begin(); i != list.end(); ++i) {
                Variant::Map& map = i->asMap();
                QPID_LOG(trace, "Broker replicator event: " << map);
                Variant::Map& schema = map[SCHEMA_ID].asMap();
                Variant::Map& values = map[VALUES].asMap();
                if      (match<EventQueueDeclare>(schema)) doEventQueueDeclare(values);
                else if (match<EventQueueDelete>(schema)) doEventQueueDelete(values);
                else if (match<EventExchangeDeclare>(schema)) doEventExchangeDeclare(values);
                else if (match<EventExchangeDelete>(schema)) doEventExchangeDelete(values);
                else if (match<EventBind>(schema)) doEventBind(values);
                else if (match<EventUnbind>(schema)) doEventUnbind(values);
                else if (match<EventMembersUpdate>(schema)) doEventMembersUpdate(values);
            }
        } else if (headers->getAsString(QMF_OPCODE) == QUERY_RESPONSE) {
            for (Variant::List::iterator i = list.begin(); i != list.end(); ++i) {
                Variant::Map& map = i->asMap();
                QPID_LOG(trace, "Broker replicator response: " << map);
                string type = map[SCHEMA_ID].asMap()[CLASS_NAME].asString();
                Variant::Map& values = map[VALUES].asMap();
                framing::FieldTable args;
                amqp_0_10::translate(asMapVoid(values[ARGUMENTS]), args);
                if      (type == QUEUE) doResponseQueue(values);
                else if (type == EXCHANGE) doResponseExchange(values);
                else if (type == BINDING) doResponseBind(values);
                else if (type == HA_BROKER) doResponseHaBroker(values);
            }
            if (messageProperties->getCorrelationId() == EXCHANGE && !headers->isSet(PARTIAL)) {
                // We have received all of the exchange response.
                alternates.clear();
            }
        }
    } catch (const std::exception& e) {
        QPID_LOG(critical, logPrefix << "Configuration failed: " << e.what()
                 << ": while handling: " << list);
        throw;
    }
}

void BrokerReplicator::doEventQueueDeclare(Variant::Map& values) {
    Variant::Map argsMap = asMapVoid(values[ARGS]);
    bool autoDel = values[AUTODEL].asBool();
    bool excl = values[EXCL].asBool();
    if (values[DISP] == CREATED &&
        replicationTest.isReplicated(CONFIGURATION, argsMap, autoDel, excl))
    {
        string name = values[QNAME].asString();
        QPID_LOG(debug, logPrefix << "Queue declare event: " << name);
        framing::FieldTable args;
        amqp_0_10::translate(argsMap, args);
        // If we already have a queue with this name, replace it.
        // The queue was definitely created on the primary.
        if (broker.getQueues().find(name)) {
            QPID_LOG(warning, logPrefix << "Replacing exsiting queue: " << name);
            broker.getQueues().destroy(name);
            stopQueueReplicator(name);
        }
        boost::shared_ptr<Queue> queue = createQueue(
            name, values[DURABLE].asBool(), autoDel, args, values[ALTEX].asString());
        assert(queue);  // Should be created since we destroed the previous queue above.
        if (queue) startQueueReplicator(queue);
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
    if (queue && replicationTest.replicateLevel(queue->getSettings())) {
        QPID_LOG(debug, logPrefix << "Queue delete event: " << name);
        stopQueueReplicator(name);
        broker.deleteQueue(name, userId, remoteHost);
    }
}

void BrokerReplicator::doEventExchangeDeclare(Variant::Map& values) {
    Variant::Map argsMap(asMapVoid(values[ARGS]));
    if (!replicationTest.replicateLevel(argsMap)) return; // Not a replicated exchange.
    if (values[DISP] == CREATED && replicationTest.replicateLevel(argsMap)) {
        string name = values[EXNAME].asString();
        QPID_LOG(debug, logPrefix << "Exchange declare event: " << name);
        framing::FieldTable args;
        amqp_0_10::translate(argsMap, args);
        // If we already have a exchange with this name, replace it.
        // The exchange was definitely created on the primary.
        if (broker.getExchanges().find(name)) {
            broker.getExchanges().destroy(name);
            QPID_LOG(warning, logPrefix << "Replaced exsiting exchange: " << name);
        }
        boost::shared_ptr<Exchange> exchange =
            createExchange(name, values[EXTYPE].asString(), values[DURABLE].asBool(), args, values[ALTEX].asString());
        assert(exchange);
    }
}

void BrokerReplicator::doEventExchangeDelete(Variant::Map& values) {
    string name = values[EXNAME].asString();
    boost::shared_ptr<Exchange> exchange = broker.getExchanges().find(name);
    if (!exchange) {
        QPID_LOG(warning, logPrefix << "Exchange delete event, does not exist: " << name);
    } else if (!replicationTest.replicateLevel(exchange->getArgs())) {
        QPID_LOG(warning, logPrefix << "Exchange delete event, not replicated: " << name);
    } else {
        QPID_LOG(debug, logPrefix << "Exchange delete event:" << name);
        broker.deleteExchange(name, userId, remoteHost);
    }
}

void BrokerReplicator::doEventBind(Variant::Map& values) {
    boost::shared_ptr<Exchange> exchange =
        broker.getExchanges().find(values[EXNAME].asString());
    boost::shared_ptr<Queue> queue =
        broker.getQueues().find(values[QNAME].asString());
    // We only replicate binds for a replicated queue to replicated
    // exchange that both exist locally.
    if (exchange && replicationTest.replicateLevel(exchange->getArgs()) &&
        queue && replicationTest.replicateLevel(queue->getSettings()))
    {
        framing::FieldTable args;
        amqp_0_10::translate(asMapVoid(values[ARGS]), args);
        string key = values[KEY].asString();
        QPID_LOG(debug, logPrefix << "Bind event: exchange=" << exchange->getName()
                 << " queue=" << queue->getName()
                 << " key=" << key);
        exchange->bind(queue, key, &args);
    }
}

void BrokerReplicator::doEventUnbind(Variant::Map& values) {
    boost::shared_ptr<Exchange> exchange =
        broker.getExchanges().find(values[EXNAME].asString());
    boost::shared_ptr<Queue> queue =
        broker.getQueues().find(values[QNAME].asString());
    // We only replicate unbinds for a replicated queue to replicated
    // exchange that both exist locally.
    if (exchange && replicationTest.replicateLevel(exchange->getArgs()) &&
        queue && replicationTest.replicateLevel(queue->getSettings()))
    {
        framing::FieldTable args;
        amqp_0_10::translate(asMapVoid(values[ARGS]), args);
        string key = values[KEY].asString();
        QPID_LOG(debug, logPrefix << "Unbind event: exchange=" << exchange->getName()
                 << " queue=" << queue->getName()
                 << " key=" << key);
        exchange->unbind(queue, key, &args);
    }
}

void BrokerReplicator::doEventMembersUpdate(Variant::Map& values) {
    Variant::List members = values[MEMBERS].asList();
    haBroker.setMembership(members);
}

namespace {

// Get the alternate exchange from the exchange field of a queue or exchange response.
static const string EXCHANGE_KEY_PREFIX("org.apache.qpid.broker:exchange:");

string getAltExchange(const types::Variant& var) {
    if (!var.isVoid()) {
        management::ObjectId oid(var);
        string key = oid.getV2Key();
        if (key.find(EXCHANGE_KEY_PREFIX) != 0) throw Exception("Invalid exchange reference: "+key);
        return key.substr(EXCHANGE_KEY_PREFIX.size());
    }
    else return string();
}
}

void BrokerReplicator::doResponseQueue(Variant::Map& values) {
    Variant::Map argsMap(asMapVoid(values[ARGUMENTS]));
    if (!replicationTest.isReplicated(
            CONFIGURATION,
            values[ARGUMENTS].asMap(),
            values[AUTODELETE].asBool(),
            values[EXCLUSIVE].asBool()))
        return;
    string name(values[NAME].asString());
    QPID_LOG(debug, logPrefix << "Queue response: " << name);
    framing::FieldTable args;
    amqp_0_10::translate(argsMap, args);
    boost::shared_ptr<Queue> queue =
        createQueue(name, values[DURABLE].asBool(), values[AUTODELETE].asBool(), args,
                    getAltExchange(values[ALTEXCHANGE]));
    // It is normal for the queue to already exist if we are failing over.
    if (queue) startQueueReplicator(queue);
    else QPID_LOG(debug, logPrefix << "Queue already replicated: " << name);
}

void BrokerReplicator::doResponseExchange(Variant::Map& values) {
    Variant::Map argsMap(asMapVoid(values[ARGUMENTS]));
    if (!replicationTest.replicateLevel(argsMap)) return;
    string name = values[NAME].asString();
    QPID_LOG(debug, logPrefix << "Exchange response: " << name);
    framing::FieldTable args;
    amqp_0_10::translate(argsMap, args);
    boost::shared_ptr<Exchange> exchange = createExchange(
        name, values[TYPE].asString(), values[DURABLE].asBool(), args,
        getAltExchange(values[ALTEXCHANGE]));
    // It is normal for the exchange to already exist if we are failing over.
    QPID_LOG_IF(debug, !exchange, logPrefix << "Exchange already replicated: " << name);
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
    if (exchange && replicationTest.replicateLevel(exchange->getArgs()) &&
        queue && replicationTest.replicateLevel(queue->getSettings()))
    {
        string key = values[KEY].asString();
        QPID_LOG(debug, logPrefix << "Bind response: exchange:" << exName
                 << " queue:" << qName
                 << " key:" << key);
        framing::FieldTable args;
        amqp_0_10::translate(asMapVoid(values[ARGUMENTS]), args);
        exchange->bind(queue, key, &args);
    }
}

namespace {
const string REPLICATE_DEFAULT="replicateDefault";
}

// Received the ha-broker configuration object for the primary broker.
void BrokerReplicator::doResponseHaBroker(Variant::Map& values) {
    try {
        QPID_LOG(trace, logPrefix << "HA Broker response: " << values);
        ReplicateLevel mine = haBroker.getSettings().replicateDefault.get();
        ReplicateLevel primary = replicationTest.replicateLevel(
            values[REPLICATE_DEFAULT].asString());
        if (mine != primary)
            throw Exception(QPID_MSG("Replicate default on backup (" << mine
                                     << ") does not match primary (" <<  primary << ")"));
        haBroker.setMembership(values[MEMBERS].asList());
    } catch (const std::exception& e) {
        QPID_LOG(critical, logPrefix << "Invalid HA Broker response: " << e.what()
                 << ": " << values);
        haBroker.shutdown();
    }
}

void BrokerReplicator::startQueueReplicator(const boost::shared_ptr<Queue>& queue)
{
    if (replicationTest.replicateLevel(queue->getSettings()) == ALL) {
        boost::shared_ptr<QueueReplicator> qr(
            new QueueReplicator(haBroker.getBrokerInfo(), queue, link));
        if (!broker.getExchanges().registerExchange(qr))
            throw Exception(QPID_MSG("Duplicate queue replicator " << qr->getName()));
        qr->activate();
    }
}

void BrokerReplicator::stopQueueReplicator(const std::string& name) {
    boost::shared_ptr<QueueReplicator> qr = findQueueReplicator(name);
    if (qr) {
        qr->deactivate();
        // QueueReplicator's bridge is now queued for destruction but may not
        // actually be destroyed.
        broker.getExchanges().destroy(qr->getName());
    }
}

boost::shared_ptr<Queue> BrokerReplicator::createQueue(
    const std::string& name,
    bool durable,
    bool autodelete,
    const qpid::framing::FieldTable& arguments,
    const std::string& alternateExchange)
{
    std::pair<boost::shared_ptr<Queue>, bool> result =
        broker.createQueue(
            name,
            durable,
            autodelete,
            0, // no owner regardless of exclusivity on primary
            string(), // Set alternate exchange below
            arguments,
            userId,
            remoteHost);
    if (result.second) {
        if (!alternateExchange.empty()) {
            alternates.setAlternate(
                alternateExchange, boost::bind(&Queue::setAlternateExchange, result.first, _1));
        }
        return result.first;
    }
    else return  boost::shared_ptr<Queue>();
}

boost::shared_ptr<Exchange> BrokerReplicator::createExchange(
    const std::string& name,
    const std::string& type,
    bool durable,
    const qpid::framing::FieldTable& args,
    const std::string& alternateExchange)
{
    std::pair<boost::shared_ptr<Exchange>, bool> result =
        broker.createExchange(
            name,
            type,
            durable,
            string(),  // Set alternate exchange below
            args,
            userId,
            remoteHost);
    if (result.second) {
        alternates.addExchange(result.first);
        if (!alternateExchange.empty()) {
            alternates.setAlternate(
                alternateExchange, boost::bind(&Exchange::setAlternate, result.first, _1));
        }
        return result.first;
    }
    else return  boost::shared_ptr<Exchange>();
}

bool BrokerReplicator::bind(boost::shared_ptr<Queue>, const string&, const framing::FieldTable*) { return false; }
bool BrokerReplicator::unbind(boost::shared_ptr<Queue>, const string&, const framing::FieldTable*) { return false; }
bool BrokerReplicator::isBound(boost::shared_ptr<Queue>, const string* const, const framing::FieldTable* const) { return false; }

string BrokerReplicator::getType() const { return QPID_CONFIGURATION_REPLICATOR; }

}} // namespace broker
