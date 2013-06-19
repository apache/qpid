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
#include "Interconnect.h"
#include "Interconnects.h"
#include "Connection.h"
#include "SaslClient.h"
#include "Session.h"
#include "qpid/broker/Broker.h"
#include "qpid/Exception.h"
#include "qpid/SaslFactory.h"
#include "qpid/sys/ConnectionCodec.h"
#include "qpid/sys/OutputControl.h"
#include "qpid/log/Statement.h"
#include <boost/shared_ptr.hpp>
extern "C" {
#include <proton/engine.h>
#include <proton/error.h>
}

namespace qpid {
namespace broker {
namespace amqp {

Interconnect::Interconnect(qpid::sys::OutputControl& out, const std::string& id, qpid::broker::Broker& broker, bool saslInUse,
                           bool i, const std::string& n, const std::string& s, const std::string& t, Domain& d, Interconnects& r)
    : Connection(out, id, broker, r, true, std::string()), incoming(i), name(n), source(s), target(t), domain(d), registry(r), headerDiscarded(saslInUse),
      closeRequested(false), isTransportDeleted(false)
{}

Interconnect::~Interconnect()
{
    QPID_LOG(notice, "Interconnect deleted");
}

namespace {
const pn_state_t UNINIT = PN_LOCAL_UNINIT | PN_REMOTE_UNINIT;
const size_t PROTOCOL_HEADER_LENGTH(8);
}

size_t Interconnect::encode(char* buffer, size_t size)
{
    if (headerDiscarded) {
        return Connection::encode(buffer, size);
    } else {
        //The IO 'layer' will write in a protocol header when an
        //'outgoing' connection is established. However the proton
        //protocol engine will also emit one. One needs to be
        //discarded, here we discard the one the engine emits for
        //interconnects.
        headerDiscarded = true;
        size_t encoded = Connection::encode(buffer, size);
        assert(encoded >= PROTOCOL_HEADER_LENGTH);//we never encode part of protocol header
        //discard first eight bytes
        ::memmove(buffer, buffer + PROTOCOL_HEADER_LENGTH, encoded - PROTOCOL_HEADER_LENGTH);
        return encoded - PROTOCOL_HEADER_LENGTH;
    }
}

void Interconnect::process()
{
    QPID_LOG(trace, id << " processing interconnect");
    if (closeRequested) {
        close();
    } else {
        if ((pn_connection_state(connection) & UNINIT) == UNINIT) {
            QPID_LOG_CAT(debug, model, id << " interconnect opened");
            pn_connection_set_container(connection, broker.getFederationTag().c_str());
            pn_connection_open(connection);
            out.connectionEstablished();

            pn_session_t* s = pn_session(connection);
            pn_session_open(s);
            boost::shared_ptr<Session> ssn(new Session(s, broker, *this, out));
            sessions[s] = ssn;

            pn_link_t* l = incoming ? pn_receiver(s, name.c_str()) : pn_sender(s, name.c_str());
            pn_link_open(l);
            ssn->attach(l, source, target, relay);
        }
        Connection::process();
    }
}

void Interconnect::setRelay(boost::shared_ptr<Relay> r)
{
    relay = r;
}

void Interconnect::deletedFromRegistry()
{
    closeRequested = true;
    if (!isTransportDeleted) out.activateOutput();
}

void Interconnect::transportDeleted()
{
    isTransportDeleted = true;
    registry.remove(name);
}

}}} // namespace qpid::broker::amqp
