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
#ifndef _Connector_
#define _Connector_

#include "InputHandler.h"
#include "OutputHandler.h"
#include "InitiationHandler.h"
#include "ProtocolInitiation.h"
#include "ShutdownHandler.h"
#include "TimeoutHandler.h"

namespace qpid {
namespace io {

    class Connector
    {
    public:
	virtual void connect(const std::string& host, int port) = 0;
	virtual void init(qpid::framing::ProtocolInitiation* header) = 0;
	virtual void close() = 0;
	virtual void setInputHandler(qpid::framing::InputHandler* handler) = 0;
	virtual void setTimeoutHandler(TimeoutHandler* handler) = 0;
	virtual void setShutdownHandler(ShutdownHandler* handler) = 0;
	virtual qpid::framing::OutputHandler* getOutputHandler() = 0;
        /**
         * Set the timeout for reads, in secs.
         */
        virtual void setReadTimeout(u_int16_t timeout) = 0;
        /**
         * Set the timeout for writes, in secs.
         */
        virtual void setWriteTimeout(u_int16_t timeout) = 0;
	virtual ~Connector(){}
    };

}
}


#endif
