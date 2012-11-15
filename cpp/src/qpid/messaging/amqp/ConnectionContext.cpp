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
#include "ConnectionContext.h"
#include "DriverImpl.h"
#include "ReceiverContext.h"
#include "Sasl.h"
#include "SenderContext.h"
#include "SessionContext.h"
#include "Transport.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/messaging/Duration.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/MessageImpl.h"
#include "qpid/framing/Buffer.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/framing/Uuid.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Time.h"
#include <vector>
extern "C" {
#include <proton/engine.h>
}

namespace qpid {
namespace messaging {
namespace amqp {


ConnectionContext::ConnectionContext(const std::string& u, const qpid::types::Variant::Map& o)
    : qpid::messaging::ConnectionOptions(o),
      url(u),
      engine(pn_transport()),
      connection(pn_connection()),
      //note: disabled read/write of header as now handled by engine
      writeHeader(false),
      readHeader(false),
      haveOutput(false),
      state(DISCONNECTED)
{
    if (pn_transport_bind(engine, connection)) {
        //error
    }
    pn_connection_set_container(connection, "qpid::messaging");//TODO: take this from a connection option
    bool enableTrace(false);
    QPID_LOG_TEST_CAT(trace, protocol, enableTrace);
    if (enableTrace) pn_transport_trace(engine, PN_TRACE_FRM);
}

ConnectionContext::~ConnectionContext()
{
    close();
    sessions.clear();
    pn_transport_free(engine);
    pn_connection_free(connection);
}

namespace {
const std::string COLON(":");
}
void ConnectionContext::open()
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    if (state != DISCONNECTED) throw qpid::messaging::ConnectionError("Connection was already opened!");
    if (!driver) driver = DriverImpl::getDefault();

    for (Url::const_iterator i = url.begin(); state != CONNECTED && i != url.end(); ++i) {
        transport = driver->getTransport(i->protocol, *this);
        std::stringstream port;
        port << i->port;
        id = i->host + COLON + port.str();
        if (useSasl()) {
            sasl = std::auto_ptr<Sasl>(new Sasl(id, *this, i->host));
        }
        state = CONNECTING;
        try {
            QPID_LOG(debug, id << " Connecting ...");
            transport->connect(i->host, port.str());
        } catch (const std::exception& e) {
            QPID_LOG(info, id << " Error while connecting: " << e.what());
        }
        while (state == CONNECTING) {
            lock.wait();
        }
        if (state == DISCONNECTED) {
            QPID_LOG(debug, id << " Failed to connect");
            transport = boost::shared_ptr<Transport>();
        } else {
            QPID_LOG(debug, id << " Connected");
        }
    }

    if (state != CONNECTED) throw qpid::messaging::TransportFailure(QPID_MSG("Could not connect to " << url));

    if (sasl.get()) {
        wakeupDriver();
        while (!sasl->authenticated()) {
            QPID_LOG(debug, id << " Waiting to be authenticated...");
            wait();
        }
        QPID_LOG(debug, id << " Authenticated");
    }

    QPID_LOG(debug, id << " Opening...");
    pn_connection_open(connection);
    wakeupDriver(); //want to write
    while (pn_connection_state(connection) & PN_REMOTE_UNINIT) {
        wait();
    }
    if (!(pn_connection_state(connection) & PN_REMOTE_ACTIVE)) {
        throw qpid::messaging::ConnectionError("Failed to open connection");
    }
    QPID_LOG(debug, id << " Opened");
}

bool ConnectionContext::isOpen() const
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    return pn_connection_state(connection) & (PN_LOCAL_ACTIVE | PN_REMOTE_ACTIVE);
}

void ConnectionContext::endSession(boost::shared_ptr<SessionContext> ssn)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    pn_session_close(ssn->session);
    //TODO: need to destroy session and remove context from map
    wakeupDriver();
}

void ConnectionContext::close()
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    if (state != CONNECTED) return;
    if (!(pn_connection_state(connection) & PN_LOCAL_CLOSED)) {
        for (SessionMap::iterator i = sessions.begin(); i != sessions.end(); ++i){
            if (!(pn_session_state(i->second->session) & PN_LOCAL_CLOSED)) {
                pn_session_close(i->second->session);
            }
        }
        pn_connection_close(connection);
        wakeupDriver();
        //wait for close to be confirmed by peer?
        while (!(pn_connection_state(connection) & PN_REMOTE_CLOSED)) {
            wait();
        }
        sessions.clear();
    }
    transport->close();
    while (state != DISCONNECTED) {
        lock.wait();
    }
}

bool ConnectionContext::fetch(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<ReceiverContext> lnk, qpid::messaging::Message& message, qpid::messaging::Duration timeout)
{
    {
        qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
        if (!lnk->capacity) {
            pn_link_flow(lnk->receiver, 1);
            wakeupDriver();
        }
    }
    if (get(ssn, lnk, message, timeout)) {
        qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
        if (lnk->capacity) {
            pn_link_flow(lnk->receiver, 1);//TODO: is this the right approach?
        }
        return true;
    } else {
        {
            qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
            pn_link_drain(lnk->receiver, 0);
            wakeupDriver();
            while (pn_link_credit((pn_link_t*) lnk->receiver) - pn_link_queued((pn_link_t*) lnk->receiver)) {
                QPID_LOG(notice, "Waiting for credit to be drained: " << (pn_link_credit((pn_link_t*) lnk->receiver) - pn_link_queued((pn_link_t*) lnk->receiver)));
                wait();
            }
        }
        return get(ssn, lnk, message, qpid::messaging::Duration::IMMEDIATE);
    }
}

qpid::sys::AbsTime convert(qpid::messaging::Duration timeout)
{
    qpid::sys::AbsTime until;
    uint64_t ms = timeout.getMilliseconds();
    if (ms < (uint64_t) (qpid::sys::TIME_INFINITE/qpid::sys::TIME_MSEC)) {
        return qpid::sys::AbsTime(qpid::sys::now(), ms * qpid::sys::TIME_MSEC);
    } else {
        return qpid::sys::FAR_FUTURE;
    }
}

bool ConnectionContext::get(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<ReceiverContext> lnk, qpid::messaging::Message& message, qpid::messaging::Duration timeout)
{
    qpid::sys::AbsTime until(convert(timeout));
    while (true) {
        qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
        pn_delivery_t* current = pn_link_current((pn_link_t*) lnk->receiver);
        QPID_LOG(debug, "In ConnectionContext::get(), current=" << current);
        if (current) {
            qpid::messaging::MessageImpl& impl = MessageImplAccess::get(message);
            boost::shared_ptr<EncodedMessage> encoded(new EncodedMessage(pn_delivery_pending(current)));
            ssize_t read = pn_link_recv(lnk->receiver, encoded->getData(), encoded->getSize());
            if (read < 0) throw qpid::messaging::MessagingException("Failed to read message");
            encoded->trim((size_t) read);
            QPID_LOG(debug, "Received message of " << encoded->getSize() << " bytes: ");
            encoded->init(impl);
            impl.setEncoded(encoded);
            impl.setInternalId(ssn->record(current));
            pn_link_advance(lnk->receiver);
            return true;
        } else if (until > qpid::sys::now()) {
            wait();
        } else {
            return false;
        }
    }
    return false;
}

void ConnectionContext::acknowledge(boost::shared_ptr<SessionContext> ssn, qpid::messaging::Message* message, bool cumulative)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    if (message) {
        ssn->acknowledge(MessageImplAccess::get(*message).getInternalId(), cumulative);
    } else {
        ssn->acknowledge();
    }
    wakeupDriver();
}


void ConnectionContext::attach(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<SenderContext> lnk)
{
    pn_terminus_t* target = pn_link_target((pn_link_t*) lnk->sender);
    pn_terminus_set_address(target, lnk->getTarget().c_str());
    attach(ssn->session, (pn_link_t*) lnk->sender);
    if (!pn_link_remote_target((pn_link_t*) lnk->sender)) {
        std::string msg("No such target : ");
        msg += lnk->getTarget();
        throw qpid::messaging::NotFound(msg);
    }
}

void ConnectionContext::attach(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<ReceiverContext> lnk)
{
    lnk->configure();
    attach(ssn->session, lnk->receiver, lnk->capacity);
    if (!pn_link_remote_source(lnk->receiver)) {
        std::string msg("No such source : ");
        msg += lnk->getSource();
        throw qpid::messaging::NotFound(msg);
    }
}

void ConnectionContext::attach(pn_session_t* /*session*/, pn_link_t* link, int credit)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    QPID_LOG(debug, "Attaching link " << link << ", state=" << pn_link_state(link));
    pn_link_open(link);
    QPID_LOG(debug, "Link attached " << link << ", state=" << pn_link_state(link));
    if (credit) pn_link_flow(link, credit);
    wakeupDriver();
    while (pn_link_state(link) & PN_REMOTE_UNINIT) {
        QPID_LOG(debug, "waiting for confirmation of link attach for " << link << ", state=" << pn_link_state(link));
        wait();
    }
}

void ConnectionContext::send(boost::shared_ptr<SenderContext> snd, const qpid::messaging::Message& message, bool sync)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    SenderContext::Delivery* delivery(0);
    while (!(delivery = snd->send(message))) {
        QPID_LOG(debug, "Waiting for capacity...");
        wait();//wait for capacity
    }
    wakeupDriver();
    if (sync) {
        while (!delivery->accepted()) {
            QPID_LOG(debug, "Waiting for confirmation...");
            wait();//wait until message has been confirmed
        }
    }
}

void ConnectionContext::setCapacity(boost::shared_ptr<SenderContext> sender, uint32_t capacity)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    sender->setCapacity(capacity);
}
uint32_t ConnectionContext::getCapacity(boost::shared_ptr<SenderContext> sender)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    return sender->getCapacity();
}
uint32_t ConnectionContext::getUnsettled(boost::shared_ptr<SenderContext> sender)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    return sender->getUnsettled();
}

void ConnectionContext::setCapacity(boost::shared_ptr<ReceiverContext> receiver, uint32_t capacity)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    receiver->setCapacity(capacity);
    pn_link_flow((pn_link_t*) receiver->receiver, receiver->getCapacity());
}
uint32_t ConnectionContext::getCapacity(boost::shared_ptr<ReceiverContext> receiver)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    return receiver->getCapacity();
}
uint32_t ConnectionContext::getAvailable(boost::shared_ptr<ReceiverContext> receiver)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    return receiver->getAvailable();
}
uint32_t ConnectionContext::getUnsettled(boost::shared_ptr<ReceiverContext> receiver)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    return receiver->getUnsettled();
}

void ConnectionContext::activateOutput()
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    wakeupDriver();
}
/**
 * Expects lock to be held by caller
 */
void ConnectionContext::wakeupDriver()
{
    switch (state) {
      case CONNECTED:
        haveOutput = true;
        transport->activateOutput();
        QPID_LOG(debug, "wakeupDriver()");
        break;
      case DISCONNECTED:
      case CONNECTING:
        QPID_LOG(error, "wakeupDriver() called while not connected");
        break;
    }
}

void ConnectionContext::wait()
{
    lock.wait();
    if (state == DISCONNECTED) {
        throw qpid::messaging::TransportFailure("Disconnected");
    }
    //check for any closed links, sessions or indeed the connection
}

boost::shared_ptr<SessionContext> ConnectionContext::newSession(bool transactional, const std::string& n)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    if (transactional) throw qpid::messaging::MessagingException("Transactions not yet supported");
    std::string name = n.empty() ? qpid::framing::Uuid(true).str() : n;
    SessionMap::const_iterator i = sessions.find(name);
    if (i == sessions.end()) {
        boost::shared_ptr<SessionContext> s(new SessionContext(connection));
        s->session = pn_session(connection);
        pn_session_open(s->session);
        sessions[name] = s;
        wakeupDriver();
        while (pn_session_state(s->session) & PN_REMOTE_UNINIT) {
            wait();
        }
        return s;
    } else {
        throw qpid::messaging::KeyError(std::string("Session already exists: ") + name);
    }

}
boost::shared_ptr<SessionContext> ConnectionContext::getSession(const std::string& name) const
{
    SessionMap::const_iterator i = sessions.find(name);
    if (i == sessions.end()) {
        throw qpid::messaging::KeyError(std::string("No such session") + name);
    } else {
        return i->second;
    }
}

void ConnectionContext::setOption(const std::string& name, const qpid::types::Variant& value)
{
    set(name, value);
}

std::string ConnectionContext::getAuthenticatedUsername()
{
    return sasl.get() ? sasl->getAuthenticatedUsername() : std::string();
}

std::size_t ConnectionContext::decode(const char* buffer, std::size_t size)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    QPID_LOG(trace, id << " decode(" << size << ")");
    if (readHeader) {
        size_t decoded = readProtocolHeader(buffer, size);
        if (decoded < size) {
            decoded += decode(buffer + decoded, size - decoded);
        }
        return decoded;
    }

    //TODO: Fix pn_engine_input() to take const buffer
    ssize_t n = pn_transport_input(engine, const_cast<char*>(buffer), size);
    if (n > 0 || n == PN_EOS) {
        //If engine returns EOS, have no way of knowing how many bytes
        //it processed, but can assume none need to be reprocessed so
        //consider them all read:
        if (n == PN_EOS) n = size;
        QPID_LOG_CAT(debug, network, id << " decoded " << n << " bytes from " << size)
        pn_transport_tick(engine, 0);
        lock.notifyAll();
        return n;
    } else if (n == PN_ERR) {
        throw qpid::Exception(QPID_MSG("Error on input: " << getError()));
    } else {
        return 0;
    }

}
std::size_t ConnectionContext::encode(char* buffer, std::size_t size)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    QPID_LOG(trace, id << " encode(" << size << ")");
    if (writeHeader) {
        size_t encoded = writeProtocolHeader(buffer, size);
        if (encoded < size) {
            encoded += encode(buffer + encoded, size - encoded);
        }
        return encoded;
    }

    ssize_t n = pn_transport_output(engine, buffer, size);
    if (n > 0) {
        QPID_LOG_CAT(debug, network, id << " encoded " << n << " bytes from " << size)
        haveOutput = true;
        return n;
    } else if (n == PN_ERR) {
        throw qpid::Exception(QPID_MSG("Error on output: " << getError()));
    } else if (n == PN_EOS) {
        haveOutput = false;
        return 0;//Is this right?
    } else {
        haveOutput = false;
        return 0;
    }
}
bool ConnectionContext::canEncode()
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    return haveOutput && state == CONNECTED;
}
void ConnectionContext::closed()
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    state = DISCONNECTED;
    lock.notifyAll();
}
void ConnectionContext::opened()
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    state = CONNECTED;
    lock.notifyAll();
}
bool ConnectionContext::isClosed() const
{
    return !isOpen();
}
namespace {
qpid::framing::ProtocolVersion AMQP_1_0_PLAIN(1,0,qpid::framing::ProtocolVersion::AMQP);
}

std::string ConnectionContext::getError()
{
    std::stringstream text;
    pn_error_t* cerror = pn_connection_error(connection);
    if (cerror) text << "connection error " << pn_error_text(cerror);
    pn_error_t* terror = pn_transport_error(engine);
    if (terror) text << "transport error " << pn_error_text(terror);
    return text.str();
}

framing::ProtocolVersion ConnectionContext::getVersion() const
{
    return AMQP_1_0_PLAIN;
}

std::size_t ConnectionContext::readProtocolHeader(const char* buffer, std::size_t size)
{
    framing::ProtocolInitiation pi(getVersion());
    if (size >= pi.encodedSize()) {
        readHeader = false;
        qpid::framing::Buffer out(const_cast<char*>(buffer), size);
        pi.decode(out);
        QPID_LOG_CAT(debug, protocol, id << " read protocol header: " << pi);
        return pi.encodedSize();
    } else {
        return 0;
    }
}
std::size_t ConnectionContext::writeProtocolHeader(char* buffer, std::size_t size)
{
    framing::ProtocolInitiation pi(getVersion());
    if (size >= pi.encodedSize()) {
        QPID_LOG_CAT(debug, protocol, id << " writing protocol header: " << pi);
        writeHeader = false;
        qpid::framing::Buffer out(buffer, size);
        pi.encode(out);
        return pi.encodedSize();
    } else {
        QPID_LOG_CAT(debug, protocol, id << " insufficient buffer for protocol header: " << size)
        return 0;
    }
}
bool ConnectionContext::useSasl()
{
    return !(mechanism == "none" || mechanism == "NONE" || mechanism == "None");
}

qpid::sys::Codec& ConnectionContext::getCodec()
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    if (sasl.get()) {
        qpid::sys::Codec* c = sasl->getCodec();
        if (c) return *c;
        lock.notifyAll();
    }
    return *this;
}

}}} // namespace qpid::messaging::amqp
