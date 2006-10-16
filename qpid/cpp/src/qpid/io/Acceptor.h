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
#ifndef _Acceptor_
#define _Acceptor_

#include "qpid/io/SessionHandlerFactory.h"

namespace qpid {
namespace io {

    class Acceptor
    {
    public:
        /**
         * Bind to port.
         * @param port Port to bind to, 0 to bind to dynamically chosen port.
         * @return The local bound port.
         */
        virtual int16_t bind(int16_t port) = 0;

        /**
         * Run the acceptor.
         */
        virtual void run(SessionHandlerFactory* factory) = 0;

        /**
         * Shut down the acceptor.
         */
        virtual void shutdown() = 0;
        
	virtual ~Acceptor();
    };

}
}


#endif
