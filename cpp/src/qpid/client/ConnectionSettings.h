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
#include "qpid/sys/Socket.h"

#include <iostream>
#include <exception>

namespace qpid {
namespace client {

/**
 * Settings for a Connection.
 */
struct ConnectionSettings : public sys::Socket::Configuration {

    ConnectionSettings();
    virtual ~ConnectionSettings();

    /**
     * Applies any tcp specific options to the sockets file descriptor
     */
    virtual void configurePosixTcpSocket(int fd) const;

    /**
     * The host (or ip address) to connect to (defaults to 'localhost').
     */
    std::string host;
    /**
     * The port to connect to (defaults to 5672).
     */
    uint16_t port;
    /**
     * Allows an AMQP 'virtual host' to be specified for the
     * connection.
     */
    std::string virtualhost;

    std::string clientid;
    /**
     * The username to use when authenticating the connection.
     */
    std::string username;
    /**
     * The password to use when authenticating the connection.
     */
    std::string password;
    /**
     * The SASL mechanism to use when authenticating the connection;
     * the options are currently PLAIN or ANONYMOUS.
     */
    std::string mechanism;
    /**
     * Allows a locale to be specified for the connection.
     */
    std::string locale;
    /**
     * Allows a heartbeat frequency to be specified (this feature is
     * not yet implemented).
     */
    uint16_t heartbeat;
    /**
     * The maximum number of channels that the client will request for
     * use on this connection.
     */
    uint16_t maxChannels;
    /**
     * The maximum frame size that the client will request for this
     * connection.
     */
    uint16_t maxFrameSize;
    /**
     * Allows the size of outgoing frames to be limited. The value
     * should be a mutliple of the maximum buffer size in use (which
     * is in turn set through the maxFrameSize setting above).
     */
    uint bounds;
    /**
     * If true, TCP_NODELAY will be set for the connection.
     */
    bool tcpNoDelay;
};

}} // namespace qpid::client

#endif  /*!QPID_CLIENT_CONNECTIONSETTINGS_H*/
