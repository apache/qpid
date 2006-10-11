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
#include <assert.h>
#include <iostream>
#include "BlockingAPRSessionContext.h"
#include "BlockingAPRAcceptor.h"
#include "APRBase.h"
#include "QpidError.h"

using namespace qpid::concurrent;
using namespace qpid::framing;
using namespace qpid::io;


BlockingAPRSessionContext::BlockingAPRSessionContext(apr_socket_t* _socket, 
                                                     ThreadFactory* factory, 
                                                     BlockingAPRAcceptor* _acceptor,
                                                     bool _debug) 
    : socket(_socket), 
      debug(_debug),
      handler(0),
      acceptor(_acceptor),
      inbuf(65536),
      outbuf(65536),
      closed(false){

    reader = new Reader(this);
    writer = new Writer(this);

    rThread = factory->create(reader);
    wThread = factory->create(writer);
}            

BlockingAPRSessionContext::~BlockingAPRSessionContext(){
    delete reader;
    delete writer;

    delete rThread;
    delete wThread;

    delete handler;
}

void BlockingAPRSessionContext::read(){
    try{
        bool initiated(false);
	while(!closed){
	    apr_size_t bytes(inbuf.available());
            if(bytes < 1){
                THROW_QPID_ERROR(INTERNAL_ERROR, "Frame exceeds buffer size.");
            }
	    apr_status_t s = apr_socket_recv(socket, inbuf.start(), &bytes);
            if(APR_STATUS_IS_TIMEUP(s)){
                //timed out, check closed on loop
            }else if(APR_STATUS_IS_EOF(s) || bytes == 0){
                closed = true;
            }else{
		inbuf.move(bytes);
		inbuf.flip();
		
                if(!initiated){
                    ProtocolInitiation* protocolInit = new ProtocolInitiation();
                    if(protocolInit->decode(inbuf)){
                        handler->initiated(protocolInit);
                        if(debug) std::cout << "RECV: [" << &socket << "]: Initialised " << std::endl; 
                        initiated = true;
                    }
                }else{
                    AMQFrame frame;
                    while(frame.decode(inbuf)){
                        if(debug) std::cout << "RECV: [" << &socket << "]:" << frame << std::endl; 
                        handler->received(&frame);
                    }
                }
                //need to compact buffer to preserve any 'extra' data
                inbuf.compact();
	    }
	}

        //close socket 
    }catch(qpid::QpidError error){
	std::cout << "Error [" << error.code << "] " << error.msg << " (" << error.file << ":" << error.line << ")" << std::endl;
    }
}

void BlockingAPRSessionContext::write(){
    while(!closed){
        //get next frame
        outlock.acquire();
        while(outframes.empty() && !closed){
            outlock.wait();
        }
        if(!closed){
            AMQFrame* frame = outframes.front();                
            outframes.pop();
            outlock.release();
            
            //encode
            frame->encode(outbuf);
            if(debug) std::cout << "SENT [" << &socket << "]:" << *frame << std::endl; 
            delete frame;
            outbuf.flip();
            
            //write from outbuf to socket
            char* data = outbuf.start();
            const int available = outbuf.available();
            int written = 0;
            apr_size_t bytes = available;
            while(available > written){
                apr_status_t s = apr_socket_send(socket, data + written, &bytes);
                assert(s == 0); // TODO aconway 2006-10-05: Error Handling.
                written += bytes;
                bytes = available - written;
            }
            outbuf.clear();
        }else{
            outlock.release();
        }
    }
}

void BlockingAPRSessionContext::send(AMQFrame* frame){
    if(!closed){
        outlock.acquire();
        bool was_empty(outframes.empty());
        outframes.push(frame);
        if(was_empty){
            outlock.notify();
        }
        outlock.release();
    }else{
        std::cout << "WARNING: Session closed[" << &socket << "], dropping frame. " << &frame << std::endl; 
    }
}

void BlockingAPRSessionContext::init(SessionHandler* _handler){
    handler = _handler;
    rThread->start();
    wThread->start();
}

void BlockingAPRSessionContext::close(){
    closed = true;
    wThread->join();
    CHECK_APR_SUCCESS(apr_socket_close(socket));
    if(debug) std::cout << "RECV: [" << &socket << "]: Closed " << std::endl; 
    handler->closed();
    acceptor->closed(this);
    delete this;
}

void BlockingAPRSessionContext::shutdown(){
    closed = true;
    outlock.acquire();
    outlock.notify();
    outlock.release();

    wThread->join();
    CHECK_APR_SUCCESS(apr_socket_close(socket));
    rThread->join();
    handler->closed();
    delete this;
}
