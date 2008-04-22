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
#include "ConnectionFactory.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/amqp_0_10/Connection.h"
#include "PreviewConnectionCodec.h"

namespace qpid {
namespace broker {

using framing::ProtocolVersion;

ConnectionFactory::ConnectionFactory(Broker& b) : broker(b) {}

ConnectionFactory::~ConnectionFactory() {}

sys::ConnectionCodec*
ConnectionFactory::create(ProtocolVersion v, sys::OutputControl& out, const std::string& id) {
    if (v == ProtocolVersion(99, 0)) 
        return new PreviewConnectionCodec(out, broker, id);
    if (v == ProtocolVersion(0, 10))
        return new amqp_0_10::Connection(out, broker, id);
    return 0;
}

sys::ConnectionCodec*
ConnectionFactory::create(sys::OutputControl& out, const std::string& id) {
    // used to create connections from one broker to another
    return new amqp_0_10::Connection(out, broker, id, true);
}

}} // namespace qpid::broker
