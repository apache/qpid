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
#include "qpid/io/LFProcessor.h"
#include "qpid/concurrent/APRBase.h"
#include "qpid/io/LFSessionContext.h"
#include "qpid/QpidError.h"
#include <sstream>

using namespace qpid::io;
using namespace qpid::concurrent;
using qpid::QpidError;

// TODO aconway 2006-10-12: stopped is read outside locks.
//

LFProcessor::LFProcessor(apr_pool_t* pool, int _workers, int _size, int _timeout) :
    size(_size),
    timeout(_timeout), 
    signalledCount(0),
    current(0),
    count(0),
    workerCount(_workers),
    hasLeader(false),
    workers(new Thread*[_workers]),
    stopped(false)
{

    CHECK_APR_SUCCESS(apr_pollset_create(&pollset, size, pool, APR_POLLSET_THREADSAFE));
    //create & start the required number of threads
    for(int i = 0; i < workerCount; i++){
        workers[i] = factory.create(this);
    }
}


LFProcessor::~LFProcessor(){
    if (!stopped) stop();
    for(int i = 0; i < workerCount; i++){
        delete workers[i];
    }
    delete[] workers;
    CHECK_APR_SUCCESS(apr_pollset_destroy(pollset));
}

void LFProcessor::start(){
    for(int i = 0; i < workerCount; i++){
        workers[i]->start();
    }
}

void LFProcessor::add(const apr_pollfd_t* const fd){
    CHECK_APR_SUCCESS(apr_pollset_add(pollset, fd));
    countLock.acquire();
    sessions.push_back(reinterpret_cast<LFSessionContext*>(fd->client_data));
    count++;
    countLock.release();
}

void LFProcessor::remove(const apr_pollfd_t* const fd){
    CHECK_APR_SUCCESS(apr_pollset_remove(pollset, fd));
    countLock.acquire();
    sessions.erase(find(sessions.begin(), sessions.end(), reinterpret_cast<LFSessionContext*>(fd->client_data)));
    count--;
    countLock.release();
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
    Locker locker(countLock);
    return count == size; 
}

bool LFProcessor::empty(){
    Locker locker(countLock);
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
            leadLock.acquire();
            waitToLead();
            if(!stopped){
                const apr_pollfd_t* evt = getNextEvent();
                if(evt){
                    LFSessionContext* session = reinterpret_cast<LFSessionContext*>(evt->client_data);
                    session->startProcessing();

                    relinquishLead();
                    leadLock.release();

                    //process event:
                    if(evt->rtnevents & APR_POLLIN) session->read();
                    if(evt->rtnevents & APR_POLLOUT) session->write();

                    if(session->isClosed()){
                        session->handleClose();
                        countLock.acquire();
                        sessions.erase(find(sessions.begin(), sessions.end(), session));
                        count--;
                        countLock.release();                        
                    }else{
                        session->stopProcessing();
                    }

                }else{
                    leadLock.release();
                }
            }else{
                leadLock.release();
            }
        }
    }catch(QpidError error){
	std::cout << "Error [" << error.code << "] " << error.msg << " (" << error.file << ":" << error.line << ")" << std::endl;
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
    leadLock.acquire();
    leadLock.notifyAll();
    leadLock.release();

    for(int i = 0; i < workerCount; i++){
        workers[i]->join();
    }

    for(iterator i = sessions.begin(); i < sessions.end(); i++){
        (*i)->shutdown();
    }
}

