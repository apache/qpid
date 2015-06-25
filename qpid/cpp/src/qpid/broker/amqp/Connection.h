#ifndef QPID_BROKER_AMQP1_CONNECTION_H
#define QPID_BROKER_AMQP1_CONNECTION_H

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
#include "qpid/sys/ConnectionCodec.h"
#include "qpid/broker/amqp/BrokerContext.h"
#include "qpid/broker/amqp/ManagedConnection.h"
#include "qpid/sys/AtomicValue.h"
#include <map>
#include <boost/intrusive_ptr.hpp>
#include <boost/shared_ptr.hpp>

struct pn_connection_t;
struct pn_session_t;
struct pn_transport_t;
struct pn_collector_t;
struct pn_link_t;
struct pn_delivery_t;

namespace qpid {
namespace sys {
class TimerTask;
}
namespace broker {

class Broker;

namespace amqp {

class Interconnects;
class Session;
/**
 * AMQP 1.0 protocol support for broker
 */
class Connection : public BrokerContext, public sys::ConnectionCodec, public ManagedConnection
{
  public:
    Connection(qpid::sys::OutputControl& out, const std::string& id, BrokerContext& context, bool saslInUse, bool brokerInitiated);
    virtual ~Connection();
    size_t decode(const char* buffer, size_t size);
    virtual size_t encode(char* buffer, size_t size);
    bool canEncode();

    void closed();
    bool isClosed() const;

    framing::ProtocolVersion getVersion() const;
    pn_transport_t* getTransport();
    void setUserId(const std::string&);
    void abort();
    void trace(const char*) const;
    void requestIO();
  protected:
    typedef std::map<pn_session_t*, boost::shared_ptr<Session> > Sessions;
    pn_connection_t* connection;
    pn_transport_t* transport;
    pn_collector_t* collector;
    qpid::sys::OutputControl& out;
    const std::string id;
    bool haveOutput;
    Sessions sessions;
    bool closeInitiated;
    bool closeRequested;
    boost::intrusive_ptr<sys::TimerTask> ticker;
    qpid::sys::AtomicValue<bool> ioRequested;

    virtual void process();
    void doOutput(size_t);
    bool dispatch();
    void processDeliveries();
    std::string getError();
    void close();
    void open();
    void readPeerProperties();
    void closedByManagement();

 private:
    bool checkTransportError(std::string&);

    // handle Proton engine events
    void doConnectionRemoteOpen();
    void doConnectionRemoteClose();
    void doSessionRemoteOpen(pn_session_t *session);
    void doSessionRemoteClose(pn_session_t *session);
    void doLinkRemoteOpen(pn_link_t *link);
    void doLinkRemoteClose(pn_link_t *link);
    void doLinkRemoteDetach(pn_link_t *link, bool closed);
    void doDeliveryUpdated(pn_delivery_t *delivery);
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP1_CONNECTION_H*/
