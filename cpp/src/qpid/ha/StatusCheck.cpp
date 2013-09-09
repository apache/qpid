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
#include "StatusCheck.h"
#include "ConnectionObserver.h"
#include "HaBroker.h"
#include "qpid/broker/Broker.h"
#include "qpid/log/Statement.h"
#include "qpid/messaging/Address.h"
#include "qpid/messaging/Connection.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/Receiver.h"
#include "qpid/messaging/Sender.h"
#include "qpid/messaging/Session.h"
#include "qpid/types/Variant.h"

namespace qpid {
namespace ha {

using namespace qpid::messaging;
using namespace qpid::types;
using namespace std;
using namespace sys;

const string HA_BROKER = "org.apache.qpid.ha:habroker:ha-broker";

class StatusCheckThread : public sys::Runnable {
  public:
    StatusCheckThread(StatusCheck& sc, const qpid::Address& addr)
        : url(addr), statusCheck(sc) {}
    void run();
  private:
    Url url;
    StatusCheck& statusCheck;
};

void StatusCheckThread::run() {
    string logPrefix("Status check " + url.str() + ": ");
    Connection c;
    try {
        // Check for self connections
        Variant::Map options, clientProperties;
        clientProperties[ConnectionObserver::ADMIN_TAG] = 1; // Allow connection to backups.
        clientProperties[ConnectionObserver::ADDRESS_TAG] = url.str();
        clientProperties[ConnectionObserver::BACKUP_TAG] = statusCheck.haBroker.getBrokerInfo().asMap();

        // Set connection options
        Settings settings(statusCheck.haBroker.getSettings());
        if (settings.username.size()) options["username"] = settings.username;
        if (settings.password.size()) options["password"] = settings.password;
        if (settings.mechanism.size()) options["sasl_mechanisms"] = settings.mechanism;
        options["client-properties"] = clientProperties;
        sys::Duration heartbeat(statusCheck.haBroker.getBroker().getOptions().linkHeartbeatInterval);
        options["heartbeat"] = heartbeat/sys::TIME_SEC;
        c = Connection(url.str(), options);

        c.open();
        Session session = c.createSession();
        messaging::Address responses("#;{create:always,node:{x-declare:{exclusive:True,auto-delete:True,arguments:{'qpid.replicate':none}}}}");
        Receiver r = session.createReceiver(responses);
        Sender s = session.createSender("qmf.default.direct/broker");
        Message request;
        request.setReplyTo(responses);
        request.setContentType("amqp/map");
        request.setProperty("x-amqp-0-10.app-id", "qmf2");
        request.setProperty("qmf.opcode", "_query_request");
        Variant::Map oid;
        oid["_object_name"] = HA_BROKER;
        Variant::Map content;
        content["_what"] = "OBJECT";
        content["_object_id"] = oid;
        encode(content, request);
        s.send(request);
        messaging::Duration timeout(heartbeat/sys::TIME_MSEC);
        Message response = r.fetch(timeout);
        session.acknowledge();
        Variant::List contentIn;
        decode(response, contentIn);
        if (contentIn.size() == 1) {
            Variant::Map details = contentIn.front().asMap()["_values"].asMap();
            string status = details["status"].getString();
            QPID_LOG(debug, logPrefix << status);
            if (status != "joining") {
                statusCheck.setPromote(false);
                QPID_LOG(info, logPrefix << "Joining established cluster");
            }
        }
    } catch(const exception& error) {
        // Its not an error to fail to connect to self.
        if (statusCheck.haBroker.getBrokerInfo().getAddress() != url[0])
            QPID_LOG(warning, logPrefix << error.what());
    }
    try { c.close(); } catch(...) {}
    delete this;
}

StatusCheck::StatusCheck(HaBroker& hb) : promote(true), haBroker(hb)
{}

StatusCheck::~StatusCheck() {
    // Join any leftovers
    for (size_t i = 0; i < threads.size(); ++i) threads[i].join();
}

void StatusCheck::setUrl(const Url& url) {
    Mutex::ScopedLock l(lock);
    for (size_t i = 0; i < url.size(); ++i)
        threads.push_back(Thread(new StatusCheckThread(*this, url[i])));
}

bool StatusCheck::canPromote() {
    Mutex::ScopedLock l(lock);
    while (!threads.empty()) {
        Thread t = threads.back();
        threads.pop_back();
        Mutex::ScopedUnlock u(lock);
        t.join();
    }
    return promote;
}

void StatusCheck::setPromote(bool p) {
    Mutex::ScopedLock l(lock);
    promote = p;
}

}} // namespace qpid::ha
