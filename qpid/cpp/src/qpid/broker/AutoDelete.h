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
#ifndef _AutoDelete_
#define _AutoDelete_

#include <iostream>
#include <queue>
#include "qpid/concurrent/MonitorImpl.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/concurrent/ThreadFactoryImpl.h"

namespace qpid {
    namespace broker{
        class AutoDelete : private virtual qpid::concurrent::Runnable{
            qpid::concurrent::ThreadFactoryImpl factory;
            qpid::concurrent::MonitorImpl lock;            
            qpid::concurrent::MonitorImpl monitor;            
            std::queue<Queue::shared_ptr> queues;
            QueueRegistry* const registry;
            const u_int32_t period;
            volatile bool stopped;
            qpid::concurrent::Thread* runner;
            
            Queue::shared_ptr const pop();
            void process();
            virtual void run();

        public:
            AutoDelete(QueueRegistry* const registry, u_int32_t period);
            void add(Queue::shared_ptr const);
            void start();
            void stop();
        };
    }
}


#endif
