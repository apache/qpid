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
#include "LFSessionContext.h"
#include "APRBase.h"
#include "qpid/QpidError.h"
#include "qpid/log/Statement.h"
#include <assert.h>

using namespace qpid::sys;
using namespace qpid::sys;
using namespace qpid::framing;

LFSessionContext::LFSessionContext(apr_pool_t* _pool, apr_socket_t* _socket, 
                                   LFProcessor* const _processor,
                                   bool _debug) :
    debug(_debug),
    socket(_socket),
    initiated(false),
    in(65536),
    out(65536),
    processor(_processor),
    processing(false),
    closing(false)
{
    
    fd.p = _pool;
    fd.desc_type = APR_POLL_SOCKET;
    fd.reqevents = APR_POLLIN;
    fd.client_data = this;
    fd.desc.s = _socket;

    out.flip();
}

LFSessionContext::~LFSessionContext(){

}

void LFSessionContext::read(){
    socket.read(in);
    in.flip();
    if(initiated){
        AMQFrame frame;
        try{
            while(frame.decode(in)){
                QPID_LOG(debug, "RECV: " << frame);
                handler->received(&frame);
            }
        }catch(const std::exception& e){
            QPID_LOG(error, e.what());
        }
    }else{
        ProtocolInitiation protocolInit;
        if(protocolInit.decode(in)){
            handler->initiated(protocolInit);
            initiated = true;
            QPID_LOG(debug, "INIT [" << &socket << "]");
        }
    }
    in.compact();
}

void LFSessionContext::write(){
    bool done = isClosed();
    while(!done){
        if(out.available() > 0){
            socket.write(out);
            if(out.available() > 0){

                //incomplete write, leave flags to receive notification of readiness to write
                done = true;//finished processing for now, but write is still in progress
            }
        }else{
            //do we have any frames to write?
            Mutex::ScopedLock l(writeLock);
            if(!framesToWrite.empty()){
                out.clear();
                bool encoded(false);
                AMQFrame* frame = framesToWrite.front();
                while(frame && out.available() >= frame->size()){
                    encoded = true;
                    frame->encode(out);
                    QPID_LOG(debug, "SENT: " << *frame);
                    delete frame;
                    framesToWrite.pop();
                    frame = framesToWrite.empty() ? 0 : framesToWrite.front();
                }
                if(!encoded) THROW_QPID_ERROR(FRAMING_ERROR, "Could not write frame, too large for buffer.");
                out.flip();
            }else{
                //reset flags, don't care about writability anymore
                fd.reqevents = APR_POLLIN;
                done = true;

                if(closing){
                    socket.close();
                }
            }
        }
    }
}

void LFSessionContext::send(AMQFrame* frame){
    Mutex::ScopedLock l(writeLock);
    if(!closing){
        framesToWrite.push(frame);
        if(!(fd.reqevents & APR_POLLOUT)){
            fd.reqevents |= APR_POLLOUT;
            if(!processing){
                processor->update(&fd);
            }
        }
    }
}

void LFSessionContext::startProcessing(){
    Mutex::ScopedLock l(writeLock);
    processing = true;
    processor->deactivate(&fd);
}

void LFSessionContext::stopProcessing(){
    Mutex::ScopedLock l(writeLock);
    processor->reactivate(&fd);
    processing = false;
}

void LFSessionContext::close(){
    Mutex::ScopedLock l(writeLock);
    closing = true;
    if(!processing){
        //allow pending frames to be written to socket
        fd.reqevents = APR_POLLOUT;
        processor->update(&fd);
    }
}

void LFSessionContext::handleClose(){
    handler->closed();
    QPID_LOG(info, "Session closed [" << &socket << "]");
    delete handler;
    delete this;
}

void LFSessionContext::shutdown(){
    socket.close();
    handleClose();
}

void LFSessionContext::init(ConnectionInputHandler* _handler){
    handler = _handler;
    processor->add(&fd);
}

