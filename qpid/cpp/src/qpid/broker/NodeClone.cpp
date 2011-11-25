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
bool isQMFv2(const Message& message)
{
    const framing::MessageProperties* props = message.getProperties<framing::MessageProperties>();
    return props && props->getAppId() == "qmf2";
}

template <class T> bool match(Variant::Map& schema)
{
    return T::match(schema["_class_name"], schema["_package_name"]);
}

const std::string QPID_REPLICATE("qpid.replicate");
const std::string ALL("all");
const std::string WIRING("wiring");

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
    // FIXME aconway 2011-11-21: outer error handling, e.g. for decoding error.
    if (isQMFv2(msg.getMessage()) && headers) {
        // FIXME aconway 2011-11-21: string constants
        if (headers->getAsString("qmf.content") == "_event") { //decode as list
            std::string content = msg.getMessage().getFrames().getContent();
            Variant::List list;
            amqp_0_10::ListCodec::decode(content, list);
            if (list.empty()) { // FIXME aconway 2011-11-21: remove
                QPID_LOG(error, "Error parsing QMF event, empty list");
            } else {
                try {
                    // FIXME aconway 2011-11-18: should be iterating list?
                    Variant::Map& map = list.front().asMap();
                    Variant::Map& schema = map[
                        "_schema_id"].asMap();
                    Variant::Map& values = map["_values"].asMap();
                    if      (match<EventQueueDeclare>(schema)) doEventQueueDeclare(values);
                    else if (match<EventQueueDelete>(schema)) doEventQueueDelete(values);
                    else if (match<EventExchangeDeclare>(schema)) doEventExchangeDeclare(values);
                    else if (match<EventExchangeDelete>(schema)) doEventExchangeDelete(values);
                    else if (match<EventBind>(schema)) doEventBind(values);
                    else if (match<EventSubscribe>(schema)) {} // Deliberately ignored.
                    else QPID_LOG(warning, "Replicator received unexpected event, schema=" << schema);
                } catch (const std::exception& e) {
                    QPID_LOG(error, "Error replicating configuration: " << e.what());
                }
            }
        } else if (headers->getAsString("qmf.opcode") == "_query_response") {
            //decode as list
            std::string content = msg.getMessage().getFrames().getContent();
            Variant::List list;
            amqp_0_10::ListCodec::decode(content, list);
            for (Variant::List::iterator i = list.begin(); i != list.end(); ++i) {
                std::string type = i->asMap()["_schema_id"].asMap()["_class_name"];
                Variant::Map& values = i->asMap()["_values"].asMap();
                if (isReplicated(values["arguments"].asMap())) {
                    framing::FieldTable args;
                    amqp_0_10::translate(values["arguments"].asMap(), args);
                    if      (type == "queue") doResponseQueue(values);
                    else if (type == "exchange") doResponseExchange(values);
                    else if (type == "bind") doResponseBind(values);
                    else {
                        QPID_LOG(warning, "Replicator ignoring unexpected class: " << type);
                    }
                }
            }
        } else {
            QPID_LOG(warning, "Replicator ignoring QMFv2 message with headers: " << *headers);
        }
    } else {
        QPID_LOG(warning, "Replicator ignoring message which is not a QMFv2 event or query response");
    }
}

void NodeClone::doEventQueueDeclare(Variant::Map& values) {
    std::string name = values["qName"].asString();
    if (values["disp"] == "created" && isReplicated(values["args"].asMap())) {
        QPID_LOG(debug, "Creating replicated queue " << name);
        framing::FieldTable args;
        amqp_0_10::translate(values["args"].asMap(), args);
        if (!broker.createQueue(
                name,
                values["durable"].asBool(),
                values["autoDel"].asBool(),
                0 /*i.e. no owner regardless of exclusivity on master*/,
                values["altEx"].asString(),
                args,
                values["user"].asString(),
                values["rhost"].asString()).second) {
            QPID_LOG(warning, "Replicated queue " << name << " already exists");
        }
    }
}

void NodeClone::doEventQueueDelete(Variant::Map& values) {
    std::string name = values["qName"].asString();
    boost::shared_ptr<Queue> queue = broker.getQueues().find(name);
    if (queue && isReplicated(queue->getSettings())) {
        QPID_LOG(debug, "Deleting replicated queue " << name);
        broker.deleteQueue(
            name,
            values["user"].asString(),
            values["rhost"].asString());
    }
}

void NodeClone::doEventExchangeDeclare(Variant::Map& values) {
    if (values["disp"] == "created" && isReplicated(values["args"].asMap())) {
        std::string name = values["exName"].asString();
        framing::FieldTable args;
        amqp_0_10::translate(values["args"].asMap(), args);
        QPID_LOG(debug, "Creating replicated exchange " << name);
        if (!broker.createExchange(
                name,
                values["exType"].asString(),
                values["durable"].asBool(),
                values["altEx"].asString(),
                args,
                values["user"].asString(),
                values["rhost"].asString()).second) {
            QPID_LOG(warning, "Replicated exchange " << name << " already exists");
        }
    }
}

void NodeClone::doEventExchangeDelete(Variant::Map& values) {
    std::string name = values["exName"].asString();
    try {
        boost::shared_ptr<Exchange> exchange = broker.getExchanges().get(name);
        if (exchange && isReplicated(exchange->getArgs())) {
            QPID_LOG(warning, "Deleting replicated exchange " << name);
            broker.deleteExchange(
                name,
                values["user"].asString(),
                values["rhost"].asString());
        } 
    } catch (const framing::NotFoundException&) {}
}

void NodeClone::doEventBind(Variant::Map&) {
    QPID_LOG(error, "FIXME NodeClone: Not yet implemented - replicate bindings.");
    // FIXME aconway 2011-11-18: only replicated binds of replicated q to replicated ex.
}

void NodeClone::doResponseQueue(Variant::Map& values) {
    QPID_LOG(debug, "Creating replicated queue " << values["name"].asString() << " (in catch-up)");
    if (!broker.createQueue(
            values["name"].asString(),
            values["durable"].asBool(),
            values["autoDelete"].asBool(),
            0 /*i.e. no owner regardless of exclusivity on master*/,
            ""/*TODO: need to include alternate-exchange*/,
            args,
            ""/*TODO: who is the user?*/,
            ""/*TODO: what should we use as connection id?*/).second) {
        QPID_LOG(warning, "Replicated queue " << values["name"] << " already exists (in catch-up)");
    }
}

void NodeClone::doResponseExchange(Variant::Map& values) {
    QPID_LOG(debug, "Creating replicated exchange " << values["name"].asString() << " (in catch-up)");
    if (!broker.createExchange(
            values["name"].asString(),
            values["type"].asString(),
            values["durable"].asBool(),
            ""/*TODO: need to include alternate-exchange*/,
            args,
            ""/*TODO: who is the user?*/,
            ""/*TODO: what should we use as connection id?*/).second) {
        QPID_LOG(warning, "Replicated exchange " << values["qName"] << " already exists (in catch-up)");
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
    return target == "qpid.node-cloner";
}

bool NodeClone::bind(boost::shared_ptr<Queue>, const std::string&, const framing::FieldTable*) { return false; }
bool NodeClone::unbind(boost::shared_ptr<Queue>, const std::string&, const framing::FieldTable*) { return false; }
bool NodeClone::isBound(boost::shared_ptr<Queue>, const std::string* const, const framing::FieldTable* const) { return false; }

const std::string NodeClone::typeName("node-cloner"); // FIXME aconway 2011-11-21: qpid.replicator

std::string NodeClone::getType() const
{
    return typeName;
}

}} // namespace broker
