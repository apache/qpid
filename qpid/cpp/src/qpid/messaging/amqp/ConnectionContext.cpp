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
#include "Decoder.h"
#include "ReceiverContext.h"
#include "SenderContext.h"
#include "SessionContext.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/messaging/Duration.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/MessageImpl.h"
#include "qpid/framing/Uuid.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Time.h"
#include <vector>

namespace qpid {
namespace messaging {
namespace amqp {

/*
ssize_t intercept_input(pn_transport_t *transport, char *bytes, size_t available, void* context, pn_input_fn_t *next)
{
    ConnectionContext* connection = reinterpret_cast<ConnectionContext*>(context);
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(connection->lock);
    ssize_t result = next(transport, bytes, available);
    connection->lock.notifyAll();
    return result;
}
ssize_t intercept_output(pn_transport_t *transport, char *bytes, size_t size, void* context, pn_output_fn_t *next)
{
    ConnectionContext* connection = reinterpret_cast<ConnectionContext*>(context);
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(connection->lock);
    return next(transport, bytes, size);
}
time_t intercept_tick(pn_transport_t *transport, time_t now, void* context, pn_tick_fn_t *next)
{
    ConnectionContext* connection = reinterpret_cast<ConnectionContext*>(context);
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(connection->lock);
    return next(transport, now);
}
*/

/*
void callback(pn_selectable_t* s)
{
    ConnectionContext* context = reinterpret_cast<ConnectionContext*>(pn_selectable_context(s));

    if (context->read()) {
        context->doInput();
    }
    if (context->doOutput()) {
        context->write();
    }
}
*/

ConnectionContext::ConnectionContext(const std::string& u, const qpid::types::Variant::Map& o)
    : url(u),
      driver(pn_driver()),//TODO: allow driver to be shared
      socket(0),
      connection(0),
      //transport(0),
      options(o),
      waitingToWrite(false),
      active(false) {}

ConnectionContext::~ConnectionContext()
{
    if (active.boolCompareAndSwap(true, false)) {
        wakeupDriver();
        driverThread.join();//if active was false, then the caller of
                            //close() will have joined the driver
                            //thread already
    }
    pn_connector_destroy(socket);
    pn_driver_destroy(driver);
    //What else am I responsible for deleting?
}

void ConnectionContext::open()
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    //TODO: check not already open
    std::string host = "localhost";
    std::string port = "5672";

    for (Url::const_iterator i = url.begin(); i != url.end(); ++i) {
        std::stringstream port;
        port << i->port;
        socket = pn_connector(driver, i->host.c_str(), port.str().c_str(), this);
        if (socket) break;
    }

    if (socket) {
        pn_sasl_t *sasl = pn_connector_sasl(socket);

        std::string user = url.getUser();
        if (user.size()) {//TODO: proper SASL
            pn_sasl_plain(sasl, user.c_str(), url.getPass().c_str());
        } else {
            pn_sasl_mechanisms(sasl, "ANONYMOUS");
            pn_sasl_client(sasl);
        }

        connection = pn_connector_connection(socket);
        pn_connection_open(connection);
        active = true;
        driverThread = qpid::sys::Thread(this);
        //wait for open state
        while (pn_sasl_outcome(sasl) == PN_SASL_NONE ||
               !(pn_connection_state(connection) & (PN_LOCAL_ACTIVE | PN_REMOTE_UNINIT))) {
            wait();
        }
        if (pn_sasl_outcome(sasl) != PN_SASL_OK) {
            throw qpid::messaging::ConnectionError("Authentication failed!");
        }
    } else {
        //set error state
    }
}

void ConnectionContext::run()
{
    while (active.get()) {
        pn_driver_wait(driver);
        for (pn_connector_t* c = pn_driver_connector(driver); c; c = pn_driver_connector(driver)) {
            ConnectionContext* context = reinterpret_cast<ConnectionContext*>(pn_connector_context(c));
            qpid::sys::ScopedLock<qpid::sys::Monitor> l(context->lock);
            pn_connector_process(c);
            context->lock.notifyAll();
        }
    }
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
    {
        qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
        if (!(pn_connection_state(connection) & PN_LOCAL_CLOSED)) {
            for (SessionMap::iterator i = sessions.begin(); i != sessions.end(); ++i){
                pn_session_close(i->second->session);
            }
            sessions.clear();
            pn_connection_close(connection);
            wakeupDriver();
            //wait for close to be confirmed by peer?
            while (!(pn_connection_state(connection) & PN_REMOTE_CLOSED)) {
                wait();
            }
        }
    }
    if (active.boolCompareAndSwap(true, false)) {
        wakeupDriver();
    }
    driverThread.join();
}

bool ConnectionContext::fetch(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<ReceiverContext> lnk, qpid::messaging::Message& message, qpid::messaging::Duration timeout)
{
    if (!lnk->capacity) {
        pn_flow(lnk->receiver, 1);
        wakeupDriver();
    }
    if (get(ssn, lnk, message, timeout)) {
        if (lnk->capacity) {
            pn_flow(lnk->receiver, 1);//TODO: is this the right approach?
        }
        return true;
    } else {
        //TODO: flush
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
    while (until > qpid::sys::now()) {
        qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
        pn_delivery_t* current = pn_current((pn_link_t*) lnk->receiver);
        if (current) {
            //TODO: can we avoid copying here?
            std::vector<char> data;
            data.resize(pn_pending(current));
            ssize_t read = pn_recv(lnk->receiver, &data[0], data.size());
            MessageDataDecoder decoder(MessageImplAccess::get(message));
            decoder.decode(&data[0], read);
            MessageImplAccess::get(message).setInternalId(ssn->record(current));
            pn_advance(lnk->receiver);
            return true;
        } else {
            wait();
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
    pn_set_target((pn_link_t*) lnk->sender, lnk->getTarget().c_str());
    attach(ssn->session, (pn_link_t*) lnk->sender);
}

void ConnectionContext::attach(boost::shared_ptr<SessionContext> ssn, boost::shared_ptr<ReceiverContext> lnk)
{
    pn_set_source((pn_link_t*) lnk->receiver, lnk->getSource().c_str());
    attach(ssn->session, (pn_link_t*) lnk->receiver, lnk->capacity);
}

void ConnectionContext::attach(pn_session_t* /*session*/, pn_link_t* link, int credit)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    pn_link_open(link);
    if (credit) pn_flow(link, credit);
    wakeupDriver();
    while (!(pn_link_state(link) & PN_REMOTE_UNINIT)) wait();
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

/**
 * Expects lock to be held by caller
 */
void ConnectionContext::wakeupDriver()
{
    pn_driver_wakeup(driver);
    waitingToWrite = true;
    QPID_LOG(debug, "wakeupDriver()");
}

void ConnectionContext::wait()
{
    lock.wait();
}
/*
bool ConnectionContext::read()
{
    int r = pn_selectable_recv(socket, inputBuffer.position(), inputBuffer.capacity());
    if (r > 0) {
        inputBuffer.advance(r);
        return true;
    } else if (r == 0) {
        //closed
        return false;
    } else {//i.e. r < 0
        return false;
    }
}

void ConnectionContext::write()
{
    QPID_LOG(debug, "write() " << outputBuffer.available() << " bytes available");
    int w = pn_selectable_send(socket, outputBuffer.start(), outputBuffer.available());
    if (w < 0) {
        //io error
     } else {
        outputBuffer.consume(w);
        QPID_LOG(debug, "Wrote " << w << " bytes");
     }
}

void ConnectionContext::doInput()
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    ssize_t n = pn_input(transport, inputBuffer.start(), inputBuffer.available());
    if (n > 0) {
        inputBuffer.consume(n);
        lock.notifyAll();
    } else if (n < 0) {
        QPID_LOG(error, "Error on input. Engine returned code: " << n);
    }
}

bool ConnectionContext::doOutput()
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    ssize_t n = pn_output(transport, outputBuffer.position(), outputBuffer.capacity());
    if (n < 0) {
        //engine error
    } else {
        outputBuffer.advance(n);
    }
    lock.notifyAll();
    if (!outputBuffer.available() && !waitingToWrite) {
        pn_selectable_flags(socket, PN_SEL_RD);
    }
    return outputBuffer.available();
}

time_t ConnectionContext::doTick(time_t now)
{
    qpid::sys::ScopedLock<qpid::sys::Monitor> l(lock);
    waitingToWrite = false;
    return pn_tick(transport, now);
}
*/

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
        QPID_LOG(debug, "Session " << name << " begun");
        wakeupDriver();
        while (pn_session_state(s->session) & PN_REMOTE_UNINIT) wait();
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
    options[name] = value;
}

std::string ConnectionContext::getAuthenticatedUsername()
{
    return std::string();//TODO
}

ConnectionContext::Buffer::Buffer(size_t s) : data(new char[s]), size(s), used(0) {}
ConnectionContext::Buffer::~Buffer() { delete[](data); }
char* ConnectionContext::Buffer::position()
{
    return data + used;
}

size_t ConnectionContext::Buffer::available()
{
    return used;
}

size_t ConnectionContext::Buffer::capacity()
{
    return size - used;
}

char* ConnectionContext::Buffer::start()
{
    return data;
}

void ConnectionContext::Buffer::advance(size_t bytes)
{
    used += bytes;
}

void ConnectionContext::Buffer::consume(size_t bytes)
{
    memmove(data, data + bytes, size - bytes);
    used -= bytes;
}


}}} // namespace qpid::messaging::amqp
