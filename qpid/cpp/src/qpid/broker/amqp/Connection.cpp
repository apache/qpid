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
#include "Connection.h"
#include "Session.h"
#include "qpid/Exception.h"
#include "qpid/broker/Broker.h"
#include "qpid/framing/Buffer.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/OutputControl.h"
#include <sstream>
extern "C" {
#include <proton/engine.h>
#include <proton/error.h>
}

namespace qpid {
namespace broker {
namespace amqp {

Connection::Connection(qpid::sys::OutputControl& o, const std::string& i, qpid::broker::Broker& b, bool saslInUse)
    : ManagedConnection(b, i),
      connection(pn_connection()),
      transport(pn_transport()),
      out(o), id(i), broker(b), haveOutput(true)
{
    if (pn_transport_bind(transport, connection)) {
        //error
    }
    out.activateOutput();
    bool enableTrace(false);
    QPID_LOG_TEST_CAT(trace, protocol, enableTrace);
    if (enableTrace) pn_transport_trace(transport, PN_TRACE_FRM);

    if (!saslInUse) {
        //feed in a dummy AMQP 1.0 header as engine expects one, but
        //we already read it (if sasl is in use we read the sasl
        //header,not the AMQP 1.0 header).
        std::vector<char> protocolHeader(8);
        qpid::framing::ProtocolInitiation pi(getVersion());
        qpid::framing::Buffer buffer(&protocolHeader[0], protocolHeader.size());
        pi.encode(buffer);
        pn_transport_input(transport, &protocolHeader[0], protocolHeader.size());

        //wont get a userid, so set a dummy one on the ManagedConnection to trigger event
        setUserid("no authentication used");
    }
}


Connection::~Connection()
{

    pn_transport_free(transport);
    pn_connection_free(connection);
}

pn_transport_t* Connection::getTransport()
{
    return transport;
}
size_t Connection::decode(const char* buffer, size_t size)
{
    QPID_LOG(trace, id << " decode(" << size << ")")
    //TODO: Fix pn_engine_input() to take const buffer
    ssize_t n = pn_transport_input(transport, const_cast<char*>(buffer), size);
    if (n > 0 || n == PN_EOS) {
        //If engine returns EOS, have no way of knowing how many bytes
        //it processed, but can assume none need to be reprocessed so
        //consider them all read:
        if (n == PN_EOS) n = size;
        QPID_LOG_CAT(debug, network, id << " decoded " << n << " bytes from " << size)
        process();
        pn_transport_tick(transport, 0);
        if (!haveOutput) {
            haveOutput = true;
            out.activateOutput();
        }
        return n;
    } else if (n == PN_ERR) {
        throw qpid::Exception(QPID_MSG("Error on input: " << getError()));
    } else {
        return 0;
    }
}

size_t Connection::encode(char* buffer, size_t size)
{
    QPID_LOG(trace, "encode(" << size << ")")
    ssize_t n = pn_transport_output(transport, buffer, size);
    if (n > 0) {
        QPID_LOG_CAT(debug, network, id << " encoded " << n << " bytes from " << size)
        haveOutput = true;
        return n;
    } else if (n == PN_EOS) {
        haveOutput = size;
        return size;//Is this right?
    } else if (n == PN_ERR) {
        throw qpid::Exception(QPID_MSG("Error on output: " << getError()));
    } else {
        haveOutput = false;
        return 0;
    }
}
bool Connection::canEncode()
{
    for (Sessions::iterator i = sessions.begin();i != sessions.end(); ++i) {
        if (i->second->dispatch()) haveOutput = true;
    }
    process();
    //TODO: proper handling of time in and out of tick
    pn_transport_tick(transport, 0);
    QPID_LOG_CAT(trace, network, id << " canEncode(): " << haveOutput)
    return haveOutput;
}
void Connection::closed()
{
    //TODO: tear down sessions and associated links
    for (Sessions::iterator i = sessions.begin(); i != sessions.end(); ++i) {
        i->second->close();
    }
}
bool Connection::isClosed() const
{
    return pn_connection_state(connection) & PN_REMOTE_CLOSED;
}
framing::ProtocolVersion Connection::getVersion() const
{
    return qpid::framing::ProtocolVersion(1,0);
}
namespace {
pn_state_t REQUIRES_OPEN = PN_LOCAL_UNINIT | PN_REMOTE_ACTIVE;
pn_state_t REQUIRES_CLOSE = PN_LOCAL_ACTIVE | PN_REMOTE_CLOSED;
}

void Connection::process()
{
    QPID_LOG(trace, id << " process()");
    if ((pn_connection_state(connection) & REQUIRES_OPEN) == REQUIRES_OPEN) {
        QPID_LOG_CAT(debug, model, id << " connection opened");
        pn_connection_set_container(connection, broker.getFederationTag().c_str());
        pn_connection_open(connection);
    }

    for (pn_session_t* s = pn_session_head(connection, REQUIRES_OPEN); s; s = pn_session_next(s, REQUIRES_OPEN)) {
        QPID_LOG_CAT(debug, model, id << " session begun");
        pn_session_open(s);
        boost::shared_ptr<Session> ssn(new Session(s, broker, *this, out));
        sessions[s] = ssn;
    }
    for (pn_link_t* l = pn_link_head(connection, REQUIRES_OPEN); l; l = pn_link_next(l, REQUIRES_OPEN)) {
        pn_link_open(l);

        Sessions::iterator session = sessions.find(pn_link_session(l));
        if (session == sessions.end()) {
            QPID_LOG(error, id << " Link attached on unknown session!");
        } else {
            try {
                session->second->attach(l);
                QPID_LOG_CAT(debug, protocol, id << " link " << l << " attached on " << pn_link_session(l));
            } catch (const std::exception& e) {
                QPID_LOG_CAT(error, protocol, "Error on attach: " << e.what());
                //TODO: set error details on detach when that is exposed via engine API
                pn_link_close(l);
            }
        }
    }

    //handle deliveries
    for (pn_delivery_t* delivery = pn_work_head(connection); delivery; delivery = pn_work_next(delivery)) {
        pn_link_t* link = pn_delivery_link(delivery);
        if (pn_link_is_receiver(link)) {
            Sessions::iterator i = sessions.find(pn_link_session(link));
            if (i != sessions.end()) {
                i->second->incoming(link, delivery);
            } else {
                pn_delivery_update(delivery, PN_REJECTED);
            }
        } else { //i.e. SENDER
            Sessions::iterator i = sessions.find(pn_link_session(link));
            if (i != sessions.end()) {
                QPID_LOG(trace, id << " handling outgoing delivery for " << link << " on session " << pn_link_session(link));
                i->second->outgoing(link, delivery);
            } else {
                QPID_LOG(error, id << " Got delivery for non-existent session: " << pn_link_session(link) << ", link: " << link);
            }
        }
    }


    for (pn_link_t* l = pn_link_head(connection, REQUIRES_CLOSE); l; l = pn_link_next(l, REQUIRES_CLOSE)) {
        pn_link_close(l);
        Sessions::iterator session = sessions.find(pn_link_session(l));
        if (session == sessions.end()) {
            QPID_LOG(error, id << " peer attempted to detach link on unknown session!");
        } else {
            session->second->detach(l);
            QPID_LOG_CAT(debug, model, id << " link detached");
        }
    }
    for (pn_session_t* s = pn_session_head(connection, REQUIRES_CLOSE); s; s = pn_session_next(s, REQUIRES_CLOSE)) {
        pn_session_close(s);
        Sessions::iterator i = sessions.find(s);
        if (i != sessions.end()) {
            i->second->close();
            sessions.erase(i);
            QPID_LOG_CAT(debug, model, id << " session ended");
        } else {
            QPID_LOG(error, id << " peer attempted to close unrecognised session");
        }
    }
    if ((pn_connection_state(connection) & REQUIRES_CLOSE) == REQUIRES_CLOSE) {
        QPID_LOG_CAT(debug, model, id << " connection closed");
        pn_connection_close(connection);
    }
}

std::string Connection::getError()
{
    std::stringstream text;
    pn_error_t* cerror = pn_connection_error(connection);
    if (cerror) text << "connection error " << pn_error_text(cerror);
    pn_error_t* terror = pn_transport_error(transport);
    if (terror) text << "transport error " << pn_error_text(terror);
    return text.str();
}

}}} // namespace qpid::broker::amqp
