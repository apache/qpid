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
#include "APRIOProcessor.h"
#include "APRBase.h"
#include "QpidError.h"

using namespace qpid::io;
using namespace qpid::concurrent;

APRIOProcessor::APRIOProcessor(apr_pool_t* pool, int _size, int _timeout) : size(_size), 
                                                                            timeout(_timeout), 
                                                                            count(0), 
                                                                            thread(pool, this), 
                                                                            stopped(false){

    CHECK_APR_SUCCESS(apr_pollset_create(&pollset, size, pool, APR_POLLSET_THREADSAFE));
    thread.start();
}

void APRIOProcessor::add(apr_pollfd_t* const fd){
    CHECK_APR_SUCCESS(apr_pollset_add(pollset, fd));
    lock.acquire();
    if(!count++) lock.notify();
    lock.release();
}

void APRIOProcessor::remove(apr_pollfd_t* const fd){
    CHECK_APR_SUCCESS(apr_pollset_remove(pollset, fd));
    lock.acquire();
    count--;
    lock.release();
}

bool APRIOProcessor::full(){
    lock.acquire();
    bool full = count == size; 
    lock.release();
    return full;
}

bool APRIOProcessor::empty(){
    lock.acquire();
    bool empty = count == 0; 
    lock.release();
    return empty;
}

void APRIOProcessor::poll(){
    try{
        int signalledCount;
        const apr_pollfd_t* signalledFDs;
        apr_status_t status = apr_pollset_poll(pollset, timeout, &signalledCount, &signalledFDs);
        if(status == APR_SUCCESS){
            for(int i = 0; i < signalledCount; i++){
                IOSessionHolder* session = reinterpret_cast<IOSessionHolder*>(signalledFDs[i].client_data);
                if(signalledFDs[i].rtnevents & APR_POLLIN) session->read();
                if(signalledFDs[i].rtnevents & APR_POLLOUT) session->write();
            }
        }
    }catch(qpid::QpidError error){
	std::cout << "Error [" << error.code << "] " << error.msg << " (" << error.file << ":" << error.line << ")" << std::endl;
    }

}

void APRIOProcessor::run(){
    while(!stopped){
        lock.acquire();
        while(count == 0) lock.wait();
        lock.release();
        poll();
    }
}

void APRIOProcessor::stop(){
    lock.acquire();
    stopped = true;
    lock.notify();
    lock.release();
}

APRIOProcessor::~APRIOProcessor(){
    CHECK_APR_SUCCESS(apr_pollset_destroy(pollset));
}

