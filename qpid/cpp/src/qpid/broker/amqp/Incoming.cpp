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
#include "Incoming.h"
#include "Exception.h"
#include "ManagedConnection.h"
#include "Message.h"
#include "Session.h"
#include "qpid/amqp/descriptors.h"
#include "qpid/broker/AsyncCompletion.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/Broker.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace broker {
namespace amqp {
Incoming::Incoming(pn_link_t* l, Broker& broker, Session& parent, const std::string& source, const std::string& target, const std::string& name)
    : ManagedIncomingLink(broker, parent, source, target, name), credit(500), window(0), link(l), session(parent) {}


Incoming::~Incoming() {}
bool Incoming::doWork()
{
    uint32_t c = getCredit();
    bool issue = window < c;
    if (issue) {
        pn_link_flow(link, c - window);
        window = c;
    }
    return issue;
}
bool Incoming::haveWork()
{
    return window <= (getCredit()/2);
}

uint32_t Incoming::getCredit()
{
    return credit;//TODO: proper flow control
}

void Incoming::detached(bool /*closed*/)
{
}

void Incoming::wakeup()
{
    session.wakeup();
}

void Incoming::verify(const std::string& u, const std::string& r)
{
    userid.init(u, r);
}

Incoming::UserId::UserId() : inDefaultRealm(false) {}
void Incoming::UserId::init(const std::string& u, const std::string& defaultRealm)
{
    userid = u;
    size_t at = userid.find('@');
    if (at != std::string::npos) {
        unqualified = userid.substr(0, at);
        inDefaultRealm = defaultRealm == userid.substr(at+1);
    }
}
void Incoming::UserId::verify(const std::string& claimed)
{
    if(!userid.empty() && !claimed.empty() && userid != claimed && !(inDefaultRealm && claimed == unqualified)) {
        throw Exception(qpid::amqp::error_conditions::NOT_ALLOWED, QPID_MSG("Authenticated user id is " << userid << " but user id in message declared as " << claimed));
    }
}


namespace {
    class Transfer : public qpid::broker::AsyncCompletion::Callback
    {
      public:
        Transfer(pn_delivery_t* d, boost::shared_ptr<Session> s) : delivery(d), session(s) {}
        void completed(bool sync) { session->accepted(delivery, sync); }
        boost::intrusive_ptr<qpid::broker::AsyncCompletion::Callback> clone()
        {
            boost::intrusive_ptr<qpid::broker::AsyncCompletion::Callback> copy(new Transfer(delivery, session));
            return copy;
        }

      private:
        pn_delivery_t* delivery;
        boost::shared_ptr<Session> session;
    };
}

DecodingIncoming::DecodingIncoming(pn_link_t* link, Broker& broker, Session& parent, const std::string& source, const std::string& target, const std::string& name)
    : Incoming(link, broker, parent, source, target, name), sessionPtr(parent.shared_from_this()) {}
DecodingIncoming::~DecodingIncoming() {}

void DecodingIncoming::readable(pn_delivery_t* delivery)
{
    size_t pending = pn_delivery_pending(delivery);
    size_t offset = partial ? partial->getSize() : 0;
    boost::intrusive_ptr<Message> received(new Message(offset + pending));
    if (partial) {
        ::memcpy(received->getData(), partial->getData(), offset);
        partial = boost::intrusive_ptr<Message>();
    }
    assert(received->getSize() == pending + offset);
    pn_link_recv(link, received->getData() + offset, pending);

    if (pn_delivery_partial(delivery)) {
        QPID_LOG(debug, "Message incomplete: received " << pending << " bytes, now have " << received->getSize());
        partial = received;
    } else {
	incomingMessageReceived();
        if (offset) {
            QPID_LOG(debug, "Message complete: received " << pending << " bytes, " << received->getSize() << " in total");
        } else {
            QPID_LOG(debug, "Message received: " << received->getSize() << " bytes");
        }

        received->scan();
        pn_link_advance(link);
        received->setPublisher(&session.getParent());
        received->computeExpiration();
        --window;
        deliver(received, delivery);
    }
}

void DecodingIncoming::deliver(boost::intrusive_ptr<qpid::broker::amqp::Message> received, pn_delivery_t* delivery)
{
    qpid::broker::Message message(received, received);
    userid.verify(message.getUserId());
    received->begin();
    handle(message, session.getTransaction(delivery));
    Transfer t(delivery, sessionPtr);
    sessionPtr->pending_accept(delivery);
    received->end(t);
}
}}} // namespace qpid::broker::amqp
