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
#include "SessionContext.h"
#include "SenderContext.h"
#include "ReceiverContext.h"
#include "Transaction.h"
#include "PnData.h"
#include <boost/format.hpp>
#include "qpid/messaging/Address.h"
#include "qpid/messaging/Duration.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/log/Statement.h"
#include "qpid/amqp/descriptors.h"

extern "C" {
#include <proton/engine.h>
}

namespace qpid {
namespace messaging {
namespace amqp {

SessionContext::SessionContext(pn_connection_t* connection) : session(pn_session(connection)) {}

SessionContext::~SessionContext()
{
    // Clear all pointers to senders and receivers before we free the session.
    senders.clear();
    receivers.clear();
    transaction.reset();        // Transaction is a sender.
    if (!error && session)
        pn_session_free(session);
}

boost::shared_ptr<SenderContext> SessionContext::createSender(const qpid::messaging::Address& address, bool setToOnSend)
{
    error.raise();
    std::string name = AddressHelper::getLinkName(address);
    if (senders.find(name) != senders.end())
        throw LinkError("Link name must be unique within the scope of the connection");
    boost::shared_ptr<SenderContext> s(
        new SenderContext(session, name, address, setToOnSend, transaction));
    senders[name] = s;
    return s;
}

boost::shared_ptr<ReceiverContext> SessionContext::createReceiver(const qpid::messaging::Address& address)
{
    error.raise();
    std::string name = AddressHelper::getLinkName(address);
    if (receivers.find(name) != receivers.end()) throw LinkError("Link name must be unique within the scope of the connection");
    boost::shared_ptr<ReceiverContext> r(new ReceiverContext(session, name, address));
    receivers[name] = r;
    return r;
}

boost::shared_ptr<SenderContext> SessionContext::getSender(const std::string& name) const
{
    error.raise();
    SenderMap::const_iterator i = senders.find(name);
    if (i == senders.end()) {
        throw qpid::messaging::KeyError(std::string("No such sender") + name);
    } else {
        return i->second;
    }
}

boost::shared_ptr<ReceiverContext> SessionContext::getReceiver(const std::string& name) const
{
    error.raise();
    ReceiverMap::const_iterator i = receivers.find(name);
    if (i == receivers.end()) {
        throw qpid::messaging::KeyError(std::string("No such receiver") + name);
    } else {
        return i->second;
    }
}

void SessionContext::removeReceiver(const std::string& n)
{
    error.raise();
    receivers.erase(n);
}

void SessionContext::removeSender(const std::string& n)
{
    error.raise();
    senders.erase(n);
}

boost::shared_ptr<ReceiverContext> SessionContext::nextReceiver()
{
    error.raise();
    for (SessionContext::ReceiverMap::iterator i = receivers.begin(); i != receivers.end(); ++i) {
        if (i->second->hasCurrent()) {
            return i->second;
        }
    }

    return boost::shared_ptr<ReceiverContext>();
}

uint32_t SessionContext::getReceivable()
{
    error.raise();
    return 0;//TODO
}

uint32_t SessionContext::getUnsettledAcks()
{
    error.raise();
    return 0;//TODO
}

qpid::framing::SequenceNumber SessionContext::record(pn_delivery_t* delivery)
{
    error.raise();
    qpid::framing::SequenceNumber id = next++;
    if (!pn_delivery_settled(delivery)) {
        unacked[id] = delivery;
        QPID_LOG(debug, "Recorded delivery " << id << " -> " << delivery);
        pn_link_advance(pn_delivery_link(delivery));
    } else {
        pn_delivery_settle(delivery); // Automatically advances the link.
    }
    return id;
}

void SessionContext::acknowledge(DeliveryMap::iterator begin, DeliveryMap::iterator end)
{
    error.raise();
    for (DeliveryMap::iterator i = begin; i != end; ++i) {
        types::Variant txState;
        if (transaction) {
            QPID_LOG(trace, "Setting disposition for transactional delivery "
                     << i->first << " -> " << i->second);
            transaction->acknowledge(i->second);
        } else {
            QPID_LOG(trace, "Setting disposition for delivery " << i->first << " -> " << i->second);
            pn_delivery_update(i->second, PN_ACCEPTED);
            pn_delivery_settle(i->second); //TODO: different settlement modes?
        }
    }
    unacked.erase(begin, end);
}

void SessionContext::acknowledge()
{
    error.raise();
    QPID_LOG(debug, "acknowledging all " << unacked.size() << " messages");
    acknowledge(unacked.begin(), unacked.end());
}

void SessionContext::acknowledge(const qpid::framing::SequenceNumber& id, bool cumulative)
{
    error.raise();
    QPID_LOG(debug, "acknowledging selected messages, id=" << id << ", cumulative=" << cumulative);
    DeliveryMap::iterator i = unacked.find(id);
    if (i != unacked.end()) {
        DeliveryMap::iterator start = cumulative ? unacked.begin() : i;
        acknowledge(start, ++i);
    } else {
        QPID_LOG(debug, "selective acknowledgement failed; message not found for id " << id);
    }
}

void SessionContext::nack(const qpid::framing::SequenceNumber& id, bool reject)
{
    error.raise();
    DeliveryMap::iterator i = unacked.find(id);
    if (i != unacked.end()) {
        if (reject) {
            QPID_LOG(debug, "rejecting message with id=" << id);
            pn_delivery_update(i->second, PN_REJECTED);
        } else {
            QPID_LOG(debug, "releasing message with id=" << id);
            pn_delivery_update(i->second, PN_MODIFIED);
            pn_disposition_set_failed(pn_delivery_local(i->second), true);
        }
        pn_delivery_settle(i->second);
        unacked.erase(i);
    }
}

bool SessionContext::settled()
{
    error.raise();
    bool result = true;

    for (SenderMap::iterator i = senders.begin(); i != senders.end(); ++i) {
        try {
            if (!i->second->closed() && !i->second->settled()) result = false;
        } catch (const std::exception&) {
            senders.erase(i);
            throw;
        }
    }
    return result;
}

void SessionContext::setName(const std::string& n)
{
    name = n;
}
std::string SessionContext::getName() const
{
    return name;
}

void SessionContext::reset(pn_connection_t* connection)
{
    unacked.clear();
    if (transaction) {
        if (transaction->isCommitting())
            error = new TransactionUnknown("Transaction outcome unknown: transport failure");
        else
            error = new TransactionAborted("Transaction aborted: transport failure");
        resetSession(0);
        senders.clear();
        receivers.clear();
        transaction.reset();
        return;
    }
    resetSession(pn_session(connection));

}

void SessionContext::resetSession(pn_session_t* session_) {
    session = session_;
    if (transaction) transaction->reset(session);
    for (SessionContext::SenderMap::iterator i = senders.begin(); i != senders.end(); ++i) {
        i->second->reset(session);
    }
    for (SessionContext::ReceiverMap::iterator i = receivers.begin(); i != receivers.end(); ++i) {
        i->second->reset(session);
    }
}


}}} // namespace qpid::messaging::amqp
