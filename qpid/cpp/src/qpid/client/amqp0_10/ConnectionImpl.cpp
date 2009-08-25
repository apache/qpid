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
#include "qpid/client/ConnectionSettings.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace client {
namespace amqp0_10 {

using qpid::messaging::Variant;

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

ConnectionImpl::ConnectionImpl(const std::string& url, const Variant::Map& options)
{
    QPID_LOG(debug, "Opening connection to " << url << " with " << options);
    Url u(url);
    ConnectionSettings settings;
    convert(options, settings);
    connection.open(u, settings);
}

void ConnectionImpl::close()
{
    connection.close();
}

qpid::messaging::Session ConnectionImpl::newSession()
{
    qpid::messaging::Session impl(new SessionImpl(connection.newSession()));
    return impl;
}

}}} // namespace qpid::client::amqp0_10
