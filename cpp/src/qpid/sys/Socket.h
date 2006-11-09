#ifndef _sys_Socket_h
#define _sys_Socket_h

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

#include <string>

#ifdef USE_APR
#  include <apr-1/apr_network_io.h>
#endif

namespace qpid {
namespace sys {

class Socket
{
  public:
    Socket();

    /** Set timeout for read and write */
    void setTimeout(long msecs);

    void connect(const std::string& host, int port);

    void close();

    enum { SOCKET_TIMEOUT=-2, SOCKET_EOF=-3 } ErrorCode;

    /** Returns bytes sent or an ErrorCode value < 0. */
    ssize_t send(const char* data, size_t size);

    /**
     * Returns bytes received, an ErrorCode value < 0 or 0
     * if the connection closed in an orderly manner.
     */
    ssize_t recv(char* data, size_t size);

  private:
#ifdef USE_APR    
    apr_socket_t* socket;
#else
    int socket;
#endif
};

}}


#endif  /*!_sys_Socket_h*/
