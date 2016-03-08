#ifndef QPID_SYS_BSDSOCKET_H
#define QPID_SYS_BSDSOCKET_H

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

namespace qpid {
namespace sys {

class Duration;
class IOHandle;
class SocketAddress;

namespace ssl {
class SslMuxSocket;
}

class QPID_COMMON_CLASS_EXTERN BSDSocket : public Socket
{
public:
    /** Create a socket wrapper for descriptor. */
    QPID_COMMON_EXTERN BSDSocket();

    /** Construct socket with existing fd (posix specific and not in Socket interface) */
    QPID_COMMON_EXTERN BSDSocket(int fd);

    QPID_COMMON_EXTERN virtual ~BSDSocket();

    QPID_COMMON_EXTERN operator const IOHandle&() const;

    QPID_COMMON_EXTERN virtual void setNonblocking() const;
    QPID_COMMON_EXTERN virtual void setTcpNoDelay() const;

    QPID_COMMON_EXTERN std::string getPeerAddress() const;
    QPID_COMMON_EXTERN std::string getLocalAddress() const;

    QPID_COMMON_EXTERN int getError() const;
    QPID_COMMON_EXTERN virtual std::string lastErrorCodeText() const;

    QPID_COMMON_EXTERN virtual void connect(const SocketAddress&) const;
    QPID_COMMON_EXTERN virtual void finishConnect(const SocketAddress&) const;
    QPID_COMMON_EXTERN virtual int listen(const SocketAddress&, int backlog = 10) const;
    QPID_COMMON_EXTERN virtual Socket* accept() const;
    QPID_COMMON_EXTERN virtual int read(void *buf, size_t count) const;
    QPID_COMMON_EXTERN virtual int write(const void *buf, size_t count) const;
    QPID_COMMON_EXTERN virtual void close() const;

    QPID_COMMON_EXTERN virtual int getKeyLen() const;
    QPID_COMMON_EXTERN virtual std::string getPeerAuthId() const;
    QPID_COMMON_EXTERN virtual std::string getLocalAuthId() const;

protected:
    /** Create socket */
    void createSocket(const SocketAddress&) const;

    mutable boost::scoped_ptr<IOHandle> handle;
    mutable std::string localname;
    mutable std::string peername;
    mutable int fd;
    mutable int lastErrorCode;
    mutable bool nonblocking;
    mutable bool nodelay;
};

}}
#endif  /*!QPID_SYS_BSDSOCKET_H*/
