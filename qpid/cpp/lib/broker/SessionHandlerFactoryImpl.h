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
#ifndef _SessionHandlerFactoryImpl_
#define _SessionHandlerFactoryImpl_

#include <AutoDelete.h>
#include <ExchangeRegistry.h>
#include <MessageStore.h>
#include <QueueRegistry.h>
#include <AMQFrame.h>
#include <ProtocolInitiation.h>
#include <sys/SessionContext.h>
#include <sys/SessionHandler.h>
#include <sys/SessionHandlerFactory.h>
#include <sys/TimeoutHandler.h>
#include <SessionHandlerImpl.h>
#include <memory>

namespace qpid {
    namespace broker {

        class SessionHandlerFactoryImpl : public virtual qpid::sys::SessionHandlerFactory
        {
            std::auto_ptr<MessageStore> store;
            QueueRegistry queues;
            ExchangeRegistry exchanges;
            const Settings settings;
            AutoDelete cleaner;
        public:
            SessionHandlerFactoryImpl(const std::string& store = "", u_int64_t stagingThreshold = 0, u_int32_t timeout = 30000);
            virtual qpid::sys::SessionHandler* create(qpid::sys::SessionContext* ctxt);
            virtual ~SessionHandlerFactoryImpl();
        };

    }
}


#endif
