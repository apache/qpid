#ifndef _sys_Socket_h
#define _sys_Socket_h

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
#include <string>
#include "qpid/sys/Time.h"

struct sockaddr;

namespace qpid {
namespace sys {

class SocketPrivate;
class Socket
{
	friend class Poller;

	SocketPrivate* const impl;

public:
    /** Create a socket wrapper for descriptor. */
    Socket();
    ~Socket();

    /** Create an initialized TCP socket */
    void createTcp() const;
    
    /** Set timeout for read and write */
    void setTimeout(const Duration& interval) const;
    
    /** Set socket non blocking */
    void setNonblocking() const;

    void connect(const std::string& host, int port) const;

    void close() const;

    enum { SOCKET_TIMEOUT=-2, SOCKET_EOF=-3 } ErrorCode;

    /** Returns bytes sent or an ErrorCode value < 0. */
    ssize_t send(const void* data, size_t size) const;

    /**
     * Returns bytes received, an ErrorCode value < 0 or 0
     * if the connection closed in an orderly manner.
     */
    ssize_t recv(void* data, size_t size) const;

    /** Bind to a port and start listening.
     *@param port 0 means choose an available port.
     *@param backlog maximum number of pending connections.
     *@return The bound port.
     */
    int listen(int port = 0, int backlog = 10) const;
    
    /** Returns the "socket name" ie the address bound to 
     * the near end of the socket
     */
    std::string getSockname() const;

    /** Returns the "peer name" ie the address bound to 
     * the remote end of the socket
     */
    std::string getPeername() const;

    /** 
     * Returns an address (host and port) for the remote end of the
     * socket
     */
    std::string getPeerAddress() const;

    /** Accept a connection from a socket that is already listening
     * and has an incoming connection
     */
    Socket* accept(struct sockaddr *addr, socklen_t *addrlen) const;

    // TODO The following are raw operations, maybe they need better wrapping? 
    int read(void *buf, size_t count) const;
    int write(const void *buf, size_t count) const;

private:
	Socket(SocketPrivate*);
};

}}
#endif  /*!_sys_Socket_h*/
