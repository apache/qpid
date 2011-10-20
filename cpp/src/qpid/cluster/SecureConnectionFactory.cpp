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
#include "qpid/cluster/SecureConnectionFactory.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/cluster/ConnectionCodec.h"
#include "qpid/broker/SecureConnection.h"
#include "qpid/sys/SecuritySettings.h"
#include "qpid/log/Statement.h"
#include <memory>


namespace qpid {
namespace cluster {

using framing::ProtocolVersion;
using qpid::sys::SecuritySettings;
using qpid::broker::SecureConnection;

typedef std::auto_ptr<qpid::broker::SecureConnection> SecureConnectionPtr;
typedef std::auto_ptr<qpid::sys::ConnectionCodec> CodecPtr;

SecureConnectionFactory::SecureConnectionFactory(CodecFactoryPtr f) : codecFactory(f) {
}

sys::ConnectionCodec*
SecureConnectionFactory::create(ProtocolVersion v, sys::OutputControl& out, const std::string& id,
                                const SecuritySettings& external) {
    CodecPtr codec(codecFactory->create(v, out, id, external));
    ConnectionCodec* clusterCodec = dynamic_cast<qpid::cluster::ConnectionCodec*>(codec.get());
    if (clusterCodec) {
        SecureConnectionPtr sc(new SecureConnection());
        clusterCodec->setSecureConnection(sc.get());
        sc->setCodec(codec);
        return sc.release();
    }
    return 0;
}

sys::ConnectionCodec*
SecureConnectionFactory::create(sys::OutputControl& out, const std::string& id,
                                const SecuritySettings& external) {
    // used to create connections from one broker to another
    CodecPtr codec(codecFactory->create(out, id, external));
    ConnectionCodec* clusterCodec = dynamic_cast<qpid::cluster::ConnectionCodec*>(codec.get());
    if (clusterCodec) {
        SecureConnectionPtr sc(new SecureConnection());
        clusterCodec->setSecureConnection(sc.get());
        sc->setCodec(codec);
        return sc.release();
    }
    return 0;
}


}} // namespace qpid::cluster
