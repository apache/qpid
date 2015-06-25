#ifndef _sys_ssl_Socket_h
#define _sys_ssl_Socket_h

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

#include "qpid/sys/IOHandle.h"
#include "qpid/sys/posix/BSDSocket.h"
#include <nspr.h>

#include <string>

struct sockaddr;

namespace qpid {
namespace sys {

class Duration;

namespace ssl {

class SslSocket : public qpid::sys::BSDSocket
{
public:
    /** Create a socket wrapper for descriptor.
     *@param certName name of certificate to use to identify the socket
     */
    SslSocket(const std::string& certName = "", bool clientAuth = false);

    /** Proceed with connect inspite of hostname verifcation failures*/
    void ignoreHostnameVerificationFailure();

    /** Set socket non blocking */
    void setNonblocking() const;

    /** Set tcp-nodelay */
    void setTcpNoDelay() const;

    /** Set SSL cert-name. Allows the cert-name to be set per
     * connection, overriding global cert-name settings from
     * NSSInit().*/
    void setCertName(const std::string& certName);

    void connect(const SocketAddress&) const;
    void finishConnect(const SocketAddress&) const;

    void close() const;

    /** Bind to a port and start listening.
     *@param port 0 means choose an available port.
     *@param backlog maximum number of pending connections.
     *@return The bound port.
     */
    int listen(const SocketAddress&, int backlog = 10) const;

    /**
     * Accept a connection from a socket that is already listening
     * and has an incoming connection
     */
    virtual Socket* accept() const;

    // TODO The following are raw operations, maybe they need better wrapping?
    int read(void *buf, size_t count) const;
    int write(const void *buf, size_t count) const;

    int getKeyLen() const;
    std::string getClientAuthId() const;

protected:
    mutable PRFileDesc* nssSocket;
    std::string certname;
    mutable std::string url;

    /**
     * 'model' socket, with configuration to use when importing
     * accepted sockets for use as ssl sockets. Set on listen(), used
     * in accept to pass through to newly created socket instances.
     */
    mutable PRFileDesc* prototype;
    bool hostnameVerification;

    SslSocket(int fd, PRFileDesc* model);
    friend class SslMuxSocket; // Needed for this constructor
};

class SslMuxSocket : public SslSocket
{
public:
    SslMuxSocket(const std::string& certName = "", bool clientAuth = false);
    Socket* accept() const;
};

}}}
#endif  /*!_sys_ssl_Socket_h*/
