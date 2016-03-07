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
#include "DataReader.h"
#include "Session.h"
#include "Exception.h"
#include "qpid/broker/AclModule.h"
#include "qpid/broker/Broker.h"
#include "qpid/amqp/descriptors.h"
#include "qpid/framing/Buffer.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Time.h"
#include "qpid/sys/Timer.h"
#include "qpid/sys/OutputControl.h"
#include "qpid/Version.h"
#include "config.h"
#include <sstream>
extern "C" {
#include <proton/engine.h>
#include <proton/error.h>
#ifdef HAVE_PROTON_EVENTS
#include <proton/event.h>
#endif
}

namespace qpid {
namespace broker {
namespace amqp {
namespace {

void do_trace(pn_transport_t* transport, const char* message)
{
    Connection* c = reinterpret_cast<Connection*>(pn_transport_get_context(transport));
    if (c) c->trace(message);
}

void set_tracer(pn_transport_t* transport, void* context)
{
    pn_transport_set_context(transport, context);
    pn_transport_set_tracer(transport, &do_trace);
}

#ifdef USE_PROTON_TRANSPORT_CONDITION
std::string get_error(pn_connection_t* connection, pn_transport_t* transport)
{
    std::stringstream text;
    pn_error_t* cerror = pn_connection_error(connection);
    if (cerror) text << "connection error " << pn_error_text(cerror) << " [" << cerror << "]";
    pn_condition_t* tcondition = pn_transport_condition(transport);
    if (pn_condition_is_set(tcondition)) text << "transport error: " << pn_condition_get_name(tcondition) << ", " << pn_condition_get_description(tcondition);
    return text.str();
}
#else
std::string get_error(pn_connection_t* connection, pn_transport_t* transport)
{
    std::stringstream text;
    pn_error_t* cerror = pn_connection_error(connection);
    if (cerror) text << "connection error " << pn_error_text(cerror) << " [" << cerror << "]";
    pn_error_t* terror = pn_transport_error(transport);
    if (terror) text << "transport error " << pn_error_text(terror) << " [" << terror << "]";
    return text.str();
}
#endif

}

void Connection::trace(const char* message) const
{
    QPID_LOG_CAT(trace, protocol, "[" << id << "]: " << message);
}

namespace {
struct ConnectionTickerTask : public qpid::sys::TimerTask
{
    qpid::sys::Timer& timer;
    Connection& connection;
    ConnectionTickerTask(uint64_t interval, qpid::sys::Timer& t, Connection& c) :
        TimerTask(qpid::sys::Duration(interval*qpid::sys::TIME_MSEC), "ConnectionTicker"),
        timer(t),
        connection(c)
    {}

    void fire() {
        // Setup next firing
        setupNextFire();
        timer.add(this);

        // Send Ticker
        connection.requestIO();
    }
};
const std::string ANONYMOUS_RELAY("ANONYMOUS-RELAY");
}

Connection::Connection(qpid::sys::OutputControl& o, const std::string& i, BrokerContext& b, bool saslInUse, bool brokerInitiated)
    : BrokerContext(b), ManagedConnection(getBroker(), i, brokerInitiated),
      connection(pn_connection()),
      transport(pn_transport()),
      collector(0),
      out(o), id(i), haveOutput(true), closeInitiated(false), closeRequested(false), ioRequested(false)
{
#ifdef HAVE_PROTON_EVENTS
    collector = pn_collector();
    pn_connection_collect(connection, collector);
#endif

    if (pn_transport_bind(transport, connection)) {
        //error
        QPID_LOG(error, "Failed to bind transport to connection: " << getError());
    }
    out.activateOutput();
    bool enableTrace(false);
    QPID_LOG_TEST_CAT(trace, protocol, enableTrace);
    if (enableTrace) {
        pn_transport_trace(transport, PN_TRACE_FRM);
        set_tracer(transport, this);
    }

    getBroker().getConnectionObservers().connection(*this);
    if (!saslInUse) {
        //feed in a dummy AMQP 1.0 header as engine expects one, but
        //we already read it (if sasl is in use we read the sasl
        //header,not the AMQP 1.0 header).
        std::vector<char> protocolHeader(8);
        qpid::framing::ProtocolInitiation pi(getVersion());
        qpid::framing::Buffer buffer(&protocolHeader[0], protocolHeader.size());
        pi.encode(buffer);
        pn_transport_input(transport, &protocolHeader[0], protocolHeader.size());

        setUserId("none");
    }
}

void Connection::requestIO()
{
    ioRequested = true;
    out.activateOutput();
}

Connection::~Connection()
{
    if (ticker) ticker->cancel();
    getBroker().getConnectionObservers().closed(*this);
    pn_connection_free(connection);
    pn_transport_free(transport);
#ifdef HAVE_PROTON_EVENTS
    pn_collector_free(collector);
#endif
}

pn_transport_t* Connection::getTransport()
{
    return transport;
}
size_t Connection::decode(const char* buffer, size_t size)
{
    QPID_LOG(trace, id << " decode(" << size << ")");
    if (size == 0) return 0;

    ssize_t n = pn_transport_input(transport, const_cast<char*>(buffer), size);
    if (n > 0 || n == PN_EOS) {
        // PN_EOS either means we received a Close (which also means we've
        // consumed all the input), OR some Very Bad Thing happened and this
        // connection is toast.
        if (n == PN_EOS)
        {
            std::string error;
            if (checkTransportError(error)) {
                // "He's dead, Jim."
                QPID_LOG_CAT(error, network, id << " connection failed: " << error);
                out.abort();
                return 0;
            } else {
                n = size;   // assume all consumed
            }
        }
        QPID_LOG_CAT(debug, network, id << " decoded " << n << " bytes from " << size);
        try {
            process();
        } catch (const Exception& e) {
            QPID_LOG(error, id << ": " << e.what());
            pn_condition_t* error = pn_connection_condition(connection);
            pn_condition_set_name(error, e.symbol());
            pn_condition_set_description(error, e.what());
            close();
        } catch (const std::exception& e) {
            QPID_LOG(error, id << ": " << e.what());
            pn_condition_t* error = pn_connection_condition(connection);
            pn_condition_set_name(error, qpid::amqp::error_conditions::INTERNAL_ERROR.c_str());
            pn_condition_set_description(error, e.what());
            close();
        }
        // QPID-6698: don't use wallclock here, use monotonic clock
        int64_t now = qpid::sys::Duration(qpid::sys::ZERO, qpid::sys::AbsTime::now());
        pn_transport_tick(transport, now / int64_t(qpid::sys::TIME_MSEC));
        if (!haveOutput) {
            haveOutput = true;
            out.activateOutput();
        }
        return n;
    } else if (n == PN_ERR) {
        std::string error;
        checkTransportError(error);
        QPID_LOG_CAT(error, network, id << " connection error: " << error);
        out.abort();
        return 0;
    } else {
        return 0;
    }
}

size_t Connection::encode(char* buffer, size_t size)
{
    QPID_LOG(trace, "encode(" << size << ")");
    doOutput(size);
    ssize_t n = pn_transport_output(transport, buffer, size);
    if (n > 0) {
        QPID_LOG_CAT(debug, network, id << " encoded " << n << " bytes from " << size)
        haveOutput = true;
        if (ticker) ticker->restart();
        return n;
    } else if (n == PN_EOS) {
        haveOutput = false;
        // Normal close, or error?
        std::string error;
        if (checkTransportError(error)) {
            QPID_LOG_CAT(error, network, id << " connection failed: " << error);
            out.abort();
        }
        return 0;
    } else if (n == PN_ERR) {
        std::string error;
        checkTransportError(error);
        QPID_LOG_CAT(error, network, id << " connection error: " << error);
        out.abort();
        return 0;
    } else {
        haveOutput = false;
        return 0;
    }
}

void Connection::doOutput(size_t capacity)
{
    ssize_t n = 0;
    do {
        if (dispatch()) {
            processDeliveries();
            ssize_t next = pn_transport_pending(transport);
            if (n == next) break;
            n = next;
        } else break;
    } while (n > 0 && n < (ssize_t) capacity);
}

bool Connection::dispatch()
{
    bool result = false;
    for (Sessions::iterator i = sessions.begin();i != sessions.end();) {
        if (i->second->endedByManagement()) {
            pn_session_close(i->first);
            i->second->close();
            sessions.erase(i++);
            result = true;
            QPID_LOG_CAT(debug, model, id << " session ended by management");
        } else {
            if (i->second->dispatch()) result = true;
            ++i;
        }
    }
    return result;
}

bool Connection::canEncode()
{
    if (!closeInitiated) {
        if (closeRequested) {
            close();
            return true;
        }
        try {
            if (dispatch()) haveOutput = true;
            process();
        } catch (const Exception& e) {
            QPID_LOG(error, id << ": " << e.what());
            pn_condition_t* error = pn_connection_condition(connection);
            pn_condition_set_name(error, e.symbol());
            pn_condition_set_description(error, e.what());
            close();
            haveOutput = true;
        } catch (const std::exception& e) {
            QPID_LOG(error, id << ": " << e.what());
            pn_condition_t* error = pn_connection_condition(connection);
            pn_condition_set_name(error, qpid::amqp::error_conditions::INTERNAL_ERROR.c_str());
            pn_condition_set_description(error, e.what());
            close();
            haveOutput = true;
        }
    } else {
        QPID_LOG(info, "Connection " << id << " has been closed locally");
    }
    if (ioRequested.valueCompareAndSwap(true, false)) haveOutput = true;
    // QPID-6698: don't use wallclock here, use monotonic clock
    int64_t now = qpid::sys::Duration(qpid::sys::ZERO, qpid::sys::AbsTime::now());
    pn_transport_tick(transport, (now / int64_t(qpid::sys::TIME_MSEC)));
    QPID_LOG_CAT(trace, network, id << " canEncode(): " << haveOutput)
    return haveOutput;
}

void Connection::open()
{
    readPeerProperties();

    pn_connection_set_container(connection, getBroker().getFederationTag().c_str());
    uint32_t timeout = pn_transport_get_remote_idle_timeout(transport);
    if (timeout) {
        // if idle generate empty frames at 1/2 the timeout interval as keepalives:
        ticker = boost::intrusive_ptr<qpid::sys::TimerTask>(new ConnectionTickerTask((timeout/2)+1,
                                                                                     getBroker().getTimer(),
                                                                                     *this));
        getBroker().getTimer().add(ticker);

        // Note: in version 0-10 of the protocol, idle timeout applies to both
        // ends.  AMQP 1.0 changes that - it's now asymmetric: each end can
        // configure/disable it independently.  For backward compatibility, by
        // default mimic the old behavior and set our local timeout.
        // Use 2x the remote's timeout, as per the spec the remote should
        // advertise 1/2 its actual timeout threshold
        pn_transport_set_idle_timeout(transport, timeout * 2);
        QPID_LOG_CAT(debug, network, id << " AMQP 1.0 idle-timeout set:"
                     << " local=" << pn_transport_get_idle_timeout(transport)
                     << " remote=" << pn_transport_get_remote_idle_timeout(transport));
    }

    pn_data_t* offered_capabilities = pn_connection_offered_capabilities(connection);
    if (offered_capabilities) {
        pn_data_put_array(offered_capabilities, false, PN_SYMBOL);
        pn_data_enter(offered_capabilities);
        pn_data_put_symbol(offered_capabilities, pn_bytes(ANONYMOUS_RELAY.size(), ANONYMOUS_RELAY.c_str()));
        pn_data_exit(offered_capabilities);
        pn_data_rewind(offered_capabilities);
    }

    // QPID-6592: put self-identifying information into the connection
    // properties.  Use keys defined by the 0-10 spec, as AMQP 1.0 has yet to
    // define any.
    //
    pn_data_t *props = pn_connection_properties(connection);
    if (props) {
        boost::shared_ptr<const System> sysInfo = getBroker().getSystem();

        pn_data_clear(props);
        pn_data_put_map(props);
        pn_data_enter(props);
        pn_data_put_symbol(props, pn_bytes(7, "product"));
        pn_data_put_string(props, pn_bytes(qpid::product.size(), qpid::product.c_str()));
        pn_data_put_symbol(props, pn_bytes(7, "version"));
        pn_data_put_string(props, pn_bytes(qpid::version.size(), qpid::version.c_str()));
        if (sysInfo) {
            std::string osName(sysInfo->getOsName());
            std::string nodeName(sysInfo->getNodeName());
            pn_data_put_symbol(props, pn_bytes(8, "platform"));
            pn_data_put_string(props, pn_bytes(osName.size(), osName.c_str()));
            pn_data_put_symbol(props, pn_bytes(4, "host"));
            pn_data_put_string(props, pn_bytes(nodeName.size(), nodeName.c_str()));
        }
        pn_data_exit(props);
        pn_data_rewind(props);
    }

    pn_connection_open(connection);
    out.connectionEstablished();
    opened();
    getBroker().getConnectionObservers().opened(*this);
}

void Connection::readPeerProperties()
{
    qpid::types::Variant::Map properties;
    DataReader::read(pn_connection_remote_properties(connection), properties);
    setPeerProperties(properties);
}

void Connection::closed()
{
    if (ticker) ticker->cancel();
    for (Sessions::iterator i = sessions.begin(); i != sessions.end(); ++i) {
        i->second->close();
    }
}
void Connection::close()
{
    if (!closeInitiated) {
        closeInitiated = true;
        closed();
        QPID_LOG_CAT(debug, model, id << " connection closed");
        pn_connection_close(connection);
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

void Connection::process()
{
    QPID_LOG(trace, id << " process()");
#ifdef HAVE_PROTON_EVENTS
    pn_event_t *event = pn_collector_peek(collector);
    while (event) {
        switch (pn_event_type(event)) {
        case PN_CONNECTION_REMOTE_OPEN:
            doConnectionRemoteOpen();
            break;
        case PN_CONNECTION_REMOTE_CLOSE:
            doConnectionRemoteClose();
            break;
        case PN_SESSION_REMOTE_OPEN:
            doSessionRemoteOpen(pn_event_session(event));
            break;
        case PN_SESSION_REMOTE_CLOSE:
            doSessionRemoteClose(pn_event_session(event));
            break;
        case PN_LINK_REMOTE_OPEN:
            doLinkRemoteOpen(pn_event_link(event));
            break;
        case PN_LINK_REMOTE_DETACH:
             doLinkRemoteDetach(pn_event_link(event), false);
             break;
        case PN_LINK_REMOTE_CLOSE:
            doLinkRemoteClose(pn_event_link(event));
            break;
        case PN_DELIVERY:
            doDeliveryUpdated(pn_event_delivery(event));
            break;
        default:
            break;
        }
        pn_collector_pop(collector);
        event = pn_collector_peek(collector);
    }

#else   // !HAVE_PROTON_EVENTS

    const pn_state_t REQUIRES_OPEN = PN_LOCAL_UNINIT | PN_REMOTE_ACTIVE;
    const pn_state_t REQUIRES_CLOSE = PN_LOCAL_ACTIVE | PN_REMOTE_CLOSED;

    if ((pn_connection_state(connection) & REQUIRES_OPEN) == REQUIRES_OPEN) {
        doConnectionRemoteOpen();
    }

    for (pn_session_t* s = pn_session_head(connection, REQUIRES_OPEN); s; s = pn_session_next(s, REQUIRES_OPEN)) {
        doSessionRemoteOpen(s);
    }
    for (pn_link_t* l = pn_link_head(connection, REQUIRES_OPEN); l; l = pn_link_next(l, REQUIRES_OPEN)) {
        doLinkRemoteOpen(l);
    }

    processDeliveries();

    for (pn_link_t* l = pn_link_head(connection, REQUIRES_CLOSE), *next = 0;
         l; l = next) {
        next = pn_link_next(l, REQUIRES_CLOSE);
        doLinkRemoteClose(l);
    }
    for (pn_session_t* s = pn_session_head(connection, REQUIRES_CLOSE), *next = 0;
         s; s = next) {
        next = pn_session_next(s, REQUIRES_CLOSE);
        doSessionRemoteClose(s);
    }
    if ((pn_connection_state(connection) & REQUIRES_CLOSE) == REQUIRES_CLOSE) {
        doConnectionRemoteClose();
    }
#endif  // !HAVE_PROTON_EVENTS
}
void Connection::processDeliveries()
{
#ifdef HAVE_PROTON_EVENTS
    // with the event API, there's no way to selectively process only
    // the delivery-related events.  We have to process all events:
    process();
#else
    for (pn_delivery_t* delivery = pn_work_head(connection); delivery; delivery = pn_work_next(delivery)) {
        doDeliveryUpdated(delivery);
    }
#endif
}

std::string Connection::getError()
{
    return get_error(connection, transport);
}

void Connection::abort()
{
    out.abort();
}

void Connection::setUserId(const std::string& user)
{
    ManagedConnection::setUserId(user);
    AclModule* acl = getBroker().getAcl();
    if (acl && !acl->approveConnection(*this))
    {
        throw Exception(qpid::amqp::error_conditions::RESOURCE_LIMIT_EXCEEDED, "User connection denied by configured limit");
    }
}

void Connection::closedByManagement()
{
    closeRequested = true;
    out.activateOutput();
}

// the peer has issued an Open performative
void Connection::doConnectionRemoteOpen()
{
    // respond in kind if we haven't yet
    if ((pn_connection_state(connection) & PN_LOCAL_UNINIT) == PN_LOCAL_UNINIT) {
        QPID_LOG_CAT(debug, model, id << " connection opened");
        open();
        setContainerId(pn_connection_remote_container(connection));
    }
}

// the peer has issued a Close performative
void Connection::doConnectionRemoteClose()
{
    if ((pn_connection_state(connection) & PN_LOCAL_CLOSED) == 0) {
        QPID_LOG_CAT(debug, model, id << " connection closed");
        pn_connection_close(connection);
    }
}

// the peer has issued a Begin performative
void Connection::doSessionRemoteOpen(pn_session_t *session)
{
    if ((pn_session_state(session) & PN_LOCAL_UNINIT) == PN_LOCAL_UNINIT) {
        QPID_LOG_CAT(debug, model, id << " session begun");
        pn_session_open(session);
        boost::shared_ptr<Session> ssn(new Session(session, *this, out));
        sessions[session] = ssn;
    }
}

// the peer has issued an End performative
void Connection::doSessionRemoteClose(pn_session_t *session)
{
    if ((pn_session_state(session) & PN_LOCAL_CLOSED) == 0) {
        pn_session_close(session);
        Sessions::iterator i = sessions.find(session);
        if (i != sessions.end()) {
            i->second->close();
            sessions.erase(i);
            QPID_LOG_CAT(debug, model, id << " session ended");
        } else {
            QPID_LOG(error, id << " peer attempted to close unrecognised session");
        }
    }
    pn_session_free(session);
}

// the peer has issued an Attach performative
void Connection::doLinkRemoteOpen(pn_link_t *link)
{
    if ((pn_link_state(link) & PN_LOCAL_UNINIT) == PN_LOCAL_UNINIT) {
        pn_link_open(link);
        Sessions::iterator session = sessions.find(pn_link_session(link));
        if (session == sessions.end()) {
            QPID_LOG(error, id << " Link attached on unknown session!");
        } else {
            try {
                session->second->attach(link);
                QPID_LOG_CAT(debug, protocol, id << " link " << link << " attached on " << pn_link_session(link));
            } catch (const Exception& e) {
                QPID_LOG_CAT(error, protocol, "Error on attach: " << e.what());
                pn_condition_t* error = pn_link_condition(link);
                pn_condition_set_name(error, e.symbol());
                pn_condition_set_description(error, e.what());
                pn_link_close(link);
            } catch (const qpid::framing::UnauthorizedAccessException& e) {
                QPID_LOG_CAT(error, protocol, "Error on attach: " << e.what());
                pn_condition_t* error = pn_link_condition(link);
                pn_condition_set_name(error, qpid::amqp::error_conditions::UNAUTHORIZED_ACCESS.c_str());
                pn_condition_set_description(error, e.what());
                pn_link_close(link);
            } catch (const std::exception& e) {
                QPID_LOG_CAT(error, protocol, "Error on attach: " << e.what());
                pn_condition_t* error = pn_link_condition(link);
                pn_condition_set_name(error, qpid::amqp::error_conditions::INTERNAL_ERROR.c_str());
                pn_condition_set_description(error, e.what());
                pn_link_close(link);
            }
        }
    }
}

// the peer has issued a Detach performative with closed=true
void Connection::doLinkRemoteClose(pn_link_t *link)
{
    doLinkRemoteDetach(link, true);
}
// the peer has issued a Detach performative
void Connection::doLinkRemoteDetach(pn_link_t *link, bool closed)
{
    if ((pn_link_state(link) & PN_LOCAL_CLOSED) == 0) {
        if (closed) pn_link_close(link);
        //pn_link_detach was only introduced after 0.7, as was the event interface:
#ifdef HAVE_PROTON_EVENTS
        else pn_link_detach(link);
#endif
        Sessions::iterator session = sessions.find(pn_link_session(link));
        if (session == sessions.end()) {
            QPID_LOG(error, id << " peer attempted to detach link on unknown session!");
        } else {
            session->second->detach(link, closed);
            QPID_LOG_CAT(debug, model, id << " link detached");
        }
    }
    pn_link_free(link);
}

// the status of the delivery has changed
void Connection::doDeliveryUpdated(pn_delivery_t *delivery)
{
    pn_link_t* link = pn_delivery_link(delivery);
    if (pn_link_state(link) & PN_LOCAL_CLOSED) return;

    try {
        if (pn_link_is_receiver(link)) {
            Sessions::iterator i = sessions.find(pn_link_session(link));
            if (i != sessions.end()) {
                i->second->readable(link, delivery);
            } else {
                pn_delivery_update(delivery, PN_REJECTED);
            }
        } else { //i.e. SENDER
            Sessions::iterator i = sessions.find(pn_link_session(link));
            if (i != sessions.end()) {
                QPID_LOG(trace, id << " handling outgoing delivery for " << link << " on session " << pn_link_session(link));
                i->second->writable(link, delivery);
            } else {
                QPID_LOG(error, id << " Got delivery for non-existent session: " << pn_link_session(link) << ", link: " << link);
            }
        }
    } catch (const Exception& e) {
        QPID_LOG_CAT(error, protocol, "Error processing deliveries: " << e.what());
        pn_condition_t* error = pn_link_condition(link);
        pn_condition_set_name(error, e.symbol());
        pn_condition_set_description(error, e.what());
        pn_link_close(link);
    }
}

// check for failures of the transport:
bool Connection::checkTransportError(std::string& text)
{
    std::stringstream info;

#ifdef USE_PROTON_TRANSPORT_CONDITION
    pn_condition_t* tcondition = pn_transport_condition(transport);
    if (pn_condition_is_set(tcondition))
        info << "transport error: " << pn_condition_get_name(tcondition) << ", " << pn_condition_get_description(tcondition);
#else
    pn_error_t* terror = pn_transport_error(transport);
    if (terror) info << "transport error " << pn_error_text(terror) << " [" << terror << "]";
#endif

    text = info.str();
    return !text.empty();
}

}}} // namespace qpid::broker::amqp
