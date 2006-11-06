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

#include "qpid/broker/AutoDelete.h"
#include "qpid/broker/ExchangeRegistry.h"
#include "qpid/broker/MessageStore.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/sys/SessionContext.h"
#include "qpid/sys/SessionHandler.h"
#include "qpid/sys/SessionHandlerFactory.h"
#include "qpid/sys/TimeoutHandler.h"
#include <memory>

namespace qpid {
    namespace broker {

        class SessionHandlerFactoryImpl : public virtual qpid::sys::SessionHandlerFactory
        {
            std::auto_ptr<MessageStore> store;
            QueueRegistry queues;
            ExchangeRegistry exchanges;
            const u_int32_t timeout;//timeout for auto-deleted queues (in ms)
            AutoDelete cleaner;
        public:
            SessionHandlerFactoryImpl(u_int32_t timeout = 30000);
            void recover();
            virtual qpid::sys::SessionHandler* create(qpid::sys::SessionContext* ctxt);
            virtual ~SessionHandlerFactoryImpl();
        };

    }
}


#endif
