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


#include <qpid/sys/Socket.h>
#include <qpid/apr/APRBase.h>
#include <qpid/apr/APRPool.h>


using namespace qpid::sys;

Socket::Socket()
{
    CHECK_APR_SUCCESS(
        apr_socket_create(
            &socket, APR_INET, SOCK_STREAM, APR_PROTO_TCP,
            APRPool::get()));
}

void Socket::setTimeout(long msecs)
{
    apr_socket_timeout_set(socket, msecs*1000);
}

void Socket::connect(const std::string& host, int port)
{
    apr_sockaddr_t* address;
    CHECK_APR_SUCCESS(
        apr_sockaddr_info_get(
            &address, host.c_str(), APR_UNSPEC, port, APR_IPV4_ADDR_OK,
            APRPool::get()));
    CHECK_APR_SUCCESS(apr_socket_connect(socket, address));
}

void Socket::close()
{
    if (socket == 0) return;
    CHECK_APR_SUCCESS(apr_socket_close(socket));
    socket = 0;
}

ssize_t Socket::send(const char* data, size_t size)
{
    apr_size_t sent = size;
    apr_status_t status = apr_socket_send(socket, data, &sent);
    if (APR_STATUS_IS_TIMEUP(status)) return SOCKET_TIMEOUT;
    if (APR_STATUS_IS_EOF(status)) return SOCKET_EOF;
    CHECK_APR_SUCCESS(status);
    return sent;
}

ssize_t Socket::recv(char* data, size_t size)
{
    apr_size_t received = size;
    apr_status_t status = apr_socket_recv(socket, data, &received);
    if (APR_STATUS_IS_TIMEUP(status)) return SOCKET_TIMEOUT;
    CHECK_APR_SUCCESS(status);
     return received;
}


