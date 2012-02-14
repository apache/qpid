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
#include "WiringReplicator.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Queue.h"
#include "qpid/log/Statement.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/framing/reply_exceptions.h"
#include "qmf/org/apache/qpid/broker/EventBind.h"
#include "qmf/org/apache/qpid/broker/EventExchangeDeclare.h"
#include "qmf/org/apache/qpid/broker/EventExchangeDelete.h"
#include "qmf/org/apache/qpid/broker/EventQueueDeclare.h"
#include "qmf/org/apache/qpid/broker/EventQueueDelete.h"
#include "qmf/org/apache/qpid/broker/EventSubscribe.h"

namespace qpid {
namespace ha {

using qmf::org::apache::qpid::broker::EventBind;
using qmf::org::apache::qpid::broker::EventExchangeDeclare;
using qmf::org::apache::qpid::broker::EventExchangeDelete;
using qmf::org::apache::qpid::broker::EventQueueDeclare;
using qmf::org::apache::qpid::broker::EventQueueDelete;
using qmf::org::apache::qpid::broker::EventSubscribe;

using std::string;
using types::Variant;
using namespace broker;

namespace{

const string QPID_REPLICATE("qpid.replicate");
const string ALL("all");
const string WIRING("wiring");

const string CLASS_NAME("_class_name");
const string PACKAGE_NAME("_package_name");
const string VALUES("_values");
const string EVENT("_event");
const string SCHEMA_ID("_schema_id");
const string QUERY_RESPONSE("_query_response");

const string ARGUMENTS("arguments");
const string QUEUE("queue");
const string EXCHANGE("exchange");
const string BIND("bind");
const string ARGS("args");
const string DURABLE("durable");
const string QNAME("qName");
const string AUTODEL("autoDel");
const string ALTEX("altEx");
const string USER("user");
const string RHOST("rhost");
const string EXTYPE("exType");
const string EXNAME("exName");
const string AUTODELETE("autoDelete");
const string NAME("name");
const string TYPE("type");
const string DISP("disp");
const string CREATED("created");
const string KEY("key");


const string QMF_OPCODE("qmf.opcode");
const string QMF_CONTENT("qmf.content");
const string QMF2("qmf2");

const string QPID_WIRING_REPLICATOR("qpid.wiring-replicator");


bool isQMFv2(const Message& message)
{
    const framing::MessageProperties* props = message.getProperties<framing::MessageProperties>();
    return props && props->getAppId() == QMF2;
}

template <class T> bool match(Variant::Map& schema)
{
    return T::match(schema[CLASS_NAME], schema[PACKAGE_NAME]);
}

bool isReplicated(const string& value) {
    return value == ALL || value == WIRING;
}
bool isReplicated(const framing::FieldTable& f) {
    return f.isSet(QPID_REPLICATE) && isReplicated(f.getAsString(QPID_REPLICATE));
}
bool isReplicated(const Variant::Map& m) {
    Variant::Map::const_iterator i = m.find(QPID_REPLICATE);
    return i != m.end() && isReplicated(i->second.asString());
}

} // namespace


WiringReplicator::WiringReplicator(const string& name, Broker& b) : Exchange(name), broker(b) {}

WiringReplicator::~WiringReplicator() {}

void WiringReplicator::route(Deliverable& msg, const string& /*key*/, const framing::FieldTable* headers) {
    try {
        // FIXME aconway 2011-11-21: outer error handling, e.g. for decoding error.
        if (!isQMFv2(msg.getMessage()) || !headers)
            throw Exception("Unexpected message, not QMF2 event or query response.");
        // decode as list
        string content = msg.getMessage().getFrames().getContent();
        Variant::List list;
        amqp_0_10::ListCodec::decode(content, list);

        QPID_LOG(critical, "FIXME WiringReplicator message: " << list);
        if (headers->getAsString(QMF_CONTENT) == EVENT) {
            for (Variant::List::iterator i = list.begin(); i != list.end(); ++i) {
                // FIXME aconway 2011-11-18: should be iterating list?
                Variant::Map& map = list.front().asMap();
                Variant::Map& schema = map[SCHEMA_ID].asMap();
                Variant::Map& values = map[VALUES].asMap();
                if      (match<EventQueueDeclare>(schema)) doEventQueueDeclare(values);
                else if (match<EventQueueDelete>(schema)) doEventQueueDelete(values);
                else if (match<EventExchangeDeclare>(schema)) doEventExchangeDeclare(values);
                else if (match<EventExchangeDelete>(schema)) doEventExchangeDelete(values);
                else if (match<EventBind>(schema)) doEventBind(values);
                // FIXME aconway 2011-11-21: handle unbind & all other events.
                else if (match<EventSubscribe>(schema)) {} // Deliberately ignored.
                else throw(Exception(QPID_MSG("WiringReplicator received unexpected event, schema=" << schema)));
            }
        } else if (headers->getAsString(QMF_OPCODE) == QUERY_RESPONSE) {
            QPID_LOG(critical, "FIXME WiringReplicator response: " << list);
            for (Variant::List::iterator i = list.begin(); i != list.end(); ++i) {
                string type = i->asMap()[SCHEMA_ID].asMap()[CLASS_NAME];
                Variant::Map& values = i->asMap()[VALUES].asMap();
                if (isReplicated(values[ARGUMENTS].asMap())) {
                    framing::FieldTable args;
                    amqp_0_10::translate(values[ARGUMENTS].asMap(), args);
                    if      (type == QUEUE) doResponseQueue(values);
                    else if (type == EXCHANGE) doResponseExchange(values);
                    else if (type == BIND) doResponseBind(values);
                    else throw Exception(QPID_MSG("Ignoring unexpected class: " << type));
                }
            }
        } else {
            QPID_LOG(warning, QPID_MSG("Ignoring QMFv2 message with headers: " << *headers));
        }
    } catch (const std::exception& e) {
        QPID_LOG(warning, "Error replicating configuration: " << e.what());
    }
}

void WiringReplicator::doEventQueueDeclare(Variant::Map& values) {
    string name = values[QNAME].asString();
    if (values[DISP] == CREATED && isReplicated(values[ARGS].asMap())) {
        QPID_LOG(debug, "Creating replicated queue " << name);
        framing::FieldTable args;
        amqp_0_10::translate(values[ARGS].asMap(), args);
        if (!broker.createQueue(
                name,
                values[DURABLE].asBool(),
                values[AUTODEL].asBool(),
                0 /*i.e. no owner regardless of exclusivity on master*/,
                values[ALTEX].asString(),
                args,
                values[USER].asString(),
                values[RHOST].asString()).second) {
            QPID_LOG(warning, "Replicated queue " << name << " already exists");
        }
    }
}

void WiringReplicator::doEventQueueDelete(Variant::Map& values) {
    string name = values[QNAME].asString();
    boost::shared_ptr<Queue> queue = broker.getQueues().find(name);
    if (queue && isReplicated(queue->getSettings())) {
        QPID_LOG(debug, "Deleting replicated queue " << name);
        broker.deleteQueue(
            name,
            values[USER].asString(),
            values[RHOST].asString());
    }
}

void WiringReplicator::doEventExchangeDeclare(Variant::Map& values) {
    Variant::Map argsMap(values[ARGS].asMap());
    if (values[DISP] == CREATED && isReplicated(argsMap)) {
        string name = values[EXNAME].asString();
        framing::FieldTable args;
        amqp_0_10::translate(argsMap, args);
        QPID_LOG(debug, "Creating replicated exchange " << name);
        if (!broker.createExchange(
                name,
                values[EXTYPE].asString(),
                values[DURABLE].asBool(),
                values[ALTEX].asString(),
                args,
                values[USER].asString(),
                values[RHOST].asString()).second) {
            QPID_LOG(warning, "Replicated exchange " << name << " already exists");
        }
    }
}

void WiringReplicator::doEventExchangeDelete(Variant::Map& values) {
    string name = values[EXNAME].asString();
    try {
        boost::shared_ptr<Exchange> exchange = broker.getExchanges().get(name);
        if (exchange && isReplicated(exchange->getArgs())) {
            QPID_LOG(debug, "Deleting replicated exchange " << name);
            broker.deleteExchange(
                name,
                values[USER].asString(),
                values[RHOST].asString());
        }
    } catch (const framing::NotFoundException&) {}
}

void WiringReplicator::doEventBind(Variant::Map& values) {
    QPID_LOG(critical, "FIXME doEventBind " << values);
    try {
        boost::shared_ptr<Exchange> exchange = broker.getExchanges().get(values[EXNAME].asString());
        boost::shared_ptr<Queue> queue = broker.getQueues().find(values[QNAME].asString());
        // We only replicated a binds for a replicated queue to replicated exchange.
        if (isReplicated(exchange->getArgs()) && isReplicated(queue->getSettings())) {
            framing::FieldTable args;
            amqp_0_10::translate(args, values[ARGS].asMap());
            string key = values[KEY].asString();
            QPID_LOG(debug, "Replicated binding exchange=" << exchange->getName()
                     << " queue=" << queue->getName()
                     << " key=" << key);
            exchange->bind(queue, key, &args);
        }
    } catch (const framing::NotFoundException&) {} // Ignore unreplicated queue or exchange.
}

void WiringReplicator::doResponseQueue(Variant::Map& values) {
    QPID_LOG(debug, "Creating replicated queue " << values[NAME].asString() << " (in catch-up)");
    if (!broker.createQueue(
            values[NAME].asString(),
            values[DURABLE].asBool(),
            values[AUTODELETE].asBool(),
            0 /*i.e. no owner regardless of exclusivity on master*/,
            ""/*TODO: need to include alternate-exchange*/,
            args,
            ""/*TODO: who is the user?*/,
            ""/*TODO: what should we use as connection id?*/).second) {
        QPID_LOG(warning, "Replicated queue " << values[NAME] << " already exists (in catch-up)");
    }
}

void WiringReplicator::doResponseExchange(Variant::Map& values) {
    QPID_LOG(debug, "Creating replicated exchange " << values[NAME].asString() << " (in catch-up)");
    if (!broker.createExchange(
            values[NAME].asString(),
            values[TYPE].asString(),
            values[DURABLE].asBool(),
            ""/*TODO: need to include alternate-exchange*/,
            args,
            ""/*TODO: who is the user?*/,
            ""/*TODO: what should we use as connection id?*/).second) {
        QPID_LOG(warning, "Replicated exchange " << values[QNAME] << " already exists (in catch-up)");
    }
}

void WiringReplicator::doResponseBind(Variant::Map& values) {
    QPID_LOG(critical, "FIXME doResponseBind " << values);
    throw Exception("FIXME WiringReplicator: Not yet implemented - catch-up replicate bindings.");
}

boost::shared_ptr<Exchange> WiringReplicator::create(const string& target, Broker& broker)
{
    boost::shared_ptr<Exchange> exchange;
    if (isWiringReplicatorDestination(target)) {
        //TODO: need to cache the exchange
        exchange.reset(new WiringReplicator(target, broker));
    }
    return exchange;
}

bool WiringReplicator::isWiringReplicatorDestination(const string& target)
{
    return target == QPID_WIRING_REPLICATOR;
}

bool WiringReplicator::bind(boost::shared_ptr<Queue>, const string&, const framing::FieldTable*) { return false; }
bool WiringReplicator::unbind(boost::shared_ptr<Queue>, const string&, const framing::FieldTable*) { return false; }
bool WiringReplicator::isBound(boost::shared_ptr<Queue>, const string* const, const framing::FieldTable* const) { return false; }

const string WiringReplicator::typeName(QPID_WIRING_REPLICATOR);

string WiringReplicator::getType() const
{
    return typeName;
}

}} // namespace broker
