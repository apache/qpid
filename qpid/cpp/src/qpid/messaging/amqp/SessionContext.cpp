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
#include <boost/format.hpp>
#include "qpid/messaging/Address.h"
#include "qpid/messaging/Duration.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/log/Statement.h"
extern "C" {
#include <proton/engine.h>
}

namespace qpid {
namespace messaging {
namespace amqp {

SessionContext::SessionContext(pn_connection_t* connection) : session(pn_session(connection)) {}
SessionContext::~SessionContext()
{
    senders.clear(); receivers.clear();
    pn_session_free(session);
}

boost::shared_ptr<SenderContext> SessionContext::createSender(const qpid::messaging::Address& address, bool setToOnSend)
{
    std::string name = AddressHelper::getLinkName(address);
    if (senders.find(name) != senders.end()) throw LinkError("Link name must be unique within the scope of the connection");
    boost::shared_ptr<SenderContext> s(new SenderContext(session, name, address, setToOnSend));
    senders[name] = s;
    return s;
}

boost::shared_ptr<ReceiverContext> SessionContext::createReceiver(const qpid::messaging::Address& address)
{
    std::string name = AddressHelper::getLinkName(address);
    if (receivers.find(name) != receivers.end()) throw LinkError("Link name must be unique within the scope of the connection");
    boost::shared_ptr<ReceiverContext> r(new ReceiverContext(session, name, address));
    receivers[name] = r;
    return r;
}

boost::shared_ptr<SenderContext> SessionContext::getSender(const std::string& name) const
{
    SenderMap::const_iterator i = senders.find(name);
    if (i == senders.end()) {
        throw qpid::messaging::KeyError(std::string("No such sender") + name);
    } else {
        return i->second;
    }
}

boost::shared_ptr<ReceiverContext> SessionContext::getReceiver(const std::string& name) const
{
    ReceiverMap::const_iterator i = receivers.find(name);
    if (i == receivers.end()) {
        throw qpid::messaging::KeyError(std::string("No such receiver") + name);
    } else {
        return i->second;
    }
}

void SessionContext::removeReceiver(const std::string& n)
{
    receivers.erase(n);
}

void SessionContext::removeSender(const std::string& n)
{
    senders.erase(n);
}

boost::shared_ptr<ReceiverContext> SessionContext::nextReceiver()
{
    for (SessionContext::ReceiverMap::iterator i = receivers.begin(); i != receivers.end(); ++i) {
        if (i->second->hasCurrent()) {
            return i->second;
        }
    }

    return boost::shared_ptr<ReceiverContext>();
}

uint32_t SessionContext::getReceivable()
{
    return 0;//TODO
}

uint32_t SessionContext::getUnsettledAcks()
{
    return 0;//TODO
}

qpid::framing::SequenceNumber SessionContext::record(pn_delivery_t* delivery)
{
    qpid::framing::SequenceNumber id = next++;
    unacked[id] = delivery;
    QPID_LOG(debug, "Recorded delivery " << id << " -> " << delivery);
    return id;
}

void SessionContext::acknowledge(DeliveryMap::iterator begin, DeliveryMap::iterator end)
{
    for (DeliveryMap::iterator i = begin; i != end; ++i) {
        QPID_LOG(debug, "Setting disposition for delivery " << i->first << " -> " << i->second);
        pn_delivery_update(i->second, PN_ACCEPTED);
        pn_delivery_settle(i->second);//TODO: different settlement modes?
    }
    unacked.erase(begin, end);
}

void SessionContext::acknowledge()
{
    QPID_LOG(debug, "acknowledging all " << unacked.size() << " messages");
    acknowledge(unacked.begin(), unacked.end());
}

void SessionContext::acknowledge(const qpid::framing::SequenceNumber& id, bool cumulative)
{
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
    session = pn_session(connection);
    unacked.clear();
    for (SessionContext::SenderMap::iterator i = senders.begin(); i != senders.end(); ++i) {
        i->second->reset(session);
    }
    for (SessionContext::ReceiverMap::iterator i = receivers.begin(); i != receivers.end(); ++i) {
        i->second->reset(session);
    }
}
}}} // namespace qpid::messaging::amqp
