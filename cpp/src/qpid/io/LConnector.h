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
#ifndef _LConnector_
#define _LConnector_


#include "qpid/framing/InputHandler.h"
#include "qpid/framing/OutputHandler.h"
#include "qpid/framing/InitiationHandler.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/concurrent/Thread.h"
#include "qpid/concurrent/ThreadFactory.h"
#include "qpid/io/Connector.h"

namespace qpid {
namespace io {

    class LConnector : public virtual qpid::framing::OutputHandler, 
	public virtual Connector,
	private virtual qpid::concurrent::Runnable
    {

    public:
	LConnector(bool debug = false, u_int32_t buffer_size = 1024){};
	virtual ~LConnector(){};

    };

}
}


#endif
