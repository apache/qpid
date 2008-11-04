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
#include "FailoverManager.h"
#include "qpid/Exception.h"
#include "qpid/log/Statement.h"


namespace qpid {
namespace client {

using qpid::sys::Monitor;

FailoverManager::FailoverManager(const ConnectionSettings& s) : settings(s), state(IDLE) {}

void FailoverManager::execute(Command& c)
{
    bool retry = false;
    bool completed = false;
    while (!completed) {
        try {
            AsyncSession session = connect().newSession();
            c.execute(session, retry);
            session.sync();//TODO: shouldn't be required, but seems there is a bug in session
            session.close();
            completed = true;
        } catch(const TransportFailure&) {
            retry = true;
        }            
    }
}

void FailoverManager::close()
{
    Monitor::ScopedLock l(lock);
    connection.close();
}

Connection& FailoverManager::connect(std::vector<Url> brokers)
{
    Monitor::ScopedLock l(lock);
    if (state == CANT_CONNECT) {
        state = IDLE;//retry
    }
    while (!connection.isOpen()) {
        if (state == CONNECTING) {
            lock.wait();
        } else if (state == CANT_CONNECT) {
            throw CannotConnectException("Cannot establish a connection");
        } else {
            state = CONNECTING;
            Connection c;
            attempt(c, settings, brokers.empty() ? connection.getKnownBrokers() : brokers);
            if (c.isOpen()) state = IDLE;
            else state = CANT_CONNECT;
            connection = c;
            lock.notifyAll();
        }
    }
    return connection;
}

Connection& FailoverManager::getConnection()
{
    Monitor::ScopedLock l(lock);
    return connection;
}

void FailoverManager::attempt(Connection& c, ConnectionSettings s, std::vector<Url> urls)
{
    Monitor::ScopedUnlock u(lock);
    if (urls.empty()) {
        attempt(c, s);
    } else {
        for (std::vector<Url>::const_iterator i = urls.begin(); i != urls.end() && !c.isOpen(); ++i) {
            for (Url::const_iterator j = i->begin(); j != i->end() && !c.isOpen(); ++j) {
                const TcpAddress* tcp = j->get<TcpAddress>();
                if (tcp) {
                    s.host = tcp->host;
                    s.port = tcp->port;
                    attempt(c, s);
                }
            }
        }
    }
}

void FailoverManager::attempt(Connection& c, ConnectionSettings s)
{
    try {
        c.open(s);
    } catch (const Exception& e) {
        QPID_LOG(info, "Could not connect to " << s.host << " on " << s.port << ": " << e.what()); 
    }
}


}} // namespace qpid::client
