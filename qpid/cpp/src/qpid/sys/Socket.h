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

#include "qpid/sys/IntegerTypes.h"
#include "qpid/CommonImportExport.h"
#include <string>

namespace qpid {
namespace sys {

class Duration;
class IOHandle;
class SocketAddress;

class Socket
{
public:
    virtual ~Socket() {};

    virtual operator const IOHandle&() const = 0;

    /** Set socket non blocking */
    virtual void setNonblocking() const = 0;

    virtual void setTcpNoDelay() const = 0;

    virtual void connect(const SocketAddress&) const = 0;
    virtual void finishConnect(const SocketAddress&) const = 0;

    virtual void close() const = 0;

    /** Bind to a port and start listening.
     *@param port 0 means choose an available port.
     *@param backlog maximum number of pending connections.
     *@return The bound port.
     */
    virtual int listen(const SocketAddress&, int backlog = 10) const = 0;

    /**
     * Returns an address (host and port) for the remote end of the
     * socket
     */
    virtual std::string getPeerAddress() const = 0;

    /**
     * Returns an address (host and port) for the local end of the
     * socket
     */
    virtual std::string getLocalAddress() const = 0;

    /**
     * Returns the full address of the connection: local and remote host and port.
     */
    QPID_COMMON_INLINE_EXTERN std::string getFullAddress() const { return getLocalAddress()+"-"+getPeerAddress(); }

    /**
     * Returns the error code stored in the socket.  This may be used
     * to determine the result of a non-blocking connect.
     */
    virtual int getError() const = 0;

    /**
     * Returns error text for last read/write error code
     */
    virtual std::string lastErrorCodeText() const = 0;

    /**
     * Accept a connection from a socket that is already listening
     * and has an incoming connection
     */
    virtual Socket* accept() const = 0;

    /** Read up to count bytes into buffer
     * If return is positive read that number of bytes;
     * if 0 then end of input stream (or disconnected)
     * if <0 then error, in this case an error text can be fetched
     *    using lastErrorCodeText().
     */
    virtual int read(void *buf, size_t count) const = 0;

    /** Write up to count bytes into buffer
     * If return is positive wrote that number of bytes;
     * if 0 then stream closed (or disconnected)
     * if <0 then error, in this case an error text can be fetched
     *    using lastErrorCodeText().
     */
    virtual int write(const void *buf, size_t count) const = 0;

    /* Transport security related: */
    virtual int getKeyLen() const = 0;
    virtual std::string getPeerAuthId() const = 0;
    virtual std::string getLocalAuthId() const = 0;
};

/** Make the default socket for whatever platform we are executing on
 */
QPID_COMMON_EXTERN Socket* createSocket();

}}
#endif  /*!_sys_Socket_h*/
