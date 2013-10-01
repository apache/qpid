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
#include "qpid/messaging/AddressImpl.h"
#include "qpid/messaging/Duration.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/MessageImpl.h"
#include "qpid/framing/Buffer.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/framing/Uuid.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/SystemInfo.h"
#include "qpid/sys/Time.h"
#include <vector>
extern "C" {
#include <proton/engine.h>
}

namespace qpid {
namespace messaging {
namespace amqp {

ConnectionContext::ConnectionContext(const std::string& url, const qpid::types::Variant::Map& o)
    : qpid::messaging::ConnectionOptions(o),
      engine(pn_transport()),
      connection(pn_connection()),
      //note: disabled read/write of header as now handled by engine
      writeHeader(false),
      readHeader(false),
      haveOutput(false),
      state(DISCONNECTED)
{
    urls.insert(urls.begin(), url);
    if (pn_transport_bind(engine, connection)) {
        //error
    }
    if (identifier.empty()) {
        identifier = qpid::types::Uuid(true).str();
    }
    pn_connection_set_container(connection, identifier.c_str());
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

bool ConnectionContext::isOpen() const
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    return state == CONNECTED && pn_connection_state(connection) & (PN_LOCAL_ACTIVE | PN_REMOTE_ACTIVE);
}

void ConnectionContext::endSession(boost::shared_ptr<SessionContext> ssn)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    if (pn_session_state(ssn->session) & PN_REMOTE_ACTIVE) {
        //wait for outstanding sends to settle
        while (!ssn->settled()) {
            QPID_LOG(debug, "Waiting for sends to settle before closing");
            wait(ssn);//wait until message has been confirmed
        }
    }

    if (pn_session_state(ssn->session) & PN_REMOTE_ACTIVE) {
        pn_session_close(ssn->session);
    }
    sessions.erase(ssn->getName());

    wakeupDriver();
}

void ConnectionContext::close()
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    if (state != CONNECTED) return;
    if (!(pn_connection_state(connection) & PN_LOCAL_CLOSED)) {
        for (SessionMap::iterator i = sessions.begin(); i != sessions.end(); ++i) {
            //wait for outstanding sends to settle
            while (!i->second->settled()) {
                QPID_LOG(debug, "Waiting for sends to settle before closing");
                wait(i->second);//wait until message has been confirmed
            }


            if (!(pn_session_state(i->second->session) & PN_LOCAL_CLOSED)) {
                pn_session_close(i->second->session);
            }
        }
        pn_connection_close(connection);
        wakeupDriver();
        //wait for close to be confirmed by peer?
        while (!(pn_connection_state(connection) & PN_REMOTE_CLOSED)) {
            if (state == DISCONNECTED) {
                QPID_LOG(warning, "Disconnected before close received from peer.");
                break;
            }
            lock.wait();
        }
        sessions.clear();
    }
    if (state != DISCONNECTED) {
        transport->close();
        while (state != DISCONNECTED) {
            lock.wait();
        }
    }
}

bool ConnectionContext::fetch(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<ReceiverContext> lnk, qpid::messaging::Message& message, qpid::messaging::Duration timeout)
{
    {
        qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
        checkClosed(ssn, lnk);
        if (!lnk->capacity) {
            pn_link_flow(lnk->receiver, 1);
            wakeupDriver();
        }
    }
    if (get(ssn, lnk, message, timeout)) {
        qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
        if (lnk->capacity) {
            pn_link_flow(lnk->receiver, 1);//TODO: is this the right approach?
            wakeupDriver();
        }
        return true;
    } else {
        {
            qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
            pn_link_drain(lnk->receiver, 0);
            wakeupDriver();
            while (pn_link_credit(lnk->receiver) && !pn_link_queued(lnk->receiver)) {
                QPID_LOG(debug, "Waiting for message or for credit to be drained: credit=" << pn_link_credit(lnk->receiver) << ", queued=" << pn_link_queued(lnk->receiver));
                wait(ssn, lnk);
            }
            if (lnk->capacity && pn_link_queued(lnk->receiver) == 0) {
                pn_link_flow(lnk->receiver, lnk->capacity);
            }
        }
        if (get(ssn, lnk, message, qpid::messaging::Duration::IMMEDIATE)) {
            qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
            if (lnk->capacity) {
                pn_link_flow(lnk->receiver, 1);
                wakeupDriver();
            }
            return true;
        } else {
            return false;
        }
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
        checkClosed(ssn, lnk);
        pn_delivery_t* current = pn_link_current((pn_link_t*) lnk->receiver);
        QPID_LOG(debug, "In ConnectionContext::get(), current=" << current);
        if (current) {
            qpid::messaging::MessageImpl& impl = MessageImplAccess::get(message);
            boost::shared_ptr<EncodedMessage> encoded(new EncodedMessage(pn_delivery_pending(current)));
            encoded->setNestAnnotationsOption(nestAnnotations);
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
            waitUntil(ssn, lnk, until);
        } else {
            return false;
        }
    }
    return false;
}

void ConnectionContext::acknowledge(boost::shared_ptr<SessionContext> ssn, qpid::messaging::Message* message, bool cumulative)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    checkClosed(ssn);
    if (message) {
        ssn->acknowledge(MessageImplAccess::get(*message).getInternalId(), cumulative);
    } else {
        ssn->acknowledge();
    }
    wakeupDriver();
}

void ConnectionContext::detach(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<SenderContext> lnk)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    if (pn_link_state(lnk->sender) & PN_LOCAL_ACTIVE) {
        lnk->close();
    }
    wakeupDriver();
    while (pn_link_state(lnk->sender) & PN_REMOTE_ACTIVE) {
        wait();
    }
    ssn->removeSender(lnk->getName());
}

void ConnectionContext::detach(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<ReceiverContext> lnk)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    if (pn_link_state(lnk->receiver) & PN_LOCAL_ACTIVE) {
        lnk->close();
    }
    wakeupDriver();
    while (pn_link_state(lnk->receiver) & PN_REMOTE_ACTIVE) {
        wait();
    }
    ssn->removeReceiver(lnk->getName());
}

void ConnectionContext::attach(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<SenderContext> lnk)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    lnk->configure();
    attach(ssn, lnk->sender);
    checkClosed(ssn, lnk);
    lnk->verify();
    QPID_LOG(debug, "Attach succeeded to " << lnk->getTarget());
}

void ConnectionContext::attach(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<ReceiverContext> lnk)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    lnk->configure();
    attach(ssn, lnk->receiver, lnk->capacity);
    checkClosed(ssn, lnk);
    lnk->verify();
    QPID_LOG(debug, "Attach succeeded from " << lnk->getSource());
}

void ConnectionContext::attach(boost::shared_ptr<SessionContext> ssn, pn_link_t* link, int credit)
{
    pn_link_open(link);
    QPID_LOG(debug, "Link attach sent for " << link << ", state=" << pn_link_state(link));
    if (credit) pn_link_flow(link, credit);
    wakeupDriver();
    while (pn_link_state(link) & PN_REMOTE_UNINIT) {
        QPID_LOG(debug, "Waiting for confirmation of link attach for " << link << ", state=" << pn_link_state(link) << "...");
        wait(ssn);
    }
}

void ConnectionContext::send(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<SenderContext> snd, const qpid::messaging::Message& message, bool sync)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    checkClosed(ssn);
    SenderContext::Delivery* delivery(0);
    while (!snd->send(message, &delivery)) {
        QPID_LOG(debug, "Waiting for capacity...");
        wait(ssn, snd);//wait for capacity
    }
    wakeupDriver();
    if (sync && delivery) {
        while (!delivery->accepted()) {
            QPID_LOG(debug, "Waiting for confirmation...");
            wait(ssn, snd);//wait until message has been confirmed
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
    wakeupDriver();
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

namespace {
pn_state_t REQUIRES_CLOSE = PN_LOCAL_ACTIVE | PN_REMOTE_CLOSED;
pn_state_t IS_CLOSED = PN_LOCAL_CLOSED | PN_REMOTE_CLOSED;
}

void ConnectionContext::reset()
{
    pn_transport_free(engine);
    pn_connection_free(connection);

    engine = pn_transport();
    connection = pn_connection();
    pn_connection_set_container(connection, identifier.c_str());
    bool enableTrace(false);
    QPID_LOG_TEST_CAT(trace, protocol, enableTrace);
    if (enableTrace) pn_transport_trace(engine, PN_TRACE_FRM);
    for (SessionMap::iterator i = sessions.begin(); i != sessions.end(); ++i) {
        i->second->reset(connection);
    }
    pn_transport_bind(engine, connection);
}

void ConnectionContext::check()
{
    if (state == DISCONNECTED) {
        if (ConnectionOptions::reconnect) {
            reset();
            autoconnect();
        } else {
            throw qpid::messaging::TransportFailure("Disconnected (reconnect disabled)");
        }
    }
    if ((pn_connection_state(connection) & REQUIRES_CLOSE) == REQUIRES_CLOSE) {
        pn_connection_close(connection);
        throw qpid::messaging::ConnectionError("Connection closed by peer");
    }
}

void ConnectionContext::wait()
{
    lock.wait();
    check();
}
void ConnectionContext::waitUntil(qpid::sys::AbsTime until)
{
    lock.wait(until);
    check();
}
void ConnectionContext::wait(boost::shared_ptr<SessionContext> ssn)
{
    wait();
    checkClosed(ssn);
}
void ConnectionContext::wait(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<ReceiverContext> lnk)
{
    wait();
    checkClosed(ssn, lnk);
}
void ConnectionContext::wait(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<SenderContext> lnk)
{
    wait();
    checkClosed(ssn, lnk);
}
void ConnectionContext::waitUntil(boost::shared_ptr<SessionContext> ssn, qpid::sys::AbsTime until)
{
    waitUntil(until);
    checkClosed(ssn);
}
void ConnectionContext::waitUntil(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<ReceiverContext> lnk, qpid::sys::AbsTime until)
{
    waitUntil(until);
    checkClosed(ssn, lnk);
}
void ConnectionContext::waitUntil(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<SenderContext> lnk, qpid::sys::AbsTime until)
{
    waitUntil(until);
    checkClosed(ssn, lnk);
}
void ConnectionContext::checkClosed(boost::shared_ptr<SessionContext> ssn)
{
    check();
    if ((pn_session_state(ssn->session) & REQUIRES_CLOSE) == REQUIRES_CLOSE) {
        pn_condition_t* error = pn_session_remote_condition(ssn->session);
        std::stringstream text;
        if (pn_condition_is_set(error)) {
            text << "Session ended by peer with " << pn_condition_get_name(error) << ": " << pn_condition_get_description(error);
        } else {
            text << "Session ended by peer";
        }
        pn_session_close(ssn->session);
        throw qpid::messaging::SessionError(text.str());
    } else if ((pn_session_state(ssn->session) & IS_CLOSED) == IS_CLOSED) {
        throw qpid::messaging::SessionError("Session has ended");
    }
}

void ConnectionContext::checkClosed(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<ReceiverContext> lnk)
{
    checkClosed(ssn, lnk->receiver);
}
void ConnectionContext::checkClosed(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<SenderContext> lnk)
{
    checkClosed(ssn, lnk->sender);
}
void ConnectionContext::checkClosed(boost::shared_ptr<SessionContext> ssn, pn_link_t* lnk)
{
    checkClosed(ssn);
    if ((pn_link_state(lnk) & REQUIRES_CLOSE) == REQUIRES_CLOSE) {
        pn_condition_t* error = pn_link_remote_condition(lnk);
        std::stringstream text;
        if (pn_condition_is_set(error)) {
            text << "Link detached by peer with " << pn_condition_get_name(error) << ": " << pn_condition_get_description(error);
        } else {
            text << "Link detached by peer";
        }
        pn_link_close(lnk);
        throw qpid::messaging::LinkError(text.str());
    } else if ((pn_link_state(lnk) & IS_CLOSED) == IS_CLOSED) {
        throw qpid::messaging::LinkError("Link is not attached");
    }
}

void ConnectionContext::restartSession(boost::shared_ptr<SessionContext> s)
{
    pn_session_open(s->session);
    wakeupDriver();
    while (pn_session_state(s->session) & PN_REMOTE_UNINIT) {
        wait();
    }

    for (SessionContext::SenderMap::iterator i = s->senders.begin(); i != s->senders.end(); ++i) {
        QPID_LOG(debug, id << " reattaching sender " << i->first);
        attach(s, i->second->sender);
        i->second->verify();
        QPID_LOG(debug, id << " sender " << i->first << " reattached");
        i->second->resend();
    }
    for (SessionContext::ReceiverMap::iterator i = s->receivers.begin(); i != s->receivers.end(); ++i) {
        QPID_LOG(debug, id << " reattaching receiver " << i->first);
        attach(s, i->second->receiver, i->second->capacity);
        i->second->verify();
        QPID_LOG(debug, id << " receiver " << i->first << " reattached");
    }
    wakeupDriver();
}

boost::shared_ptr<SessionContext> ConnectionContext::newSession(bool transactional, const std::string& n)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    if (transactional) throw qpid::messaging::MessagingException("Transactions not yet supported");
    std::string name = n.empty() ? qpid::framing::Uuid(true).str() : n;
    SessionMap::const_iterator i = sessions.find(name);
    if (i == sessions.end()) {
        boost::shared_ptr<SessionContext> s(new SessionContext(connection));
        s->setName(name);
        s->session = pn_session(connection);
        pn_session_open(s->session);
        wakeupDriver();
        while (pn_session_state(s->session) & PN_REMOTE_UNINIT) {
            wait();
        }
        sessions[name] = s;
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

std::size_t ConnectionContext::decodePlain(const char* buffer, std::size_t size)
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
        throw MessagingException(QPID_MSG("Error on input: " << getError()));
    } else {
        return 0;
    }

}
std::size_t ConnectionContext::encodePlain(char* buffer, std::size_t size)
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
        throw MessagingException(QPID_MSG("Error on output: " << getError()));
    } else if (n == PN_EOS) {
        haveOutput = false;
        return 0;//Is this right?
    } else {
        haveOutput = false;
        return 0;
    }
}
bool ConnectionContext::canEncodePlain()
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
    return *this;
}

std::size_t ConnectionContext::decode(const char* buffer, std::size_t size)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    size_t decoded = 0;
    if (sasl.get() && !sasl->authenticated()) {
        decoded = sasl->decode(buffer, size);
        if (!sasl->authenticated()) return decoded;
    }
    if (decoded < size) {
        if (sasl.get() && sasl->getSecurityLayer()) decoded += sasl->getSecurityLayer()->decode(buffer+decoded, size-decoded);
        else decoded += decodePlain(buffer+decoded, size-decoded);
    }
    return decoded;
}
std::size_t ConnectionContext::encode(char* buffer, std::size_t size)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    size_t encoded = 0;
    if (sasl.get() && sasl->canEncode()) {
        encoded += sasl->encode(buffer, size);
        if (!sasl->authenticated()) return encoded;
    }
    if (encoded < size) {
        if (sasl.get() && sasl->getSecurityLayer()) encoded += sasl->getSecurityLayer()->encode(buffer+encoded, size-encoded);
        else encoded += encodePlain(buffer+encoded, size-encoded);
    }
    return encoded;
}
bool ConnectionContext::canEncode()
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    if (sasl.get()) {
        if (sasl->canEncode()) return true;
        else if (!sasl->authenticated()) return false;
        else if (sasl->getSecurityLayer()) return sasl->getSecurityLayer()->canEncode();
    }
    return canEncodePlain();
}

namespace {
const std::string CLIENT_PROCESS_NAME("qpid.client_process");
const std::string CLIENT_PID("qpid.client_pid");
const std::string CLIENT_PPID("qpid.client_ppid");
pn_bytes_t convert(const std::string& s)
{
    pn_bytes_t result;
    result.start = const_cast<char*>(s.data());
    result.size = s.size();
    return result;
}
}
void ConnectionContext::setProperties()
{
    pn_data_t* data = pn_connection_properties(connection);
    pn_data_put_map(data);
    pn_data_enter(data);

    pn_data_put_symbol(data, convert(CLIENT_PROCESS_NAME));
    std::string processName = sys::SystemInfo::getProcessName();
    pn_data_put_string(data, convert(processName));

    pn_data_put_symbol(data, convert(CLIENT_PID));
    pn_data_put_int(data, sys::SystemInfo::getProcessId());

    pn_data_put_symbol(data, convert(CLIENT_PPID));
    pn_data_put_int(data, sys::SystemInfo::getParentProcessId());
    pn_data_exit(data);
}

const qpid::sys::SecuritySettings* ConnectionContext::getTransportSecuritySettings()
{
    return transport ?  transport->getSecuritySettings() : 0;
}

void ConnectionContext::open()
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    if (state != DISCONNECTED) throw qpid::messaging::ConnectionError("Connection was already opened!");
    if (!driver) driver = DriverImpl::getDefault();

    autoconnect();
}


namespace {
std::string asString(const std::vector<std::string>& v) {
    std::stringstream os;
    os << "[";
    for(std::vector<std::string>::const_iterator i = v.begin(); i != v.end(); ++i ) {
        if (i != v.begin()) os << ", ";
        os << *i;
    }
    os << "]";
    return os.str();
}
double FOREVER(std::numeric_limits<double>::max());
bool expired(const sys::AbsTime& start, double timeout)
{
    if (timeout == 0) return true;
    if (timeout == FOREVER) return false;
    qpid::sys::Duration used(start, qpid::sys::now());
    qpid::sys::Duration allowed((int64_t)(timeout*qpid::sys::TIME_SEC));
    return allowed < used;
}
const std::string COLON(":");
}

void ConnectionContext::autoconnect()
{
    qpid::sys::AbsTime started(qpid::sys::now());
    QPID_LOG(debug, "Starting connection, urls=" << asString(urls));
    for (double i = minReconnectInterval; !tryConnect(); i = std::min(i*2, maxReconnectInterval)) {
        if (!ConnectionOptions::reconnect) {
            throw qpid::messaging::TransportFailure("Failed to connect (reconnect disabled)");
        }
        if (limit >= 0 && retries++ >= limit) {
            throw qpid::messaging::TransportFailure("Failed to connect within reconnect limit");
        }
        if (expired(started, timeout)) {
            throw qpid::messaging::TransportFailure("Failed to connect within reconnect timeout");
        }
        QPID_LOG(debug, "Connection retry in " << i*1000*1000 << " microseconds, urls="
                 << asString(urls));
        qpid::sys::usleep(int64_t(i*1000*1000)); // Sleep in microseconds.
    }
    retries = 0;
}

bool ConnectionContext::tryConnect()
{
    for (std::vector<std::string>::const_iterator i = urls.begin(); i != urls.end(); ++i) {
        try {
            QPID_LOG(info, "Trying to connect to " << *i << "...");
            if (tryConnect(qpid::Url(*i, protocol.empty() ? qpid::Address::TCP : protocol))) {
                return true;
            }
        } catch (const qpid::messaging::TransportFailure& e) {
            QPID_LOG(info, "Failed to connect to " << *i << ": " << e.what());
        }
    }
    return false;
}

void ConnectionContext::reconnect(const std::string& url)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    if (state != DISCONNECTED) throw qpid::messaging::ConnectionError("Connection was already opened!");
    if (!driver) driver = DriverImpl::getDefault();
    reset();
    if (!tryConnect(qpid::Url(url, protocol.empty() ? qpid::Address::TCP : protocol))) {
        throw qpid::messaging::TransportFailure("Failed to connect");
    }
}

void ConnectionContext::reconnect()
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    if (state != DISCONNECTED) throw qpid::messaging::ConnectionError("Connection was already opened!");
    if (!driver) driver = DriverImpl::getDefault();
    reset();
    if (!tryConnect()) {
        throw qpid::messaging::TransportFailure("Failed to reconnect");
    }
}

bool ConnectionContext::tryConnect(const Url& url)
{
    if (url.getUser().size()) username = url.getUser();
    if (url.getPass().size()) password = url.getPass();

    for (Url::const_iterator i = url.begin(); i != url.end(); ++i) {
        if (tryConnect(*i)) {
            QPID_LOG(info, "Connected to " << *i);
            setCurrentUrl(*i);
            if (sasl.get()) {
                wakeupDriver();
                while (!sasl->authenticated()) {
                    QPID_LOG(debug, id << " Waiting to be authenticated...");
                    wait();
                }
                QPID_LOG(debug, id << " Authenticated");
            }

            QPID_LOG(debug, id << " Opening...");
            setProperties();
            pn_connection_open(connection);
            wakeupDriver(); //want to write
            while (pn_connection_state(connection) & PN_REMOTE_UNINIT) {
                wait();
            }
            if (!(pn_connection_state(connection) & PN_REMOTE_ACTIVE)) {
                throw qpid::messaging::ConnectionError("Failed to open connection");
            }
            QPID_LOG(debug, id << " Opened");

            return restartSessions();
        }
    }
    return false;
}

void ConnectionContext::setCurrentUrl(const qpid::Address& a)
{
    std::stringstream u;
    u << a;
    currentUrl = u.str();
}

std::string ConnectionContext::getUrl() const
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    if (state == CONNECTED) {
        return currentUrl;
    } else {
        return std::string();
    }
}


bool ConnectionContext::tryConnect(const qpid::Address& address)
{
    transport = driver->getTransport(address.protocol, *this);
    std::stringstream port;
    port << address.port;
    id = address.host + COLON + port.str();
    if (useSasl()) {
        sasl = std::auto_ptr<Sasl>(new Sasl(id, *this, address.host));
    }
    state = CONNECTING;
    try {
        QPID_LOG(debug, id << " Connecting ...");
        transport->connect(address.host, port.str());
        bool waiting(true);
        while (waiting) {
            switch (state) {
              case CONNECTED:
                QPID_LOG(debug, id << " Connected");
                return true;
              case CONNECTING:
                lock.wait();
                break;
              case DISCONNECTED:
                waiting = false;
                QPID_LOG(debug, id << " Failed to connect");
                break;
            }
        }
    } catch (const std::exception& e) {
        QPID_LOG(info, id << " Error while connecting: " << e.what());
        state = DISCONNECTED;
    }
    transport = boost::shared_ptr<Transport>();
    return false;
}

bool ConnectionContext::restartSessions()
{
    try {
        for (SessionMap::iterator i = sessions.begin(); i != sessions.end(); ++i) {
            restartSession(i->second);
        }
        return true;
    } catch (const qpid::TransportFailure& e) {
        QPID_LOG(debug, "Connection Failed to re-initialize sessions: " << e.what());
        return false;
    }
}


}}} // namespace qpid::messaging::amqp
