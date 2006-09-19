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
#include "APRThreadFactory.h"
#include "APRThreadPool.h"
#include "QpidError.h"
#include <iostream>

using namespace qpid::concurrent;

APRThreadPool::APRThreadPool(int _size) : size(_size), factory(new APRThreadFactory()), 
                                          deleteFactory(true), running(false){
    worker = new Worker(this);
}

APRThreadPool::APRThreadPool(int _size, ThreadFactory* _factory) : size(_size), factory(_factory), 
                                                                   deleteFactory(false), running(false){
    worker = new Worker(this);
}

APRThreadPool::~APRThreadPool(){
    if(deleteFactory) delete factory;
}

void APRThreadPool::addTask(Runnable* task){
    lock.acquire();
    tasks.push(task);
    lock.notifyAll();
    lock.release();
}

void APRThreadPool::runTask(){
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

void APRThreadPool::start(){
    if(!running){
        running = true;
        for(int i = 0; i < size; i++){
            Thread* t = factory->create(worker);
            t->start();
            threads.push_back(t);
        }
    }
}

void APRThreadPool::stop(){
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


