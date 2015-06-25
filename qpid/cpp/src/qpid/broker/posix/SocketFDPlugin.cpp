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

#include "qpid/Options.h"
#include "qpid/Plugin.h"
#include "qpid/broker/Broker.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/AsynchIO.h"
#include "qpid/sys/posix/BSDSocket.h"
#include "qpid/sys/SocketTransport.h"

#include <vector>

#include <sys/stat.h>

namespace qpid {
namespace sys {

struct SocketOptions : public Options {
    std::vector<int> socketFds;

    SocketOptions() :
    socketFds(0)
    {
        addOptions()
        ("socket-fd", optValue(socketFds, "FD"), "File descriptor for tcp listening socket");
    }
};

bool isSocket(int fd)
{
    if (fd < 0 ) return false;

    struct ::stat st_fd;
    if (::fstat(fd, &st_fd) < 0) return false;

    return S_ISSOCK(st_fd.st_mode);
}

// Static instance to initialise plugin
static class SocketFDPlugin : public Plugin {
    SocketOptions options;

    Options* getOptions() { return &options; }

    void earlyInitialize(Target&) {
    }

    void initialize(Target& target) {
        broker::Broker* broker = dynamic_cast<broker::Broker*>(&target);
        // Only provide to a Broker
        if (broker) {
            if (!options.socketFds.empty()) {
                SocketAcceptor* sa = new SocketAcceptor(broker->getTcpNoDelay(), false, broker->getMaxNegotiateTime(), broker->getTimer());
                for (unsigned i = 0; i<options.socketFds.size(); ++i) {
                    int fd = options.socketFds[i];
                    if (!isSocket(fd)) {
                        QPID_LOG(error, "Imported socket fd " << fd << ": isn't a socket");
                        continue;
                    }
                    Socket* s = new BSDSocket(fd);
                    sa->addListener(s);
                    QPID_LOG(notice, "Listening on imported socket: " << s->getLocalAddress());
                }
                broker->registerTransport("socket", TransportAcceptor::shared_ptr(sa), TransportConnector::shared_ptr(), 0);
            } else {
                QPID_LOG(debug, "No Socket fd specified");
            }
        }
    }
} socketFdPlugin;

}} // namespace qpid::sys
