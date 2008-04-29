#ifndef QPID_CLIENT_CONNECTIONSETTINGS_H
#define QPID_CLIENT_CONNECTIONSETTINGS_H

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

#include "qpid/Options.h"
#include "qpid/log/Options.h"
#include "qpid/Url.h"
#include "qpid/log/Logger.h"
#include "qpid/sys/Socket.h"

#include <iostream>
#include <exception>

namespace qpid {
namespace client {

/**
 * Used to hold seetings for a connection (and parse these from
 * command line oprions etc as a convenience).
 */
struct ConnectionSettings : qpid::Options, qpid::sys::Socket::Configuration
{
    ConnectionSettings();
    virtual ~ConnectionSettings();

    /**
     * Applies any tcp specific options to the sockets file descriptor
     */
    virtual void configurePosixTcpSocket(int fd) const;

    /** 
     * Parse options from command line arguments (will throw exception
     * if arguments cannot be parsed).
     */
    void parse(int argc, char** argv);

    std::string host;
    uint16_t port;
    std::string virtualhost;
    std::string clientid;
    std::string username;
    std::string password;
    std::string mechanism;
    std::string locale;
    uint16_t heartbeat;
    uint16_t maxChannels;
    uint16_t maxFrameSize;
    uint bounds;
    bool tcpNoDelay;

    log::Options log;
};

}} // namespace qpid::client

#endif  /*!QPID_CLIENT_CONNECTIONSETTINGS_H*/
