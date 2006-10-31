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
#ifndef _ThreadPool_
#define _ThreadPool_

#include <queue>
#include <vector>
#include "qpid/concurrent/Monitor.h"
#include "qpid/concurrent/Thread.h"
#include "qpid/concurrent/ThreadFactory.h"
#include "qpid/concurrent/ThreadPool.h"
#include "qpid/concurrent/Runnable.h"

namespace qpid {
namespace concurrent {

    class ThreadPool
    {
        class Worker : public virtual Runnable{
            ThreadPool* pool;
        public:
            inline Worker(ThreadPool* _pool) : pool(_pool){}
            inline virtual void run(){
                while(pool->running){
                    pool->runTask();
                }
            }
        };
        const bool deleteFactory;
        const int size;
        ThreadFactory* factory;
        Monitor lock; 
        std::vector<Thread*> threads;
        std::queue<Runnable*> tasks;
        Worker* worker;
        volatile bool running;

        void runTask();
    public:
        ThreadPool(int size);
        ThreadPool(int size, ThreadFactory* factory);
        virtual void start();
        virtual void stop();
	virtual void addTask(Runnable* task);
        virtual ~ThreadPool();
    };

}
}


#endif
