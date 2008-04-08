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
#include <sstream>
#include <QpidError.h>
#include "LFProcessor.h"
#include "APRBase.h"
#include "APRPool.h"
#include "LFSessionContext.h"

using namespace qpid::sys;
using qpid::QpidError;

// TODO aconway 2006-10-12: stopped is read outside locks.
//

LFProcessor::LFProcessor(int _workers, int _size, int _timeout) :
    size(_size),
    timeout(_timeout), 
    signalledCount(0),
    current(0),
    count(0),
    workerCount(_workers),
    hasLeader(false),
    workers(new Thread[_workers]),
    stopped(false)
{
    pool = APRPool::get();
    CHECK_APR_SUCCESS(apr_pollset_create(&pollset, size, pool, APR_POLLSET_THREADSAFE));
}


LFProcessor::~LFProcessor(){
    if (!stopped) stop();
    delete[] workers;
    CHECK_APR_SUCCESS(apr_pollset_destroy(pollset));
    APRPool::free(pool);
}

void LFProcessor::start(){
    for(int i = 0; i < workerCount; i++){
        workers[i] = Thread(this);
    }
}

void LFProcessor::add(const apr_pollfd_t* const fd){
    CHECK_APR_SUCCESS(apr_pollset_add(pollset, fd));
    Monitor::ScopedLock l(countLock);
    sessions.push_back(reinterpret_cast<LFSessionContext*>(fd->client_data));
    count++;
}

void LFProcessor::remove(const apr_pollfd_t* const fd){
    CHECK_APR_SUCCESS(apr_pollset_remove(pollset, fd));
    Monitor::ScopedLock l(countLock);
    sessions.erase(find(sessions.begin(), sessions.end(), reinterpret_cast<LFSessionContext*>(fd->client_data)));
    count--;
}

void LFProcessor::reactivate(const apr_pollfd_t* const fd){
    CHECK_APR_SUCCESS(apr_pollset_add(pollset, fd));
}

void LFProcessor::deactivate(const apr_pollfd_t* const fd){
    CHECK_APR_SUCCESS(apr_pollset_remove(pollset, fd));
}

void LFProcessor::update(const apr_pollfd_t* const fd){
    CHECK_APR_SUCCESS(apr_pollset_remove(pollset, fd));
    CHECK_APR_SUCCESS(apr_pollset_add(pollset, fd));
}

bool LFProcessor::full(){
    Mutex::ScopedLock locker(countLock);
    return count == size; 
}

bool LFProcessor::empty(){
    Mutex::ScopedLock locker(countLock);
    return count == 0; 
}

void LFProcessor::poll() {
    apr_status_t status = APR_EGENERAL;
    do{
        current = 0;
        if(!stopped){
            status = apr_pollset_poll(pollset, timeout, &signalledCount, &signalledFDs);
        }
    }while(status != APR_SUCCESS && !stopped);
}

void LFProcessor::run(){
    try{
        while(!stopped){
            const apr_pollfd_t* event = 0;
            LFSessionContext* session = 0;
            {
                Monitor::ScopedLock l(leadLock);
                waitToLead();
                event = getNextEvent();
                if(!event) return;
                session = reinterpret_cast<LFSessionContext*>(
                    event->client_data);
                session->startProcessing();
                relinquishLead();
            }

            //process event:
            if(event->rtnevents & APR_POLLIN) session->read();
            if(event->rtnevents & APR_POLLOUT) session->write();

            if(session->isClosed()){
                session->handleClose();
                Monitor::ScopedLock l(countLock);
                sessions.erase(find(sessions.begin(),sessions.end(), session));
                count--;
            }else{
                session->stopProcessing();
            }
        }
    }catch(std::exception e){
	std::cout << e.what() << std::endl;
    }
}

void LFProcessor::waitToLead(){
    while(hasLeader && !stopped) leadLock.wait();
    hasLeader = !stopped;
}

void LFProcessor::relinquishLead(){
    hasLeader = false;
    leadLock.notify();
}

const apr_pollfd_t* LFProcessor::getNextEvent(){
    while(true){
        if(stopped){
            return 0;
        }else if(current < signalledCount){
            //use result of previous poll if one is available
            return signalledFDs + (current++);
        }else{
            //else poll to get new events
            poll();
        }
    }
}

void LFProcessor::stop(){
    stopped = true;
    {
        Monitor::ScopedLock l(leadLock);
        leadLock.notifyAll();
    }
    for(int i = 0; i < workerCount; i++){
        workers[i].join();
    }
    for(iterator i = sessions.begin(); i < sessions.end(); i++){
        (*i)->shutdown();
    }
}

