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
#include "qpid/concurrent/ThreadFactory.h"
#include "qpid/concurrent/ThreadPool.h"
#include "qpid/QpidError.h"
#include <iostream>

using namespace qpid::concurrent;

ThreadPool::ThreadPool(int _size) : deleteFactory(true), size(_size), factory(new ThreadFactory()), running(false){
    worker = new Worker(this);
}

ThreadPool::ThreadPool(int _size, ThreadFactory* _factory) :     deleteFactory(false), size(_size), factory(_factory), running(false){
    worker = new Worker(this);
}

ThreadPool::~ThreadPool(){
    if(deleteFactory) delete factory;
}

void ThreadPool::addTask(Runnable* task){
    lock.acquire();
    tasks.push(task);
    lock.notifyAll();
    lock.release();
}

void ThreadPool::runTask(){
    lock.acquire();
    while(tasks.empty()){
        lock.wait();
    }
    Runnable* task = tasks.front();
    tasks.pop();
    lock.release();
    try{
        task->run();
    }catch(qpid::QpidError error){
	std::cout << "Error [" << error.code << "] " << error.msg << " (" << error.file << ":" << error.line << ")" << std::endl;
    }
}

void ThreadPool::start(){
    if(!running){
        running = true;
        for(int i = 0; i < size; i++){
            Thread* t = factory->create(worker);
            t->start();
            threads.push_back(t);
        }
    }
}

void ThreadPool::stop(){
    if(!running){
        running = false;
        lock.acquire();
        lock.notifyAll();
        lock.release();
        for(int i = 0; i < size; i++){
            threads[i]->join();
            delete threads[i];
        }
    }
}


