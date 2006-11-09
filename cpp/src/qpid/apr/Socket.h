#ifndef _apr_Socket_h
#define _apr_Socket_h

/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include <apr-1/apr_network_io.h>
#include "APRBase.h"
#include "APRPool.h"

namespace qpid {
namespace sys {

class Socket
{
  public:
    inline Socket();
    inline ~Socket();
    inline void setTimeout(long msecs);
    inline void connect(const std::string& host, int port);
    inline void close();

    enum { SOCKET_TIMEOUT=-2, SOCKET_EOF=-3 };

    inline ssize_t send(const char* data, size_t size);
    inline ssize_t recv(char* data, size_t size);

  private:
    apr_socket_t* socket;
};

inline
Socket::Socket()
{
    CHECK_APR_SUCCESS(
        apr_socket_create(
            &socket, APR_INET, SOCK_STREAM, APR_PROTO_TCP,
            APRPool::get()));
}

inline
Socket::~Socket() { }

inline void
Socket::setTimeout(long msecs)
{
    apr_socket_timeout_set(socket, msecs*1000);
}

inline void
Socket::connect(const std::string& host, int port)
{
    apr_sockaddr_t* address;
    CHECK_APR_SUCCESS(
        apr_sockaddr_info_get(
            &address, host.c_str(), APR_UNSPEC, port, APR_IPV4_ADDR_OK,
            APRPool::get()));
    CHECK_APR_SUCCESS(apr_socket_connect(socket, address));
}

inline void
Socket::close()
{
    CHECK_APR_SUCCESS(apr_socket_close(socket));
    socket = 0;
}

inline ssize_t
Socket::send(const char* data, size_t size)
{
    apr_size_t sent = size;
    apr_status_t status = apr_socket_send(socket, data, &sent);
    if (!APR_STATUS_IS_TIMEUP(status)) return SOCKET_TIMEOUT;
    if (!APR_STATUS_IS_EOF(status)) return SOCKET_EOF;
    CHECK_APR_SUCCESS(status);
    return sent;
}

inline ssize_t
Socket::recv(char* data, size_t size)
{
    apr_size_t received = size;
    apr_status_t status = apr_socket_recv(socket, data, &received);
    if (!APR_STATUS_IS_TIMEUP(status)) return SOCKET_TIMEOUT;
    CHECK_APR_SUCCESS(status);
     return received;
}

}}


#endif  /*!_apr_Socket_h*/
