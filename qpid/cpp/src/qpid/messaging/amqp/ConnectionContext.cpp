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
#include "PnData.h"
#include "ReceiverContext.h"
#include "Sasl.h"
#include "SenderContext.h"
#include "SessionContext.h"
#include "Transaction.h"
#include "Transport.h"
#include "util.h"
#include "qpid/amqp/descriptors.h"
#include "qpid/amqp/Encoder.h"
#include "qpid/amqp/Descriptor.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/messaging/AddressImpl.h"
#include "qpid/messaging/Duration.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/MessageImpl.h"
#include "qpid/framing/Buffer.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/framing/Uuid.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/SecurityLayer.h"
#include "qpid/sys/SystemInfo.h"
#include "qpid/sys/Time.h"
#include "qpid/sys/Timer.h"
#include "qpid/sys/urlAdd.h"
#include "config.h"
#include <boost/lexical_cast.hpp>
#include <boost/bind.hpp>
#include <vector>
extern "C" {
#include <proton/engine.h>
}

namespace qpid {
namespace messaging {
namespace amqp {
using types::Variant;

namespace {
//remove conditional when 0.5 is no longer supported
#ifdef HAVE_PROTON_TRACER
void do_trace(pn_transport_t* transport, const char* message)
{
    ConnectionContext* c = reinterpret_cast<ConnectionContext*>(pn_transport_get_context(transport));
    if (c) c->trace(message);
}

void set_tracer(pn_transport_t* transport, void* context)
{
    pn_transport_set_context(transport, context);
    pn_transport_set_tracer(transport, &do_trace);
}
#else
void set_tracer(pn_transport_t*, void*)
{
}
#endif

#ifdef USE_PROTON_TRANSPORT_CONDITION
std::string get_error(pn_connection_t* connection, pn_transport_t* transport)
{
    std::stringstream text;
    pn_error_t* cerror = pn_connection_error(connection);
    if (cerror) text << "connection error " << pn_error_text(cerror) << " [" << cerror << "]";
    pn_condition_t* tcondition = pn_transport_condition(transport);
    if (pn_condition_is_set(tcondition)) text << get_error_string(tcondition, "transport error", ": ");
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

class ConnectionTickerTask : public qpid::sys::TimerTask
{
    qpid::sys::Timer& timer;
    ConnectionContext& connection;
  public:
    ConnectionTickerTask(const qpid::sys::Duration& interval, qpid::sys::Timer& t, ConnectionContext& c) :
        TimerTask(interval, "ConnectionTicker"),
        timer(t),
        connection(c)
    {}

    void fire() {
        QPID_LOG(debug, "ConnectionTickerTask fired");
        // Setup next firing
        setupNextFire();
        timer.add(this);

        // Send Ticker
        connection.activateOutput();
    }
};
}

void ConnectionContext::trace(const char* message) const
{
    QPID_LOG_CAT(trace, protocol, "[" << identifier << "]: " << message);
}

ConnectionContext::ConnectionContext(const std::string& url, const qpid::types::Variant::Map& o)
    : qpid::messaging::ConnectionOptions(o),
      fullUrl(url, protocol.empty() ? qpid::Address::TCP : protocol),
      engine(pn_transport()),
      connection(pn_connection()),
      //note: disabled read/write of header as now handled by engine
      writeHeader(false),
      readHeader(false),
      haveOutput(false),
      state(DISCONNECTED),
      codecAdapter(*this),
      notifyOnWrite(false)
{
    // Concatenate all known URLs into a single URL, get rid of duplicate addresses.
    sys::urlAddStrings(fullUrl, urls.begin(), urls.end(), protocol.empty() ?
                       qpid::Address::TCP : protocol);
    if (identifier.empty()) {
        identifier = qpid::types::Uuid(true).str();
    }
    configureConnection();
}

ConnectionContext::~ConnectionContext()
{
    if (ticker) ticker->cancel();
    close();
    sessions.clear();
    pn_connection_free(connection);
    pn_transport_free(engine);
}

bool ConnectionContext::isOpen() const
{
    sys::Monitor::ScopedLock l(lock);
    return state == CONNECTED && pn_connection_state(connection) & (PN_LOCAL_ACTIVE | PN_REMOTE_ACTIVE);
}

void ConnectionContext::sync(boost::shared_ptr<SessionContext> ssn)
{
    sys::Monitor::ScopedLock l(lock);
    syncLH(ssn, l);
}

void ConnectionContext::syncLH(boost::shared_ptr<SessionContext> ssn, sys::Monitor::ScopedLock&) {
    while (!ssn->settled()) {
        QPID_LOG(debug, "Waiting for sends to settle on sync()");
        wait(ssn);//wait until message has been confirmed
        wakeupDriver();
    }
    checkClosed(ssn);
}

void ConnectionContext::endSession(boost::shared_ptr<SessionContext> ssn)
{
    sys::Monitor::ScopedLock l(lock);
    if (pn_session_state(ssn->session) & PN_REMOTE_ACTIVE) {
        //explicitly release messages that have yet to be fetched
        for (SessionContext::ReceiverMap::iterator i = ssn->receivers.begin(); i != ssn->receivers.end(); ++i) {
            drain_and_release_messages(ssn, i->second);
        }
        syncLH(ssn, l);
    }

    if (pn_session_state(ssn->session) & PN_REMOTE_ACTIVE) {
        pn_session_close(ssn->session);
    }
    sessions.erase(ssn->getName());

    wakeupDriver();
}

void ConnectionContext::close()
{
    sys::Monitor::ScopedLock l(lock);
    if (state != CONNECTED) return;
    if (!(pn_connection_state(connection) & PN_LOCAL_CLOSED)) {
        for (SessionMap::iterator i = sessions.begin(); i != sessions.end(); ++i) {
            syncLH(i->second, l);
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
    if (ticker) {
        ticker->cancel();
        ticker = boost::intrusive_ptr<qpid::sys::TimerTask>();
    }
}

bool ConnectionContext::fetch(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<ReceiverContext> lnk, qpid::messaging::Message& message, qpid::messaging::Duration timeout)
{
    /**
     * For fetch() on a receiver with zero capacity, need to reissue the
     * credit on reconnect, so track the fetches in progress.
     */
    qpid::sys::AtomicCount::ScopedIncrement track(lnk->fetching);
    {
        sys::Monitor::ScopedLock l(lock);
        checkClosed(ssn, lnk);
        if (!lnk->capacity) {
            pn_link_flow(lnk->receiver, 1);
            wakeupDriver();
        }
    }
    if (get(ssn, lnk, message, timeout)) {
        return true;
    } else {
        {
            sys::Monitor::ScopedLock l(lock);
            pn_link_drain(lnk->receiver, 0);
            wakeupDriver();
            while (pn_link_draining(lnk->receiver) && !pn_link_queued(lnk->receiver)) {
                QPID_LOG(debug, "Waiting for message or for credit to be drained: credit=" << pn_link_credit(lnk->receiver) << ", queued=" << pn_link_queued(lnk->receiver));
                wait(ssn, lnk);
            }
            if (lnk->capacity && pn_link_queued(lnk->receiver) == 0) {
                pn_link_flow(lnk->receiver, lnk->capacity);
            }
        }
        if (get(ssn, lnk, message, qpid::messaging::Duration::IMMEDIATE)) {
            sys::Monitor::ScopedLock l(lock);
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
        sys::Monitor::ScopedLock l(lock);
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
            if (lnk->capacity) {
                pn_link_flow(lnk->receiver, 1);
                if (lnk->wakeupToIssueCredit()) {
                    wakeupDriver();
                } else {
                    haveOutput = true;
                }
            }
            // Automatically ack messages if we are in a transaction.
            if (ssn->transaction)
                acknowledgeLH(ssn, &message, false, l);
            return true;
        } else if (until > qpid::sys::now()) {
            waitUntil(ssn, lnk, until);
        } else {
            return false;
        }
    }
    return false;
}

boost::shared_ptr<ReceiverContext> ConnectionContext::nextReceiver(boost::shared_ptr<SessionContext> ssn, qpid::messaging::Duration timeout)
{
    qpid::sys::AbsTime until(convert(timeout));
    while (true) {
        sys::Monitor::ScopedLock l(lock);
        checkClosed(ssn);
        boost::shared_ptr<ReceiverContext> r = ssn->nextReceiver();
        if (r) {
            return r;
        } else if (until > qpid::sys::now()) {
            waitUntil(ssn, until);
        } else {
            return boost::shared_ptr<ReceiverContext>();
        }
    }
}

void ConnectionContext::acknowledge(boost::shared_ptr<SessionContext> ssn, qpid::messaging::Message* message, bool cumulative) {
    sys::Monitor::ScopedLock l(lock);
    acknowledgeLH(ssn, message, cumulative, l);
}

void ConnectionContext::acknowledgeLH(boost::shared_ptr<SessionContext> ssn, qpid::messaging::Message* message, bool cumulative, sys::Monitor::ScopedLock&)
{
    checkClosed(ssn);
    if (message) {
        ssn->acknowledge(MessageImplAccess::get(*message).getInternalId(), cumulative);
    } else {
        ssn->acknowledge();
    }
    wakeupDriver();
}

void ConnectionContext::nack(boost::shared_ptr<SessionContext> ssn, qpid::messaging::Message& message, bool reject)
{
    sys::Monitor::ScopedLock l(lock);
    checkClosed(ssn);
    ssn->nack(MessageImplAccess::get(message).getInternalId(), reject);
    wakeupDriver();
}

void ConnectionContext::detach(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<SenderContext> lnk)
{
    sys::Monitor::ScopedLock l(lock);
    if (pn_link_state(lnk->sender) & PN_LOCAL_ACTIVE) {
        lnk->close();
    }
    wakeupDriver();
    while (pn_link_state(lnk->sender) & PN_REMOTE_ACTIVE) {
        wait(ssn);
    }
    ssn->removeSender(lnk->getName());
}

void ConnectionContext::drain_and_release_messages(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<ReceiverContext> lnk)
{
    pn_link_drain(lnk->receiver, 0);
    wakeupDriver();
    //Not all implementations handle drain correctly, so limit the
    //time spent waiting for it
    qpid::sys::AbsTime until(qpid::sys::now(), qpid::sys::TIME_SEC*2);
    while (pn_link_credit(lnk->receiver) > pn_link_queued(lnk->receiver) && until > qpid::sys::now()) {
        QPID_LOG(debug, "Waiting for credit to be drained: credit=" << pn_link_credit(lnk->receiver) << ", queued=" << pn_link_queued(lnk->receiver));
        waitUntil(ssn, lnk, until);
    }
    //release as yet unfetched messages:
    for (pn_delivery_t* d = pn_link_current(lnk->receiver); d; d = pn_link_current(lnk->receiver)) {
        pn_link_advance(lnk->receiver);
        pn_delivery_update(d, PN_RELEASED);
        pn_delivery_settle(d);
    }
}

void ConnectionContext::detach(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<ReceiverContext> lnk)
{
    sys::Monitor::ScopedLock l(lock);
    drain_and_release_messages(ssn, lnk);
    if (pn_link_state(lnk->receiver) & PN_LOCAL_ACTIVE) {
        lnk->close();
    }
    wakeupDriver();
    while (pn_link_state(lnk->receiver) & PN_REMOTE_ACTIVE) {
        wait(ssn);
    }
    ssn->removeReceiver(lnk->getName());
}

void ConnectionContext::attach(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<SenderContext> lnk)
{
    lnk->configure();
    attach(ssn, lnk->sender);
    checkClosed(ssn, lnk);
    lnk->verify();
    QPID_LOG(debug, "Attach succeeded to " << lnk->getTarget());
}

void ConnectionContext::attach(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<ReceiverContext> lnk)
{
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

boost::shared_ptr<SenderContext> ConnectionContext::createSender(boost::shared_ptr<SessionContext> session, const qpid::messaging::Address& address)
{
    sys::Monitor::ScopedLock l(lock);
    boost::shared_ptr<SenderContext> sender = session->createSender(address, setToOnSend);
    try {
        attach(session, sender);
        return sender;
    } catch (...) {
        session->removeSender(sender->getName());
        throw;
    }

}
boost::shared_ptr<ReceiverContext> ConnectionContext::createReceiver(boost::shared_ptr<SessionContext> session, const qpid::messaging::Address& address)
{
    sys::Monitor::ScopedLock l(lock);
    boost::shared_ptr<ReceiverContext> receiver = session->createReceiver(address);
    try {
        attach(session, receiver);
        return receiver;
    } catch (...) {
        session->removeReceiver(receiver->getName());
        throw;
    }
}
boost::shared_ptr<SenderContext> ConnectionContext::getSender(boost::shared_ptr<SessionContext> session, const std::string& name) const
{
    sys::Monitor::ScopedLock l(lock);
    return session->getSender(name);
}

boost::shared_ptr<ReceiverContext> ConnectionContext::getReceiver(boost::shared_ptr<SessionContext> session, const std::string& name) const
{
    sys::Monitor::ScopedLock l(lock);
    return session->getReceiver(name);
}

void ConnectionContext::send(
    boost::shared_ptr<SessionContext> ssn,
    boost::shared_ptr<SenderContext> snd,
    const qpid::messaging::Message& message,
    bool sync,
    SenderContext::Delivery** delivery)
{
    sys::Monitor::ScopedLock l(lock);
    sendLH(ssn, snd, message, sync, delivery, l);
}

void ConnectionContext::sendLH(
    boost::shared_ptr<SessionContext> ssn,
    boost::shared_ptr<SenderContext> snd,
    const qpid::messaging::Message& message,
    bool sync,
    SenderContext::Delivery** delivery,
    sys::Monitor::ScopedLock&)
{
    checkClosed(ssn);
    while (pn_transport_pending(engine) > 65536) {
        QPID_LOG(debug, "Have " << pn_transport_pending(engine) << " bytes of output pending; waiting for this to be written...");
        notifyOnWrite = true;
        wakeupDriver();
        wait(ssn, snd);
        notifyOnWrite = false;
    }
    while (!snd->send(message, delivery)) {
        QPID_LOG(debug, "Waiting for capacity...");
        wait(ssn, snd);//wait for capacity
    }
    wakeupDriver();
    if (sync && *delivery) {
        while (!(*delivery)->delivered()) {
            QPID_LOG(debug, "Waiting for confirmation...");
            wait(ssn, snd);//wait until message has been confirmed
        }
        if ((*delivery)->rejected()) {
            throw MessageRejected("Message was rejected by peer");
        }

    }
}

void ConnectionContext::setCapacity(boost::shared_ptr<SenderContext> sender, uint32_t capacity)
{
    sys::Monitor::ScopedLock l(lock);
    sender->setCapacity(capacity);
}
uint32_t ConnectionContext::getCapacity(boost::shared_ptr<SenderContext> sender)
{
    sys::Monitor::ScopedLock l(lock);
    return sender->getCapacity();
}
uint32_t ConnectionContext::getUnsettled(boost::shared_ptr<SenderContext> sender)
{
    sys::Monitor::ScopedLock l(lock);
    return sender->getUnsettled();
}

void ConnectionContext::setCapacity(boost::shared_ptr<ReceiverContext> receiver, uint32_t capacity)
{
    sys::Monitor::ScopedLock l(lock);
    receiver->setCapacity(capacity);
    pn_link_flow((pn_link_t*) receiver->receiver, receiver->getCapacity());
    wakeupDriver();
}
uint32_t ConnectionContext::getCapacity(boost::shared_ptr<ReceiverContext> receiver)
{
    sys::Monitor::ScopedLock l(lock);
    return receiver->getCapacity();
}
uint32_t ConnectionContext::getAvailable(boost::shared_ptr<ReceiverContext> receiver)
{
    sys::Monitor::ScopedLock l(lock);
    return receiver->getAvailable();
}
uint32_t ConnectionContext::getUnsettled(boost::shared_ptr<ReceiverContext> receiver)
{
    sys::Monitor::ScopedLock l(lock);
    return receiver->getUnsettled();
}

void ConnectionContext::activateOutput()
{
    sys::Monitor::ScopedLock l(lock);
    if (state == CONNECTED) wakeupDriver();
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
    pn_connection_free(connection);
    pn_transport_free(engine);

    engine = pn_transport();
    connection = pn_connection();
    configureConnection();

    for (SessionMap::iterator i = sessions.begin(); i != sessions.end(); ++i) {
        i->second->reset(connection);
    }
}

bool ConnectionContext::check() {
    if (checkDisconnected()) {
        if (ConnectionOptions::reconnect) {
            QPID_LOG(notice, "Auto-reconnecting to " << fullUrl);
            autoconnect();
            QPID_LOG(notice, "Auto-reconnected to " << currentUrl);
        } else {
            throw qpid::messaging::TransportFailure("Disconnected (reconnect disabled)");
        }
        return true;
    }
    return false;
}

bool ConnectionContext::checkDisconnected() {
    if (state == DISCONNECTED) {
        reset();
    } else {
        if ((pn_connection_state(connection) & REQUIRES_CLOSE) == REQUIRES_CLOSE) {
            std::string text = get_error_string(pn_connection_remote_condition(connection), "Connection closed by peer");
            pn_connection_close(connection);
            throw qpid::messaging::ConnectionError(text);
        }
    }
    return state == DISCONNECTED;
}

void ConnectionContext::wait()
{
    if (check()) return;        // Reconnected, may need to re-test condition.
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
    ssn->error.raise();
    if ((pn_session_state(ssn->session) & REQUIRES_CLOSE) == REQUIRES_CLOSE) {
        std::string text = get_error_string(pn_session_remote_condition(ssn->session), "Session ended by peer");
        pn_session_close(ssn->session);
        throw qpid::messaging::SessionError(text);
    } else if ((pn_session_state(ssn->session) & IS_CLOSED) == IS_CLOSED) {
        throw qpid::messaging::SessionClosed();
    }
}

bool ConnectionContext::isClosed(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<ReceiverContext> lnk)
{
    try {
        checkClosed(ssn, lnk->receiver);
        return false;
    } catch (const LinkError&) {
        return true;
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
        std::string text = get_error_string(error, "Link detached by peer");
        pn_link_close(lnk);
        std::string name = pn_condition_get_name(error);
        if (name == qpid::amqp::error_conditions::NOT_FOUND) {
            throw qpid::messaging::NotFound(text);
        } else if (name == qpid::amqp::error_conditions::UNAUTHORIZED_ACCESS) {
            throw qpid::messaging::UnauthorizedAccess(text);
        } else {
            throw qpid::messaging::LinkError(text);
        }
    } else if ((pn_link_state(lnk) & IS_CLOSED) == IS_CLOSED) {
        throw qpid::messaging::LinkError("Link is not attached");
    }
}

void ConnectionContext::restartSession(boost::shared_ptr<SessionContext> s)
{
    if (s->error) return;
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
        if (i->second->capacity) {
            attach(s, i->second->receiver, i->second->capacity);
        } else {
            attach(s, i->second->receiver, (uint32_t) i->second->fetching);
        }
        i->second->verify();
        QPID_LOG(debug, id << " receiver " << i->first << " reattached");
    }
    wakeupDriver();
}

boost::shared_ptr<SessionContext> ConnectionContext::newSession(bool transactional, const std::string& n)
{
    boost::shared_ptr<SessionContext> session;
    std::string name = n.empty() ? qpid::framing::Uuid(true).str() : n;
    {
        sys::Monitor::ScopedLock l(lock);
        SessionMap::const_iterator i = sessions.find(name);
        if (i == sessions.end()) {
            session = boost::shared_ptr<SessionContext>(new SessionContext(connection));
            session->setName(name);
            pn_session_open(session->session);
            wakeupDriver();
            sessions[name] = session; // Add it now so it will be restarted if we reconnect in wait()
            while (pn_session_state(session->session) & PN_REMOTE_UNINIT) {
                wait();
            }
        } else {
            throw qpid::messaging::KeyError(std::string("Session already exists: ") + name);
        }

    }
    if (transactional) {        // Outside of lock
        startTxSession(session);
    }
    return session;
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
    sys::Monitor::ScopedLock l(lock);
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
        // PN_EOS either means we received a Close (which also means we've
        // consumed all the input), OR some Very Bad Thing happened and this
        // connection is toast.
        if (n == PN_EOS)
        {
            std::string error;
            if (checkTransportError(error)) {
                // "He's dead, Jim."
                QPID_LOG_CAT(error, network, id << " connection failed: " << error);
                transport->abort();
                return 0;
            } else {
                n = size;   // assume all consumed
            }
        }
        QPID_LOG_CAT(debug, network, id << " decoded " << n << " bytes from " << size)
        pn_transport_tick(engine, qpid::sys::Duration::FromEpoch() / qpid::sys::TIME_MSEC);
        lock.notifyAll();
        return n;
    } else if (n == PN_ERR) {
        std::string error;
        checkTransportError(error);
        QPID_LOG_CAT(error, network, id << " connection error: " << error);
        transport->abort();
        return 0;
    } else {
        return 0;
    }

}
std::size_t ConnectionContext::encodePlain(char* buffer, std::size_t size)
{
    sys::Monitor::ScopedLock l(lock);
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
        if (notifyOnWrite) lock.notifyAll();
        return n;
    } else if (n == PN_ERR) {
        std::string error;
        checkTransportError(error);
        QPID_LOG_CAT(error, network, id << " connection error: " << error);
        transport->abort();
        return 0;
    } else if (n == PN_EOS) {
        haveOutput = false;
        // Normal close, or error?
        std::string error;
        if (checkTransportError(error)) {
            QPID_LOG_CAT(error, network, id << " connection failed: " << error);
            transport->abort();
        }
        return 0;
    } else {
        haveOutput = false;
        return 0;
    }
}
bool ConnectionContext::canEncodePlain()
{
    sys::Monitor::ScopedLock l(lock);
    pn_transport_tick(engine, qpid::sys::Duration::FromEpoch() / qpid::sys::TIME_MSEC);
    return haveOutput && state == CONNECTED;
}
void ConnectionContext::closed()
{
    sys::Monitor::ScopedLock l(lock);
    state = DISCONNECTED;
    lock.notifyAll();
}
void ConnectionContext::opened()
{
    sys::Monitor::ScopedLock l(lock);
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
    return get_error(connection, engine);
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

const qpid::messaging::ConnectionOptions* ConnectionContext::getOptions()
{
    return this;
}

std::size_t ConnectionContext::decode(const char* buffer, std::size_t size)
{
    sys::Monitor::ScopedLock l(lock);
    size_t decoded = 0;
    try {
        if (sasl.get() && !sasl->authenticated()) {
            decoded = sasl->decode(buffer, size);
            if (!sasl->authenticated()) return decoded;
        }
        if (decoded < size) {
            if (sasl.get() && sasl->getSecurityLayer()) decoded += sasl->getSecurityLayer()->decode(buffer+decoded, size-decoded);
            else decoded += decodePlain(buffer+decoded, size-decoded);
        }
    } catch (const AuthenticationFailure&) {
        transport->close();
    }
    return decoded;
}
std::size_t ConnectionContext::encode(char* buffer, std::size_t size)
{
    sys::Monitor::ScopedLock l(lock);
    size_t encoded = 0;
    try {
        if (sasl.get() && sasl->canEncode()) {
            encoded += sasl->encode(buffer, size);
            if (!sasl->authenticated()) return encoded;
        }
        if (encoded < size) {
            if (sasl.get() && sasl->getSecurityLayer()) encoded += sasl->getSecurityLayer()->encode(buffer+encoded, size-encoded);
            else encoded += encodePlain(buffer+encoded, size-encoded);
        }
    } catch (const AuthenticationFailure&) {
        transport->close();
    }
    return encoded;
}
bool ConnectionContext::canEncode()
{
    sys::Monitor::ScopedLock l(lock);
    if (sasl.get()) {
        try {
            if (sasl->canEncode()) return true;
            else if (!sasl->authenticated()) return false;
            else if (sasl->getSecurityLayer()) return sasl->getSecurityLayer()->canEncode();
        } catch (const AuthenticationFailure&) {
            transport->close();
            return false;
        }
    }
    return canEncodePlain();
}

namespace {
const std::string CLIENT_PROCESS_NAME("qpid.client_process");
const std::string CLIENT_PID("qpid.client_pid");
const std::string CLIENT_PPID("qpid.client_ppid");
}
void ConnectionContext::setProperties()
{
    PnData data(pn_connection_properties(connection));
    pn_data_put_map(data.data);
    pn_data_enter(data.data);
    data.putSymbol(CLIENT_PROCESS_NAME);
    data.putSymbol(sys::SystemInfo::getProcessName());
    data.putSymbol(CLIENT_PID);
    data.put(int32_t(sys::SystemInfo::getProcessId()));
    data.putSymbol(CLIENT_PPID);
    data.put(int32_t(sys::SystemInfo::getParentProcessId()));
    for (Variant::Map::const_iterator i = properties.begin(); i != properties.end(); ++i)
    {
        data.putSymbol(i->first);
        data.put(i->second);
    }
    pn_data_exit(data.data);
}

const qpid::sys::SecuritySettings* ConnectionContext::getTransportSecuritySettings()
{
    return transport ?  transport->getSecuritySettings() : 0;
}

void ConnectionContext::open()
{
    sys::Monitor::ScopedLock l(lock);
    if (state != DISCONNECTED) throw qpid::messaging::ConnectionError("Connection was already opened!");
    if (!driver) driver = DriverImpl::getDefault();
    QPID_LOG(info, "Starting connection to " << fullUrl);
    autoconnect();
}


namespace {
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

void throwConnectFail(const Url& url, const std::string& msg) {
    throw qpid::messaging::TransportFailure(
        Msg() << "Connect failed to " << url << ": " << msg);
}

void ConnectionContext::autoconnect()
{
    qpid::sys::AbsTime started(qpid::sys::now());
    for (double i = minReconnectInterval; !tryConnectUrl(fullUrl); i = std::min(i*2, maxReconnectInterval)) {
        if (!ConnectionOptions::reconnect) throwConnectFail(fullUrl, "Reconnect disabled");
        if (limit >= 0 && retries++ >= limit) throwConnectFail(fullUrl, "Exceeded retries");
        if (expired(started, timeout)) throwConnectFail(fullUrl, "Exceeded timeout");
        QPID_LOG(debug, "Connection retry in " << i*1000*1000 << " microseconds to"
                 << fullUrl);
        qpid::sys::usleep(int64_t(i*1000*1000)); // Sleep in microseconds.
    }
    retries = 0;
}

void ConnectionContext::reconnect(const Url& url) {
    QPID_LOG(notice, "Reconnecting to " << url);
    sys::Monitor::ScopedLock l(lock);
    if (state != DISCONNECTED) throw qpid::messaging::ConnectionError("Connection was already opened!");
    if (!driver) driver = DriverImpl::getDefault();
    reset();
    if (!tryConnectUrl(url)) throwConnectFail(url, "Failed to reconnect");
    QPID_LOG(notice, "Reconnected to " << currentUrl);
}

void ConnectionContext::reconnect(const std::string& url) { reconnect(Url(url)); }

void ConnectionContext::reconnect() { reconnect(fullUrl); }

void ConnectionContext::waitNoReconnect() {
    if (!checkDisconnected()) {
        lock.wait();
        checkDisconnected();
    }
}

// Try to connect to a URL, i.e. try to connect to each of its addresses in turn
// till one succeeds or they all fail.
// @return true if we connect successfully
bool ConnectionContext::tryConnectUrl(const Url& url)
{
    if (url.getUser().size()) username = url.getUser();
    if (url.getPass().size()) password = url.getPass();

    for (Url::const_iterator i = url.begin(); i != url.end(); ++i) {
        QPID_LOG(info, "Connecting to " << *i);
        if (tryConnectAddr(*i) && tryOpenAddr(*i)) {
            QPID_LOG(info, "Connected to " << *i);
            return true;
        }
    }
    return false;
}

// Try to open an AMQP protocol connection on an address, after we have already
// established a transport connect (see tryConnectAddr below)
// @return true if the AMQP connection is succesfully opened.
bool ConnectionContext::tryOpenAddr(const qpid::Address& addr) {
    currentUrl = Url(addr);
    if (sasl.get()) {
        wakeupDriver();
        while (!sasl->authenticated() && state != DISCONNECTED) {
            QPID_LOG(debug, id << " Waiting to be authenticated...");
            waitNoReconnect();
        }
        if (state == DISCONNECTED) return false;
        QPID_LOG(debug, id << " Authenticated");
    }

    QPID_LOG(debug, id << " Opening...");
    pn_connection_open(connection);
    wakeupDriver(); //want to write
    while ((pn_connection_state(connection) & PN_REMOTE_UNINIT) &&
           state != DISCONNECTED)
        waitNoReconnect();
    if (state == DISCONNECTED) return false;
    if (!(pn_connection_state(connection) & PN_REMOTE_ACTIVE)) {
        throw qpid::messaging::ConnectionError("Failed to open connection");
    }

    // Connection open - check for idle timeout from the remote and start a
    // periodic tick to monitor for idle connections
    pn_timestamp_t remote = pn_transport_get_remote_idle_timeout(engine);
    pn_timestamp_t local = pn_transport_get_idle_timeout(engine);
    uint64_t shortest = ((remote && local)
                         ? std::min(remote, local)
                         : (remote) ? remote : local);
    if (shortest) {
        // send an idle frame at least twice before timeout
        shortest = (shortest + 1)/2;
        qpid::sys::Duration d(shortest * qpid::sys::TIME_MSEC);
        ticker = boost::intrusive_ptr<qpid::sys::TimerTask>(new ConnectionTickerTask(d, driver->getTimer(), *this));
        driver->getTimer().add(ticker);
        QPID_LOG(debug, id << " AMQP 1.0 idle-timeout set:"
                 << " local=" << pn_transport_get_idle_timeout(engine)
                 << " remote=" << pn_transport_get_remote_idle_timeout(engine));
    }

    QPID_LOG(debug, id << " Opened");

    return restartSessions();
}

std::string ConnectionContext::getUrl() const
{
    sys::Monitor::ScopedLock l(lock);
    return (state == CONNECTED) ? currentUrl.str() : std::string();
}

// Try to establish a transport connect to an individual address (typically a
// TCP host:port)
// @return true if we succeed in connecting.
bool ConnectionContext::tryConnectAddr(const qpid::Address& address)
{
    transport = driver->getTransport(address.protocol, *this);
    id = boost::lexical_cast<std::string>(address);
    if (useSasl()) {
        sasl = std::auto_ptr<Sasl>(new Sasl(id, *this, address.host));
    }
    state = CONNECTING;
    try {
        QPID_LOG(debug, id << " Connecting ...");
        transport->connect(address.host, boost::lexical_cast<std::string>(address.port));
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

void ConnectionContext::initSecurityLayer(qpid::sys::SecurityLayer& s)
{
    s.init(&codecAdapter);
}

ConnectionContext::CodecAdapter::CodecAdapter(ConnectionContext& c) : context(c) {}
std::size_t ConnectionContext::CodecAdapter::decode(const char* buffer, std::size_t size)
{
    return context.decodePlain(buffer, size);
}
std::size_t ConnectionContext::CodecAdapter::encode(char* buffer, std::size_t size)
{
    return context.encodePlain(buffer, size);
}
bool ConnectionContext::CodecAdapter::canEncode()
{
    return context.canEncodePlain();
}

void ConnectionContext::startTxSession(boost::shared_ptr<SessionContext> session) {
    try {
        QPID_LOG(debug, id << " attaching transaction for " << session->getName());
        boost::shared_ptr<Transaction> tx(new Transaction(session->session));
        session->transaction = tx;
        attach(session, boost::shared_ptr<SenderContext>(tx));
        tx->declare(boost::bind(&ConnectionContext::send, this, _1, _2, _3, _4, _5), session);
    } catch (const Exception& e) {
        throw TransactionError(Msg() << "Cannot start transaction: " << e.what());
    }
}

void ConnectionContext::discharge(boost::shared_ptr<SessionContext> session, bool fail) {
    {
        sys::Monitor::ScopedLock l(lock);
        checkClosed(session);
        if (!session->transaction)
            throw TransactionError("No Transaction");
        Transaction::SendFunction sendFn = boost::bind(
            &ConnectionContext::sendLH, this, _1, _2, _3, _4, _5, boost::ref(l));
        syncLH(session, boost::ref(l)); // Sync to make sure all tx transfers have been received.
        session->transaction->discharge(sendFn, session, fail);
        session->transaction->declare(sendFn, session);
    }
}

void ConnectionContext::commit(boost::shared_ptr<SessionContext> session) {
    discharge(session, false);
}

void ConnectionContext::rollback(boost::shared_ptr<SessionContext> session) {
    discharge(session, true);
}


// setup the transport and connection objects:
void ConnectionContext::configureConnection()
{
    pn_connection_set_container(connection, identifier.c_str());
    setProperties();
    if (heartbeat) {
        // fail an idle connection at 2 x heartbeat (in msecs)
        pn_transport_set_idle_timeout(engine, heartbeat*2*1000);
    }

    bool enableTrace(false);
    QPID_LOG_TEST_CAT(trace, protocol, enableTrace);
    if (enableTrace) {
        pn_transport_trace(engine, PN_TRACE_FRM);
        set_tracer(engine, this);
    }

    int err = pn_transport_bind(engine, connection);
    if (err)
        QPID_LOG(error, id << " Error binding connection and transport: " << err);
}


// check for failures of the transport:
bool ConnectionContext::checkTransportError(std::string& text)
{
    std::stringstream info;

#ifdef USE_PROTON_TRANSPORT_CONDITION
    pn_condition_t* tcondition = pn_transport_condition(engine);
    if (pn_condition_is_set(tcondition))
        info << get_error_string(tcondition, "transport error", ": ");
#else
    pn_error_t* terror = pn_transport_error(engine);
    if (terror) info << "transport error " << pn_error_text(terror) << " [" << terror << "]";
#endif

    text = info.str();
    return !text.empty();
}
}}} // namespace qpid::messaging::amqp
