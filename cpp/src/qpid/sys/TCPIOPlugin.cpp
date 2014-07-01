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

#include "qpid/sys/TransportFactory.h"

#include "qpid/Plugin.h"
#include "qpid/broker/Broker.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/AsynchIO.h"
#include "qpid/sys/Socket.h"
#include "qpid/sys/SocketTransport.h"

namespace qpid {
namespace sys {

// Static instance to initialise plugin
static class TCPIOPlugin : public Plugin {
    void earlyInitialize(Target&) {
    }

    void initialize(Target& target) {
        broker::Broker* broker = dynamic_cast<broker::Broker*>(&target);
        // Only provide to a Broker
        if (broker) {
            uint16_t port = broker->getPortOption();
            TransportAcceptor::shared_ptr ta;
            if (broker->shouldListen("tcp")) {
                SocketAcceptor* aa = new SocketAcceptor(broker->getTcpNoDelay(), false, broker->getMaxNegotiateTime(), broker->getTimer());
                ta.reset(aa);
                port = aa->listen(broker->getListenInterfaces(), port, broker->getConnectionBacklog(), &createSocket);
                if ( port!=0 ) {
                    QPID_LOG(notice, "Listening on TCP/TCP6 port " << port);
                }
            }

            TransportConnector::shared_ptr tc(new SocketConnector(broker->getTcpNoDelay(), false, broker->getMaxNegotiateTime(), broker->getTimer(), &createSocket));

            broker->registerTransport("tcp", ta, tc, port);
        }
    }
} tcpPlugin;

}} // namespace qpid::sys
