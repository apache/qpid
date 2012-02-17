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
#include "qmf/org/apache/qpid/broker/EventQueueDeclare.h"
#include "qmf/org/apache/qpid/broker/EventQueueDelete.h"
#include "qmf/org/apache/qpid/broker/EventExchangeDeclare.h"
#include "qmf/org/apache/qpid/broker/EventExchangeDelete.h"

using qmf::org::apache::qpid::broker::EventQueueDeclare;
using qmf::org::apache::qpid::broker::EventQueueDelete;
using qmf::org::apache::qpid::broker::EventExchangeDeclare;
using qmf::org::apache::qpid::broker::EventExchangeDelete;

namespace qpid {
namespace broker {

namespace{
bool isQMFv2(const Message& message)
{
    const qpid::framing::MessageProperties* props = message.getProperties<qpid::framing::MessageProperties>();
    return props && props->getAppId() == "qmf2";
}

template <class T> bool match(qpid::types::Variant::Map& schema)
{
    return T::match(schema["_class_name"], schema["_package_name"]);
}

}

NodeClone::NodeClone(const std::string& name, Broker& b) : Exchange(name), broker(b) {}

NodeClone::~NodeClone() {}

void NodeClone::route(Deliverable& msg, const std::string& /*key*/, const qpid::framing::FieldTable* headers)
{
    if (isQMFv2(msg.getMessage()) && headers) {
        if (headers->getAsString("qmf.content") == "_event") {
            //decode as list
            std::string content = msg.getMessage().getFrames().getContent();
            qpid::types::Variant::List list;
            qpid::amqp_0_10::ListCodec::decode(content, list);
            if (list.empty()) {
                QPID_LOG(error, "Error parsing QMF event, expected non-empty list");
            } else {
                try {
                    qpid::types::Variant::Map& map = list.front().asMap();
                    qpid::types::Variant::Map& schema = map["_schema_id"].asMap();
                    qpid::types::Variant::Map& values = map["_values"].asMap();
                    if (match<EventQueueDeclare>(schema)) {
                        if (values["disp"] == "created" && values["args"].asMap()["qpid.propagate"]) {
                            qpid::framing::FieldTable args;
                            qpid::amqp_0_10::translate(values["args"].asMap(), args);
                            if (!broker.createQueue(
                                    values["qName"].asString(),
                                    values["durable"].asBool(),
                                    values["autoDel"].asBool(),
                                    0 /*i.e. no owner regardless of exclusivity on master*/,
                                    values["altEx"].asString(),
                                    args,
                                    values["user"].asString(),
                                    values["rhost"].asString()).second) {
                                QPID_LOG(warning, "Propagatable queue " << values["qName"] << " already exists");
                            }
                        }
                    } else if (match<EventQueueDelete>(schema)) {
                        std::string name = values["qName"].asString();
                        QPID_LOG(debug, "Notified of deletion of queue " << name);
                        boost::shared_ptr<Queue> queue = broker.getQueues().find(name);
                        if (queue && queue->getSettings().isSet("qpid.propagate")/*TODO: check value*/) {
                            broker.deleteQueue(
                                name,
                                values["user"].asString(),
                                values["rhost"].asString());
                        } else {
                            if (queue) {
                                QPID_LOG(debug, "Ignoring deletion notification for non-propagated queue " << name);
                            } else {
                                QPID_LOG(debug, "No such queue " << name);
                            }
                        }
                    } else if (match<EventExchangeDeclare>(schema)) {
                        if (values["disp"] == "created" && values["args"].asMap()["qpid.propagate"]) {
                            qpid::framing::FieldTable args;
                            qpid::amqp_0_10::translate(values["args"].asMap(), args);
                            if (!broker.createExchange(
                                    values["exName"].asString(),
                                    values["exType"].asString(),
                                    values["durable"].asBool(),
                                    values["altEx"].asString(),
                                    args,
                                    values["user"].asString(),
                                    values["rhost"].asString()).second) {
                                QPID_LOG(warning, "Propagatable queue " << values["qName"] << " already exists");
                            }
                        }
                    } else if (match<EventExchangeDelete>(schema)) {
                        std::string name = values["exName"].asString();
                        QPID_LOG(debug, "Notified of deletion of exchange " << name);
                        try {
                            boost::shared_ptr<Exchange> exchange = broker.getExchanges().get(name);
                            if (exchange && exchange->getArgs().isSet("qpid.propagate")/*TODO: check value*/) {
                                broker.deleteExchange(
                                    name,
                                    values["user"].asString(),
                                    values["rhost"].asString());
                            } else {
                                if (exchange) {
                                    QPID_LOG(debug, "Ignoring deletion notification for non-propagated exchange " << name);
                                } else {
                                    QPID_LOG(debug, "No such exchange " << name);
                                }
                            }
                        } catch (const qpid::framing::NotFoundException&) {}
                    }
                } catch (const std::exception& e) {
                    QPID_LOG(error, "Error propagating configuration: " << e.what());
                }
            }
        } else if (headers->getAsString("qmf.opcode") == "_query_response") {
            //decode as list
            std::string content = msg.getMessage().getFrames().getContent();
            qpid::types::Variant::List list;
            qpid::amqp_0_10::ListCodec::decode(content, list);
            QPID_LOG(debug, "Got query response (" << list.size() << ")");
            for (qpid::types::Variant::List::iterator i = list.begin(); i != list.end(); ++i) {
                std::string type = i->asMap()["_schema_id"].asMap()["_class_name"];
                qpid::types::Variant::Map& values = i->asMap()["_values"].asMap();
                QPID_LOG(debug, "class: " << type << ", values: " << values);
                if (values["arguments"].asMap()["qpid.propagate"]) {
                    qpid::framing::FieldTable args;
                    qpid::amqp_0_10::translate(values["arguments"].asMap(), args);
                    if (type == "queue") {
                        if (!broker.createQueue(
                                values["name"].asString(),
                                values["durable"].asBool(),
                                values["autoDelete"].asBool(),
                                0 /*i.e. no owner regardless of exclusivity on master*/,
                                ""/*TODO: need to include alternate-exchange*/,
                                args,
                                ""/*TODO: who is the user?*/,
                                ""/*TODO: what should we use as connection id?*/).second) {
                            QPID_LOG(warning, "Propagatable queue " << values["name"] << " already exists");
                        }
                    } else if (type == "exchange") {
                        if (!broker.createExchange(
                                values["name"].asString(),
                                values["type"].asString(),
                                values["durable"].asBool(),
                                ""/*TODO: need to include alternate-exchange*/,
                                args,
                                ""/*TODO: who is the user?*/,
                                ""/*TODO: what should we use as connection id?*/).second) {
                            QPID_LOG(warning, "Propagatable queue " << values["qName"] << " already exists");
                        }
                    } else {
                        QPID_LOG(warning, "Ignoring unknow object class: " << type);
                    }
                }
            }
        } else {
            QPID_LOG(debug, "Dropping QMFv2 message with headers: " << *headers);
        }
    } else {
        QPID_LOG(warning, "Ignoring message which is not a valid QMFv2 event or query response");
    }
}

bool NodeClone::isNodeCloneDestination(const std::string& target)
{
    return target == "qpid.node-cloner";
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

bool NodeClone::bind(boost::shared_ptr<Queue>, const std::string&, const qpid::framing::FieldTable*) { return false; }
bool NodeClone::unbind(boost::shared_ptr<Queue>, const std::string&, const qpid::framing::FieldTable*) { return false; }
bool NodeClone::isBound(boost::shared_ptr<Queue>, const std::string* const, const qpid::framing::FieldTable* const) { return false; }

const std::string NodeClone::typeName("node-cloner");

std::string NodeClone::getType() const
{
    return typeName;
}

}} // namespace qpid::broker
