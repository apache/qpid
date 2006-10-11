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
#include "LFSessionContext.h"
#include "APRBase.h"
#include "QpidError.h"
#include <assert.h>

using namespace qpid::concurrent;
using namespace qpid::io;
using namespace qpid::framing;

LFSessionContext::LFSessionContext(apr_pool_t* _pool, apr_socket_t* _socket, 
                                   LFProcessor* const _processor,
                                   bool _debug) :
    debug(_debug),
    socket(_socket),
    initiated(false),
    in(32768),
    out(32768),
    processor(_processor),
    processing(false),
    closing(false),
    reading(0),
    writing(0)
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
    assert(!reading);           // No concurrent read. 
    reading = APRThread::currentThread();

    socket.read(in);
    in.flip();
    if(initiated){
        AMQFrame frame;
        while(frame.decode(in)){
            if(debug) log("RECV", &frame);
            handler->received(&frame);
        }
    }else{
        ProtocolInitiation protocolInit;
        if(protocolInit.decode(in)){
            handler->initiated(&protocolInit);
            initiated = true;
            if(debug) std::cout << "INIT [" << &socket << "]" << std::endl;
        }
    }
    in.compact();

    reading = 0;
}

void LFSessionContext::write(){
    assert(!writing);           // No concurrent writes.
    writing = APRThread::currentThread();

    bool done = isClosed();
    while(!done){
        if(out.available() > 0){
            socket.write(out);
            if(out.available() > 0){
                writing = 0;

                //incomplete write, leave flags to receive notification of readiness to write
                done = true;//finished processing for now, but write is still in progress
            }
        }else{
            //do we have any frames to write?
            writeLock.acquire();
            if(!framesToWrite.empty()){
                out.clear();
                bool encoded(false);
                AMQFrame* frame = framesToWrite.front();
                while(frame && out.available() >= frame->size()){
                    encoded = true;
                    frame->encode(out);
                    if(debug) log("SENT", frame);
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

                writing = 0;

                if(closing){
                    socket.close();
                }
            }
            writeLock.release();
        }
    }
}

void LFSessionContext::send(AMQFrame* frame){
    writeLock.acquire();
    if(!closing){
        framesToWrite.push(frame);
        if(!(fd.reqevents & APR_POLLOUT)){
            fd.reqevents |= APR_POLLOUT;
            if(!processing){
                processor->update(&fd);
            }
        }
    }
    writeLock.release();
}

void LFSessionContext::startProcessing(){
    writeLock.acquire();
    processing = true;
    processor->deactivate(&fd);
    writeLock.release();
}

void LFSessionContext::stopProcessing(){
    writeLock.acquire();
    processor->reactivate(&fd);
    processing = false;
    writeLock.release();
}

void LFSessionContext::close(){
    closing = true;
    writeLock.acquire();
    if(!processing){
        //allow pending frames to be written to socket
        fd.reqevents = APR_POLLOUT;
        processor->update(&fd);
    }
    writeLock.release();
}

void LFSessionContext::handleClose(){
    handler->closed();
    std::cout << "Session closed [" << &socket << "]" << std::endl;
    delete handler;
    delete this;
}

void LFSessionContext::shutdown(){
    socket.close();
    handleClose();
}

void LFSessionContext::init(SessionHandler* _handler){
    handler = _handler;
    processor->add(&fd);
}

void LFSessionContext::log(const std::string& desc, AMQFrame* const frame){
    logLock.acquire();
    std::cout << desc << " [" << &socket << "]: " << *frame << std::endl;
    logLock.release();
}

APRMonitor LFSessionContext::logLock;
