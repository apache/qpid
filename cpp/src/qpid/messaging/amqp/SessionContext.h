#ifndef QPID_MESSAGING_AMQP_SESSIONCONTEXT_H
#define QPID_MESSAGING_AMQP_SESSIONCONTEXT_H

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
#include <map>
#include <string>
#include <boost/shared_ptr.hpp>
#include "qpid/sys/IntegerTypes.h"
#include "qpid/framing/SequenceNumber.h"

struct pn_connection_t;
struct pn_session_t;
struct pn_delivery_t;

namespace qpid {
namespace messaging {

class Address;
class Duration;

namespace amqp {

class ConnectionContext;
class SenderContext;
class ReceiverContext;
/**
 *
 */
class SessionContext
{
  public:
    SessionContext(pn_connection_t*);
    ~SessionContext();
    boost::shared_ptr<SenderContext> createSender(const qpid::messaging::Address& address);
    boost::shared_ptr<ReceiverContext> createReceiver(const qpid::messaging::Address& address);
    boost::shared_ptr<SenderContext> getSender(const std::string& name) const;
    boost::shared_ptr<ReceiverContext> getReceiver(const std::string& name) const;
    void removeReceiver(const std::string&);
    void removeSender(const std::string&);
    boost::shared_ptr<ReceiverContext> nextReceiver(qpid::messaging::Duration timeout);
    uint32_t getReceivable();
    uint32_t getUnsettledAcks();
    bool settled();
    void setName(const std::string&);
    std::string getName() const;
  private:
    friend class ConnectionContext;
    typedef std::map<std::string, boost::shared_ptr<SenderContext> > SenderMap;
    typedef std::map<std::string, boost::shared_ptr<ReceiverContext> > ReceiverMap;
    typedef std::map<qpid::framing::SequenceNumber, pn_delivery_t*> DeliveryMap;
    pn_session_t* session;
    SenderMap senders;
    ReceiverMap receivers;
    DeliveryMap unacked;
    qpid::framing::SequenceNumber next;
    std::string name;

    qpid::framing::SequenceNumber record(pn_delivery_t*);
    void acknowledge();
    void acknowledge(const qpid::framing::SequenceNumber& id, bool cummulative);
    void acknowledge(DeliveryMap::iterator begin, DeliveryMap::iterator end);
};
}}} // namespace qpid::messaging::amqp

#endif  /*!QPID_MESSAGING_AMQP_SESSIONCONTEXT_H*/
