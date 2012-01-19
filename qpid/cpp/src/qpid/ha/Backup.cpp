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
#include "Backup.h"
#include "Settings.h"
#include "qpid/Url.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/broker/Bridge.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/SessionHandler.h"
#include "qpid/framing/AMQP_ServerProxy.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/types/Variant.h"

namespace qpid {
namespace ha {

using namespace framing;
using namespace broker;
using types::Variant;

namespace {
const std::string QPID_WIRING_REPLICATOR("qpid.wiring-replicator");
}

// Initialize a bridge as a wiring replicator.
void bridgeInitWiringReplicator(Bridge& bridge, SessionHandler& sessionHandler) {
    framing::AMQP_ServerProxy peer(sessionHandler.out);
    std::string queueName = bridge.getQueueName();
    const qmf::org::apache::qpid::broker::ArgsLinkBridge& args(bridge.getArgs());

    //declare and bind an event queue
    peer.getQueue().declare(queueName, "", false, false, true, true, FieldTable());
    peer.getExchange().bind(queueName, "qmf.default.topic", "agent.ind.event.org_apache_qpid_broker.#", FieldTable());
    //subscribe to the queue
    peer.getMessage().subscribe(queueName, args.i_dest, 1, 0, false, "", 0, FieldTable());
    peer.getMessage().flow(args.i_dest, 0, 0xFFFFFFFF);
    peer.getMessage().flow(args.i_dest, 1, 0xFFFFFFFF);

    //issue a query request for queues and another for exchanges using event queue as the reply-to address
    for (int i = 0; i < 2; ++i) {//TODO: cleanup this code into reusable utility functions
        Variant::Map request;
        request["_what"] = "OBJECT";
        Variant::Map schema;
        schema["_class_name"] = (i == 0 ? "queue" : "exchange");
        schema["_package_name"] = "org.apache.qpid.broker";
        request["_schema_id"] = schema;

        AMQFrame method((MessageTransferBody(ProtocolVersion(), "qmf.default.direct", 0, 0)));
        method.setBof(true);
        method.setEof(false);
        method.setBos(true);
        method.setEos(true);
        AMQHeaderBody headerBody;
        MessageProperties* props = headerBody.get<MessageProperties>(true);
        props->setReplyTo(qpid::framing::ReplyTo("", queueName));
        props->setAppId("qmf2");
        props->getApplicationHeaders().setString("qmf.opcode", "_query_request");
        headerBody.get<qpid::framing::DeliveryProperties>(true)->setRoutingKey("broker");
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
}

Backup::Backup(broker::Broker& b, const Settings& s) : broker(b), settings(s) {
    // Create a link to replicate wiring
    if (s.brokerUrl != "dummy") {
        Url url(s.brokerUrl);
        QPID_LOG(info, "HA backup broker connecting to: " << url);

        std::string protocol = url[0].protocol.empty() ? "tcp" : url[0].protocol;
        broker.getLinks().declare( // Declare the link
            url[0].host, url[0].port, protocol,
            false,              // durable
            s.mechanism, s.username, s.password);

        broker.getLinks().declare( // Declare the bridge
            url[0].host, url[0].port,
            false,              // durable
            QPID_WIRING_REPLICATOR, // src
            QPID_WIRING_REPLICATOR, // dest
            "x",                // key
            false,              // isQueue
            false,              // isLocal
            "",                 // id/tag
            "",                 // excludes
            false,              // dynamic
            0,                  // sync?
            bridgeInitWiringReplicator
        );
    }
    // FIXME aconway 2011-11-17: need to enhance the link code to
    // handle discovery of the primary broker and fail-over correctly.
}

}} // namespace qpid::ha
