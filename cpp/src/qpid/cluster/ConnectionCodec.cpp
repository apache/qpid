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
#include "ConnectionCodec.h"
#include "Connection.h"
#include "ProxyInputHandler.h"
#include "qpid/broker/Connection.h"
#include "qpid/log/Statement.h"
#include "qpid/memory.h"

namespace qpid {
namespace cluster {

sys::ConnectionCodec*
ConnectionCodec::Factory::create(framing::ProtocolVersion v, sys::OutputControl& out, const std::string& id) {
    if (v == framing::ProtocolVersion(0, 10)) 
        return new ConnectionCodec(out, id, cluster);
    return 0;
}

sys::ConnectionCodec*
ConnectionCodec::Factory::create(sys::OutputControl& out, const std::string& id) {
    // FIXME aconway 2008-08-27: outbound connections need to be made
    // with proper qpid::client code for failover, get rid of this
    // broker-side hack.
    return next->create(out, id);
}

ConnectionCodec::ConnectionCodec(sys::OutputControl& out, const std::string& id, Cluster& cluster)
    : codec(out, id, false),
      interceptor(new Connection(cluster, codec, id, cluster.getSelf()))
{
    std::auto_ptr<sys::ConnectionInputHandler> ih(new ProxyInputHandler(interceptor));
    codec.setInputHandler(ih);
    cluster.insert(interceptor);
}

ConnectionCodec::~ConnectionCodec() {}

// ConnectionCodec functions delegate to the codecOutput
size_t ConnectionCodec::decode(const char* buffer, size_t size) {
    return interceptor->decode(buffer, size);
}

// FIXME aconway 2008-09-02: delegate to interceptor?
size_t ConnectionCodec::encode(const char* buffer, size_t size) { return codec.encode(buffer, size); }
bool ConnectionCodec::canEncode() { return codec.canEncode(); }
void ConnectionCodec::closed() { codec.closed(); }
bool ConnectionCodec::isClosed() const { return codec.isClosed(); }
framing::ProtocolVersion ConnectionCodec::getVersion() const { return codec.getVersion(); }

}} // namespace qpid::cluster
