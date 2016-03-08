#ifndef QPID_SYS_WINDOWS_BSDSOCKET_H
#define QPID_SYS_WINDOWS_BSDSOCKET_H

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

#include "qpid/sys/Socket.h"
#include "qpid/sys/IntegerTypes.h"
#include "qpid/CommonImportExport.h"
#include <string>

#include <boost/scoped_ptr.hpp>

// Ensure we get all of winsock2.h
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0501
#endif

#include <winsock2.h>

namespace qpid {
namespace sys {

namespace windows {
Socket* createSameTypeSocket(const Socket&);
}

class Duration;
class IOHandle;
class SocketAddress;

class QPID_COMMON_CLASS_EXTERN WinSocket : public Socket
{
public:
    /** Create a socket wrapper for descriptor. */
    QPID_COMMON_EXTERN WinSocket();

    QPID_COMMON_EXTERN operator const IOHandle&() const;

    /** Set socket non blocking */
    QPID_COMMON_EXTERN virtual void setNonblocking() const;

    QPID_COMMON_EXTERN virtual void setTcpNoDelay() const;

    QPID_COMMON_EXTERN virtual void connect(const SocketAddress&) const;
    QPID_COMMON_EXTERN virtual void finishConnect(const SocketAddress&) const;

    QPID_COMMON_EXTERN virtual void close() const;

    /** Bind to a port and start listening.
     *@return The bound port number
     */
    QPID_COMMON_EXTERN virtual int listen(const SocketAddress&, int backlog = 10) const;

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
     * Returns the error code stored in the socket.  This may be used
     * to determine the result of a non-blocking connect.
     */
    QPID_COMMON_EXTERN int getError() const;

    QPID_COMMON_EXTERN std::string lastErrorCodeText() const;

    /** Accept a connection from a socket that is already listening
     * and has an incoming connection
     */
    QPID_COMMON_EXTERN virtual Socket* accept() const;

    // TODO The following are raw operations, maybe they need better wrapping?
    QPID_COMMON_EXTERN virtual int read(void *buf, size_t count) const;
    QPID_COMMON_EXTERN virtual int write(const void *buf, size_t count) const;

    QPID_COMMON_EXTERN int getKeyLen() const;
    QPID_COMMON_EXTERN std::string getPeerAuthId() const;
    QPID_COMMON_EXTERN std::string getLocalAuthId() const;

protected:
    /** Create socket */
    void createSocket(const SocketAddress&) const;

    mutable boost::scoped_ptr<IOHandle> handle;
    mutable std::string localname;
    mutable std::string peername;
    mutable bool nonblocking;
    mutable bool nodelay;

    /** Construct socket with existing handle */
    friend Socket* qpid::sys::windows::createSameTypeSocket(const Socket&);
    WinSocket(SOCKET fd);
};

}}
#endif  /*!QPID_SYS_WINDOWS_BSDSOCKET_H*/
