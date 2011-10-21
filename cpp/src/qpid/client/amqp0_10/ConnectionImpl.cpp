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
#include "ConnectionImpl.h"
#include "SessionImpl.h"
#include "SimpleUrlParser.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/messaging/Session.h"
#include "qpid/messaging/PrivateImplRef.h"
#include "qpid/framing/Uuid.h"
#include "qpid/log/Statement.h"
#include "qpid/Url.h"
#include <boost/intrusive_ptr.hpp>
#include <vector>
#include <sstream>

namespace qpid {
namespace client {
namespace amqp0_10 {

using qpid::types::Variant;
using qpid::types::VAR_LIST;
using qpid::framing::Uuid;

void convert(const Variant::List& from, std::vector<std::string>& to)
{
    for (Variant::List::const_iterator i = from.begin(); i != from.end(); ++i) {
        to.push_back(i->asString());
    }
}

template <class T> bool setIfFound(const Variant::Map& map, const std::string& key, T& value)
{
    Variant::Map::const_iterator i = map.find(key);
    if (i != map.end()) {
        value = (T) i->second;
        QPID_LOG(debug, "option " << key << " specified as " << i->second);
        return true;
    } else {
        return false;
    }
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
}

template <> bool setIfFound< std::vector<std::string> >(const Variant::Map& map,
                                            const std::string& key,
                                            std::vector<std::string>& value)
{
    Variant::Map::const_iterator i = map.find(key);
    if (i != map.end()) {
        value.clear();
        if (i->second.getType() == VAR_LIST) {
            convert(i->second.asList(), value);
        } else {
            value.push_back(i->second.asString());
        }
        QPID_LOG(debug, "option " << key << " specified as " << asString(value));
        return true;
    } else {
        return false;
    }
}

void convert(const Variant::Map& from, ConnectionSettings& to)
{
    setIfFound(from, "username", to.username);
    setIfFound(from, "password", to.password);
    setIfFound(from, "sasl-mechanism", to.mechanism);
    setIfFound(from, "sasl-service", to.service);
    setIfFound(from, "sasl-min-ssf", to.minSsf);
    setIfFound(from, "sasl-max-ssf", to.maxSsf);

    setIfFound(from, "heartbeat", to.heartbeat);
    setIfFound(from, "tcp-nodelay", to.tcpNoDelay);

    setIfFound(from, "locale", to.locale);
    setIfFound(from, "max-channels", to.maxChannels);
    setIfFound(from, "max-frame-size", to.maxFrameSize);
    setIfFound(from, "bounds", to.bounds);

    setIfFound(from, "transport", to.protocol);

    setIfFound(from, "ssl-cert-name", to.sslCertName);
}

ConnectionImpl::ConnectionImpl(const std::string& url, const Variant::Map& options) :
    reconnect(false), timeout(-1), limit(-1),
    minReconnectInterval(3), maxReconnectInterval(60),
    retries(0), reconnectOnLimitExceeded(true)
{
    setOptions(options);
    urls.insert(urls.begin(), url);
    QPID_LOG(debug, "Created connection " << url << " with " << options);
}

void ConnectionImpl::setOptions(const Variant::Map& options)
{
    sys::Mutex::ScopedLock l(lock);
    convert(options, settings);
    setIfFound(options, "reconnect", reconnect);
    setIfFound(options, "reconnect-timeout", timeout);
    setIfFound(options, "reconnect-limit", limit);
    int64_t reconnectInterval;
    if (setIfFound(options, "reconnect-interval", reconnectInterval)) {
        minReconnectInterval = maxReconnectInterval = reconnectInterval;
    } else {
        setIfFound(options, "reconnect-interval-min", minReconnectInterval);
        setIfFound(options, "reconnect-interval-max", maxReconnectInterval);
    }
    setIfFound(options, "reconnect-urls", urls);
    setIfFound(options, "x-reconnect-on-limit-exceeded", reconnectOnLimitExceeded);
}

void ConnectionImpl::setOption(const std::string& name, const Variant& value)
{
    Variant::Map options;
    options[name] = value;
    setOptions(options);
}


void ConnectionImpl::close()
{
    while(true) {
        messaging::Session session;
        {
            qpid::sys::Mutex::ScopedLock l(lock);
            if (sessions.empty()) break;
            session = sessions.begin()->second;
        }
        session.close();
    }
    detach();
}

void ConnectionImpl::detach()
{
    qpid::sys::Mutex::ScopedLock l(lock);
    connection.close();
}

bool ConnectionImpl::isOpen() const
{
    qpid::sys::Mutex::ScopedLock l(lock);
    return connection.isOpen();
}

boost::intrusive_ptr<SessionImpl> getImplPtr(qpid::messaging::Session& session)
{
    return boost::dynamic_pointer_cast<SessionImpl>(
        qpid::messaging::PrivateImplRef<qpid::messaging::Session>::get(session)
    );
}

void ConnectionImpl::closed(SessionImpl& s)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    for (Sessions::iterator i = sessions.begin(); i != sessions.end(); ++i) {
        if (getImplPtr(i->second).get() == &s) {
            sessions.erase(i);
            break;
        }
    }
}

qpid::messaging::Session ConnectionImpl::getSession(const std::string& name) const
{
    qpid::sys::Mutex::ScopedLock l(lock);
    Sessions::const_iterator i = sessions.find(name);
    if (i == sessions.end()) {
        throw qpid::messaging::KeyError("No such session: " + name);
    } else {
        return i->second;
    }
}

qpid::messaging::Session ConnectionImpl::newSession(bool transactional, const std::string& n)
{
    std::string name = n.empty() ? Uuid(true).str() : n;
    qpid::messaging::Session impl(new SessionImpl(*this, transactional));
    while (true) {
        try {
            getImplPtr(impl)->setSession(connection.newSession(name));
            qpid::sys::Mutex::ScopedLock l(lock);
            sessions[name] = impl;
            break;
        } catch (const qpid::TransportFailure&) {
            open();
        } catch (const qpid::SessionException& e) {
            throw qpid::messaging::SessionError(e.what());
        } catch (const std::exception& e) {
            throw qpid::messaging::MessagingException(e.what());
        }
    }
    return impl;
}

void ConnectionImpl::open()
{
    qpid::sys::AbsTime start = qpid::sys::now();
    qpid::sys::ScopedLock<qpid::sys::Semaphore> l(semaphore);
    try {
        if (!connection.isOpen()) connect(start);
    }
    catch (const types::Exception&) { throw; }
    catch (const qpid::Exception& e) { throw messaging::ConnectionError(e.what()); }
}

bool expired(const qpid::sys::AbsTime& start, int64_t timeout)
{
    if (timeout == 0) return true;
    if (timeout < 0) return false;
    qpid::sys::Duration used(start, qpid::sys::now());
    qpid::sys::Duration allowed = timeout * qpid::sys::TIME_SEC;
    return allowed < used;
}

void ConnectionImpl::connect(const qpid::sys::AbsTime& started)
{
    for (int64_t i = minReconnectInterval; !tryConnect(); i = std::min(i * 2, maxReconnectInterval)) {
        if (!reconnect) {
            throw qpid::messaging::TransportFailure("Failed to connect (reconnect disabled)");
        }
        if (limit >= 0 && retries++ >= limit) {
            throw qpid::messaging::TransportFailure("Failed to connect within reconnect limit");
        }
        if (expired(started, timeout)) {
            throw qpid::messaging::TransportFailure("Failed to connect within reconnect timeout");
        }
        else qpid::sys::sleep(i);
    }
    retries = 0;
}

void ConnectionImpl::mergeUrls(const std::vector<Url>& more, const sys::Mutex::ScopedLock&) {
    if (more.size()) {
        for (size_t i = 0; i < more.size(); ++i) {
            if (std::find(urls.begin(), urls.end(), more[i].str()) == urls.end()) {
                urls.push_back(more[i].str());
            }
        }
        QPID_LOG(debug, "Added known-hosts, reconnect-urls=" << asString(urls));
    }
}

bool ConnectionImpl::tryConnect()
{
    sys::Mutex::ScopedLock l(lock);
    for (std::vector<std::string>::const_iterator i = urls.begin(); i != urls.end(); ++i) {
        try {
            QPID_LOG(info, "Trying to connect to " << *i << "...");
            //TODO: when url support is more complete can avoid this test here
            if (i->find("amqp:") == 0) {
                Url url(*i);
                connection.open(url, settings);
            } else {
                SimpleUrlParser::parse(*i, settings);
                connection.open(settings);
            }
            QPID_LOG(info, "Connected to " << *i);
            mergeUrls(connection.getInitialBrokers(), l);
            return resetSessions(l);
        } catch (const qpid::ConnectionException& e) {
            //TODO: need to fix timeout on
            //qpid::client::Connection::open() so that it throws
            //TransportFailure rather than a ConnectionException
            QPID_LOG(info, "Failed to connect to " << *i << ": " << e.what());
        }
    }
    return false;
}

bool ConnectionImpl::resetSessions(const sys::Mutex::ScopedLock& )
{
    try {
        qpid::sys::Mutex::ScopedLock l(lock);
        for (Sessions::iterator i = sessions.begin(); i != sessions.end(); ++i) {
            getImplPtr(i->second)->setSession(connection.newSession(i->first));
        }
        return true;
    } catch (const qpid::TransportFailure&) {
        QPID_LOG(debug, "Connection failed while re-initialising sessions");
        return false;
    } catch (const qpid::framing::ResourceLimitExceededException& e) {
        if (reconnectOnLimitExceeded) {
            QPID_LOG(debug, "Detaching and reconnecting due to: " << e.what());
            detach();
            return false;
        } else {
            throw qpid::messaging::TargetCapacityExceeded(e.what());
        }
    }
}

bool ConnectionImpl::backoff()
{
    if (reconnectOnLimitExceeded) {
        detach();
        open();
        return true;
    } else {
        return false;
    }
}
std::string ConnectionImpl::getAuthenticatedUsername()
{
    return connection.getNegotiatedSettings().username;
}

}}} // namespace qpid::client::amqp0_10
