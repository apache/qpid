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


#include <sys/Socket.h>
#include <apr/APRBase.h>
#include <apr/APRPool.h>

#include <iostream>

using namespace qpid::sys;

Socket Socket::createTcp() {
    Socket s;
    apr_pool_t* pool = APRPool::get();
    CHECK_APR_SUCCESS(
        apr_socket_create(
            &s.socket, APR_INET, SOCK_STREAM, APR_PROTO_TCP,
            pool));
    APRPool::free(pool);
    return s;
}

Socket::Socket(apr_socket_t* s) {
    socket = s;
}

void Socket::setTimeout(Time interval) {
    apr_socket_timeout_set(socket, interval/TIME_USEC);
}

void Socket::connect(const std::string& host, int port) {
    apr_sockaddr_t* address;
    apr_pool_t* pool = APRPool::get();
    CHECK_APR_SUCCESS(
        apr_sockaddr_info_get(
            &address, host.c_str(), APR_UNSPEC, port, APR_IPV4_ADDR_OK,
            pool));
    CHECK_APR_SUCCESS(apr_socket_connect(socket, address));
    APRPool::free(pool);
}

void Socket::close() {
    if (socket == 0) return;
    CHECK_APR_SUCCESS(apr_socket_shutdown(socket, APR_SHUTDOWN_READWRITE));
    CHECK_APR_SUCCESS(apr_socket_close(socket));
    socket = 0;
}

ssize_t Socket::send(const void* data, size_t size)
{
    apr_size_t sent = size;
    apr_status_t status =
        apr_socket_send(socket, reinterpret_cast<const char*>(data), &sent);
    if (APR_STATUS_IS_TIMEUP(status)) return SOCKET_TIMEOUT;
    if (APR_STATUS_IS_EOF(status)) return SOCKET_EOF;
    CHECK_APR_SUCCESS(status);
    return sent;
}

ssize_t Socket::recv(void* data, size_t size)
{
    apr_size_t received = size;
    apr_status_t status =
        apr_socket_recv(socket, reinterpret_cast<char*>(data), &received);
    if (APR_STATUS_IS_TIMEUP(status)) return SOCKET_TIMEOUT;
    if (APR_STATUS_IS_EOF(status)) return SOCKET_EOF;
    CHECK_APR_SUCCESS(status);
    return received;
}

void Socket::setTcpNoDelay(bool on)
{
    CHECK_APR_SUCCESS(apr_socket_opt_set(socket, APR_TCP_NODELAY, on ? 1 : 0));
}

