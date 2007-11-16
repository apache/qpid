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
#include <sys/Time.h>

#ifdef USE_APR
#  include <apr_network_io.h>
#endif

namespace qpid {
namespace sys {

class Socket
{
  public:
    /** Create an initialized TCP socket */
    static Socket createTcp();

    /** Create a socket wrapper for descriptor. */
#ifdef USE_APR
    Socket(apr_socket_t* descriptor = 0);
#else
    Socket(int descriptor = 0);
#endif
    
    /** Set timeout for read and write */
    void setTimeout(Time interval);
    void setTcpNoDelay(bool on);

    void connect(const std::string& host, int port);

    void close();

    enum { SOCKET_TIMEOUT=-2, SOCKET_EOF=-3 } ErrorCode;

    /** Returns bytes sent or an ErrorCode value < 0. */
    ssize_t send(const void* data, size_t size);

    /**
     * Returns bytes received, an ErrorCode value < 0 or 0
     * if the connection closed in an orderly manner.
     */
    ssize_t recv(void* data, size_t size);

    /** Bind to a port and start listening.
     *@param port 0 means choose an available port.
     *@param backlog maximum number of pending connections.
     *@return The bound port.
     */
    int listen(int port = 0, int backlog = 10);

    /** Get file descriptor */
    int fd(); 
    
  private:
#ifdef USE_APR    
    apr_socket_t* socket;
#else
    void init() const;
    mutable int socket;         // Initialized on demand. 
#endif
};

}}


#endif  /*!_sys_Socket_h*/
