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

#include "qpid/sys/IOHandle.h"
#include "qpid/sys/IntegerTypes.h"
#include "qpid/CommonImportExport.h"
#include <string>

namespace qpid {
namespace sys {

class Duration;
class SocketAddress;

namespace ssl {
class SslMuxSocket;
}

class QPID_COMMON_CLASS_EXTERN Socket : public IOHandle
{
public:
    /** Create a socket wrapper for descriptor. */
    QPID_COMMON_EXTERN Socket();

    /** Create a new Socket which is the same address family as this one */
    QPID_COMMON_EXTERN Socket* createSameTypeSocket() const;

    /** Set socket non blocking */
    void setNonblocking() const;

    QPID_COMMON_EXTERN void setTcpNoDelay() const;

    QPID_COMMON_EXTERN void connect(const SocketAddress&) const;

    QPID_COMMON_EXTERN void close() const;

    /** Bind to a port and start listening.
     *@param port 0 means choose an available port.
     *@param backlog maximum number of pending connections.
     *@return The bound port.
     */
    QPID_COMMON_EXTERN int listen(const SocketAddress&, int backlog = 10) const;

    /**
     * Returns an address (host and port) for the remote end of the
     * socket
     */
    QPID_COMMON_EXTERN std::string getPeerAddress() const;
    /**
     * Returns an address (host and port) for the local end of the
     * socket
     */
    QPID_COMMON_EXTERN std::string getLocalAddress() const;

    /**
     * Returns the full address of the connection: local and remote host and port.
     */
    QPID_COMMON_INLINE_EXTERN std::string getFullAddress() const { return getLocalAddress()+"-"+getPeerAddress(); }

    /**
     * Returns the error code stored in the socket.  This may be used
     * to determine the result of a non-blocking connect.
     */
    QPID_COMMON_EXTERN int getError() const;

    /** Accept a connection from a socket that is already listening
     * and has an incoming connection
     */
    QPID_COMMON_EXTERN Socket* accept() const;

    // TODO The following are raw operations, maybe they need better wrapping?
    QPID_COMMON_EXTERN int read(void *buf, size_t count) const;
    QPID_COMMON_EXTERN int write(const void *buf, size_t count) const;

protected:
    /** Create socket */
    void createSocket(const SocketAddress&) const;

    mutable std::string localname;
    mutable std::string peername;
    mutable bool nonblocking;
    mutable bool nodelay;

    /** Construct socket with existing handle */
    Socket(IOHandlePrivate*);
    friend class qpid::sys::ssl::SslMuxSocket;
};

}}
#endif  /*!_sys_Socket_h*/
