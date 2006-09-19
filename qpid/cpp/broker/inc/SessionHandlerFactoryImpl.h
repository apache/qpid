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
#ifndef _SessionHandlerFactoryImpl_
#define _SessionHandlerFactoryImpl_

#include "AMQFrame.h"
#include "AutoDelete.h"
#include "DirectExchange.h"
#include "ExchangeRegistry.h"
#include "ProtocolInitiation.h"
#include "QueueRegistry.h"
#include "SessionHandlerFactory.h"
#include "TimeoutHandler.h"

namespace qpid {
    namespace broker {

        class SessionHandlerFactoryImpl : public virtual qpid::io::SessionHandlerFactory
        {
            QueueRegistry queues;
            ExchangeRegistry exchanges;
            const u_int32_t timeout;//timeout for auto-deleted queues (in ms)
            AutoDelete cleaner;
        public:
            SessionHandlerFactoryImpl(u_int32_t timeout = 30000);
            virtual qpid::io::SessionHandler* create(qpid::io::SessionContext* ctxt);
            virtual ~SessionHandlerFactoryImpl();
        };

    }
}


#endif
