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
#include "qpid/broker/amqp/ManagedConnection.h"
#include <map>
#include <boost/shared_ptr.hpp>

struct pn_connection_t;
struct pn_session_t;
struct pn_transport_t;

namespace qpid {
namespace broker {

class Broker;

namespace amqp {

class Interconnects;
class Session;
/**
 * AMQP 1.0 protocol support for broker
 */
class Connection : public sys::ConnectionCodec, public ManagedConnection
{
  public:
    Connection(qpid::sys::OutputControl& out, const std::string& id, qpid::broker::Broker& broker, Interconnects&, bool saslInUse, const std::string& domain);
    virtual ~Connection();
    size_t decode(const char* buffer, size_t size);
    virtual size_t encode(char* buffer, size_t size);
    bool canEncode();

    void closed();
    bool isClosed() const;

    framing::ProtocolVersion getVersion() const;
    pn_transport_t* getTransport();
    Interconnects& getInterconnects();
    std::string getDomain() const;
    void setUserId(const std::string&);
    void abort();

  protected:
    typedef std::map<pn_session_t*, boost::shared_ptr<Session> > Sessions;
    pn_connection_t* connection;
    pn_transport_t* transport;
    qpid::sys::OutputControl& out;
    const std::string id;
    qpid::broker::Broker& broker;
    bool haveOutput;
    Sessions sessions;
    Interconnects& interconnects;
    std::string domain;

    virtual void process();
    std::string getError();
    void close();
    void open();
    void readPeerProperties();
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP1_CONNECTION_H*/
