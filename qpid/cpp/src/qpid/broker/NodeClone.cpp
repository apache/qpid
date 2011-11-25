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
#include "NodeClone.h"
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

using qmf::org::apache::qpid::broker::EventBind;
using qmf::org::apache::qpid::broker::EventExchangeDeclare;
using qmf::org::apache::qpid::broker::EventExchangeDelete;
using qmf::org::apache::qpid::broker::EventQueueDeclare;
using qmf::org::apache::qpid::broker::EventQueueDelete;
using qmf::org::apache::qpid::broker::EventSubscribe;

namespace qpid {
namespace broker {

using types::Variant;

namespace{

const std::string QPID_REPLICATE("qpid.replicate");
const std::string ALL("all");
const std::string WIRING("wiring");

const std::string CLASS_NAME("_class_name");
const std::string PACKAGE_NAME("_package_name");
const std::string VALUES("_values");
const std::string EVENT("_event");
const std::string SCHEMA_ID("_schema_id");
const std::string QUERY_RESPONSE("_query_response");

const std::string ARGUMENTS("arguments");
const std::string QUEUE("queue");
const std::string EXCHANGE("exchange");
const std::string BIND("bind");
const std::string ARGS("args");
const std::string DURABLE("durable");
const std::string QNAME("qName");
const std::string AUTODEL("autoDel");
const std::string ALTEX("altEx");
const std::string USER("user");
const std::string RHOST("rhost");
const std::string EXTYPE("exType");
const std::string EXNAME("exName");
const std::string AUTODELETE("autoDelete");
const std::string NAME("name");
const std::string TYPE("type");
const std::string DISP("disp");
const std::string CREATED("created");


const std::string QMF_OPCODE("qmf.opcode");
const std::string QMF_CONTENT("qmf.content");
const std::string QMF2("qmf2");

const std::string QPID_NODE_CLONER("qpid.node-cloner");


bool isQMFv2(const Message& message)
{
    const framing::MessageProperties* props = message.getProperties<framing::MessageProperties>();
    return props && props->getAppId() == QMF2;
}

template <class T> bool match(Variant::Map& schema)
{
    return T::match(schema[CLASS_NAME], schema[PACKAGE_NAME]);
}

bool isReplicated(const std::string& value) {
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


NodeClone::NodeClone(const std::string& name, Broker& b) : Exchange(name), broker(b) {}

NodeClone::~NodeClone() {}

void NodeClone::route(Deliverable& msg, const std::string& /*key*/, const framing::FieldTable* headers) {
    try {
        // FIXME aconway 2011-11-21: outer error handling, e.g. for decoding error.
        if (!isQMFv2(msg.getMessage()) || !headers)
            throw Exception("Unexpected message, not QMF2 event or query response.");
        // FIXME aconway 2011-11-21: string constants
        if (headers->getAsString(QMF_CONTENT) == EVENT) { //decode as list
            std::string content = msg.getMessage().getFrames().getContent();
            Variant::List list;
            amqp_0_10::ListCodec::decode(content, list);
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
                else if (match<EventSubscribe>(schema)) {} // Deliberately ignored.
                else throw(Exception(QPID_MSG("Replicator received unexpected event, schema=" << schema)));
            }
        } else if (headers->getAsString(QMF_OPCODE) == QUERY_RESPONSE) {
            //decode as list
            std::string content = msg.getMessage().getFrames().getContent();
            Variant::List list;
            amqp_0_10::ListCodec::decode(content, list);
            for (Variant::List::iterator i = list.begin(); i != list.end(); ++i) {
                std::string type = i->asMap()[SCHEMA_ID].asMap()[CLASS_NAME];
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

void NodeClone::doEventQueueDeclare(Variant::Map& values) {
    std::string name = values[QNAME].asString();
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

void NodeClone::doEventQueueDelete(Variant::Map& values) {
    std::string name = values[QNAME].asString();
    boost::shared_ptr<Queue> queue = broker.getQueues().find(name);
    if (queue && isReplicated(queue->getSettings())) {
        QPID_LOG(debug, "Deleting replicated queue " << name);
        broker.deleteQueue(
            name,
            values[USER].asString(),
            values[RHOST].asString());
    }
}

void NodeClone::doEventExchangeDeclare(Variant::Map& values) {
    if (values[DISP] == CREATED && isReplicated(values[ARGS].asMap())) {
        std::string name = values[EXNAME].asString();
        framing::FieldTable args;
        amqp_0_10::translate(values[ARGS].asMap(), args);
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

void NodeClone::doEventExchangeDelete(Variant::Map& values) {
    std::string name = values[EXNAME].asString();
    try {
        boost::shared_ptr<Exchange> exchange = broker.getExchanges().get(name);
        if (exchange && isReplicated(exchange->getArgs())) {
            QPID_LOG(warning, "Deleting replicated exchange " << name);
            broker.deleteExchange(
                name,
                values[USER].asString(),
                values[RHOST].asString());
        } 
    } catch (const framing::NotFoundException&) {}
}

void NodeClone::doEventBind(Variant::Map&) {
    QPID_LOG(error, "FIXME NodeClone: Not yet implemented - replicate bindings.");
    // FIXME aconway 2011-11-18: only replicated binds of replicated q to replicated ex.
}

void NodeClone::doResponseQueue(Variant::Map& values) {
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

void NodeClone::doResponseExchange(Variant::Map& values) {
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

void NodeClone::doResponseBind(Variant::Map& ) {
    QPID_LOG(error, "FIXME NodeClone: Not yet implemented - catch-up replicate bindings.");
}

boost::shared_ptr<Exchange> NodeClone::create(const std::string& target, Broker& broker)
{
    boost::shared_ptr<Exchange> exchange;
    if (isNodeCloneDestination(target)) {
        //TODO: need to cache the exchange
        QPID_LOG(info, "Creating node cloner");
        exchange.reset(new NodeClone(target, broker));
    }
    return exchange;
}

bool NodeClone::isNodeCloneDestination(const std::string& target)
{
    return target == QPID_NODE_CLONER;
}

bool NodeClone::bind(boost::shared_ptr<Queue>, const std::string&, const framing::FieldTable*) { return false; }
bool NodeClone::unbind(boost::shared_ptr<Queue>, const std::string&, const framing::FieldTable*) { return false; }
bool NodeClone::isBound(boost::shared_ptr<Queue>, const std::string* const, const framing::FieldTable* const) { return false; }

const std::string NodeClone::typeName(QPID_NODE_CLONER); // FIXME aconway 2011-11-21: qpid.replicator

std::string NodeClone::getType() const
{
    return typeName;
}

}} // namespace broker
