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
#ifndef _APRThreadPool_
#define _APRThreadPool_

#include <queue>
#include <vector>
#include "APRMonitor.h"
#include "Thread.h"
#include "ThreadFactory.h"
#include "ThreadPool.h"
#include "Runnable.h"

namespace qpid {
namespace concurrent {

    class APRThreadPool : public virtual ThreadPool
    {
        class Worker : public virtual Runnable{
            APRThreadPool* pool;
        public:
            inline Worker(APRThreadPool* _pool) : pool(_pool){}
            inline virtual void run(){
                while(pool->running){
                    pool->runTask();
                }
            }
        };
        const bool deleteFactory;
        const int size;
        ThreadFactory* factory;
        APRMonitor lock; 
        std::vector<Thread*> threads;
        std::queue<Runnable*> tasks;
        Worker* worker;
        volatile bool running;

        void runTask();
    public:
        APRThreadPool(int size);
        APRThreadPool(int size, ThreadFactory* factory);
        virtual void start();
        virtual void stop();
	virtual void addTask(Runnable* task);
        virtual ~APRThreadPool();
    };

}
}


#endif
