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
#ifndef _APRSocket_
#define _APRSocket_

#include "apr-1/apr_network_io.h"
#include "qpid/framing/Buffer.h"

namespace qpid {
namespace sys {

    class APRSocket
    {
	apr_socket_t* const socket;
        volatile bool closed;
    public:
	APRSocket(apr_socket_t* socket);
        void read(qpid::framing::Buffer& b);
        void write(qpid::framing::Buffer& b);
        void close();
        bool isOpen();
        u_int8_t read();
	~APRSocket();
    };

}
}


#endif
