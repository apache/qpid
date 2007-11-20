#ifndef _AutoDelete_
#define _AutoDelete_
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
#include <iostream>
#include <queue>
#include <sys/Monitor.h>
#include <BrokerQueue.h>
#include <QueueRegistry.h>
#include <sys/Thread.h>

namespace qpid {
    namespace broker{
        class AutoDelete : private qpid::sys::Runnable {
            qpid::sys::Mutex lock;            
            qpid::sys::Monitor monitor;            
            std::queue<Queue::shared_ptr> queues;
            QueueRegistry* const registry;
            u_int32_t period;
            volatile bool stopped;
            qpid::sys::Thread runner;
            
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
