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
#include "qpid/messaging/Session.h"
#include "qpid/client/PrivateImplRef.h"
#include "qpid/log/Statement.h"
#include <boost/intrusive_ptr.hpp>

namespace qpid {
namespace client {
namespace amqp0_10 {

using qpid::messaging::Variant;
using namespace qpid::sys;

template <class T> void setIfFound(const Variant::Map& map, const std::string& key, T& value)
{
    Variant::Map::const_iterator i = map.find(key);
    if (i != map.end()) {
        value = (T) i->second;
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
}

ConnectionImpl::ConnectionImpl(const std::string& u, const Variant::Map& options) : 
    url(u), reconnectionEnabled(true), timeout(-1),
    minRetryInterval(1), maxRetryInterval(30)
{
    QPID_LOG(debug, "Opening connection to " << url << " with " << options);
    convert(options, settings);
    setIfFound(options, "reconnection-enabled", reconnectionEnabled);
    setIfFound(options, "reconnection-timeout", timeout);
    setIfFound(options, "min-retry-interval", minRetryInterval);
    setIfFound(options, "max-retry-interval", maxRetryInterval);
    connection.open(url, settings);
}

void ConnectionImpl::close()
{
    qpid::sys::Mutex::ScopedLock l(lock);
    connection.close();
}

boost::intrusive_ptr<SessionImpl> getImplPtr(qpid::messaging::Session& session)
{
    return boost::dynamic_pointer_cast<SessionImpl>(
        qpid::client::PrivateImplRef<qpid::messaging::Session>::get(session)
    );
}

void ConnectionImpl::closed(SessionImpl& s)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    for (Sessions::iterator i = sessions.begin(); i != sessions.end(); ++i) {
        if (getImplPtr(*i).get() == &s) {
            sessions.erase(i);
            break;
        }
    }
}

qpid::messaging::Session ConnectionImpl::newSession()
{
    qpid::messaging::Session impl(new SessionImpl(*this));
    {
        qpid::sys::Mutex::ScopedLock l(lock);
        sessions.push_back(impl);
    }
    try {
        getImplPtr(impl)->setSession(connection.newSession());
    } catch (const TransportFailure&) {
        reconnect();
    }
    return impl;
}

void ConnectionImpl::reconnect()
{
    AbsTime start = now();
    ScopedLock<Semaphore> l(semaphore);
    if (!connection.isOpen()) connect(start);
}

bool expired(const AbsTime& start, int timeout)
{
    if (timeout == 0) return true;
    if (timeout < 0) return false;
    Duration used(start, now());
    Duration allowed = timeout * TIME_SEC;
    return allowed > used;
}

void ConnectionImpl::connect(const AbsTime& started)
{
    for (int i = minRetryInterval; !tryConnect(); i = std::min(i * 2, maxRetryInterval)) {
        if (expired(started, timeout)) throw TransportFailure();
        else qpid::sys::sleep(i);
    }
}

bool ConnectionImpl::tryConnect()
{
    if (tryConnect(url) || tryConnect(connection.getKnownBrokers())) {
        return resetSessions();
    } else {
        return false;
    }
}

bool ConnectionImpl::tryConnect(const Url& u)
{
    try {
        QPID_LOG(info, "Trying to connect to " << url << "...");                
        connection.open(u, settings);
        return true;
    } catch (const Exception& e) {
        //TODO: need to fix timeout on open so that it throws TransportFailure
        QPID_LOG(info, "Failed to connect to " << u << ": " << e.what());                
    }
    return false;
}

bool ConnectionImpl::tryConnect(const std::vector<Url>& urls)
{
    for (std::vector<Url>::const_iterator i = urls.begin(); i != urls.end(); ++i) {
        if (tryConnect(*i)) return true;
    }
    return false;
}

bool ConnectionImpl::resetSessions()
{
    try {
        qpid::sys::Mutex::ScopedLock l(lock);
        for (Sessions::iterator i = sessions.begin(); i != sessions.end(); ++i) {
            getImplPtr(*i)->setSession(connection.newSession());
        }
        return true;
    } catch (const TransportFailure&) {
        QPID_LOG(debug, "Connection failed while re-inialising sessions");
        return false;
    }
}

}}} // namespace qpid::client::amqp0_10
