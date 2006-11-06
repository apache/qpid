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
#include <iostream>
#include "qpid/sys/APRBase.h"
#include "qpid/sys/Connector.h"
#include "qpid/sys/ThreadFactory.h"
#include "qpid/QpidError.h"

using namespace qpid::sys;
using namespace qpid::sys;
using namespace qpid::framing;
using qpid::QpidError;

Connector::Connector(bool _debug, u_int32_t buffer_size) :
    debug(_debug), 
    receive_buffer_size(buffer_size),
    send_buffer_size(buffer_size),
    closed(true),
    lastIn(0), lastOut(0),
    timeout(0),
    idleIn(0), idleOut(0), 
    timeoutHandler(0),
    shutdownHandler(0),
    inbuf(receive_buffer_size), 
    outbuf(send_buffer_size){

    APRBase::increment();

    CHECK_APR_SUCCESS(apr_pool_create(&pool, NULL));
    CHECK_APR_SUCCESS(apr_socket_create(&socket, APR_INET, SOCK_STREAM, APR_PROTO_TCP, pool));

    threadFactory = new ThreadFactory();
    writeLock = new Monitor();
}

Connector::~Connector(){
    delete receiver;
    delete writeLock;
    delete threadFactory;
    apr_pool_destroy(pool);

    APRBase::decrement();
}

void Connector::connect(const std::string& host, int port){
    apr_sockaddr_t* address;
    CHECK_APR_SUCCESS(apr_sockaddr_info_get(&address, host.c_str(), APR_UNSPEC, port, APR_IPV4_ADDR_OK, pool));
    CHECK_APR_SUCCESS(apr_socket_connect(socket, address));
    closed = false;

    receiver = threadFactory->create(this);
    receiver->start();
}

void Connector::init(ProtocolInitiation* header){
    writeBlock(header);
    delete header;
}

void Connector::close(){
    closed = true;
    CHECK_APR_SUCCESS(apr_socket_close(socket));
    receiver->join();
}

void Connector::setInputHandler(InputHandler* handler){
    input = handler;
}

void Connector::setShutdownHandler(ShutdownHandler* handler){
    shutdownHandler = handler;
}

OutputHandler* Connector::getOutputHandler(){ 
    return this; 
}

void Connector::send(AMQFrame* frame){
    writeBlock(frame);    
    if(debug) std::cout << "SENT: " << *frame << std::endl; 
    delete frame;
}

void Connector::writeBlock(AMQDataBlock* data){
    writeLock->acquire();
    data->encode(outbuf);

    //transfer data to wire
    outbuf.flip();
    writeToSocket(outbuf.start(), outbuf.available());
    outbuf.clear();
    writeLock->release();
}

void Connector::writeToSocket(char* data, size_t available){
    apr_size_t bytes(available);
    apr_size_t written(0);
    while(written < available && !closed){
	apr_status_t status = apr_socket_send(socket, data + written, &bytes);
        if(status == APR_TIMEUP){
            std::cout << "Write request timed out." << std::endl;
        }
        if(bytes == 0){
            std::cout << "Write request wrote 0 bytes." << std::endl;
        }
        lastOut = apr_time_as_msec(apr_time_now());
	written += bytes;
	bytes = available - written;
    }
}

void Connector::checkIdle(apr_status_t status){
    if(timeoutHandler){
        apr_time_t now = apr_time_as_msec(apr_time_now());
        if(APR_STATUS_IS_TIMEUP(status)){
            if(idleIn && (now - lastIn > idleIn)){
                timeoutHandler->idleIn();
            }
        }else if(APR_STATUS_IS_EOF(status)){
            closed = true;
            CHECK_APR_SUCCESS(apr_socket_close(socket));
            if(shutdownHandler) shutdownHandler->shutdown();
        }else{
            lastIn = now;
        }
        if(idleOut && (now - lastOut > idleOut)){
            timeoutHandler->idleOut();
        }
    }
}

void Connector::setReadTimeout(u_int16_t t){
    idleIn = t * 1000;//t is in secs
    if(idleIn && (!timeout || idleIn < timeout)){
        timeout = idleIn;
        setSocketTimeout();
    }

}

void Connector::setWriteTimeout(u_int16_t t){
    idleOut = t * 1000;//t is in secs
    if(idleOut && (!timeout || idleOut < timeout)){
        timeout = idleOut;
        setSocketTimeout();
    }
}

void Connector::setSocketTimeout(){
    //interval is in microseconds, timeout in milliseconds
    //want the interval to be a bit shorter than the timeout, hence multiply
    //by 800 rather than 1000.
    apr_interval_time_t interval(timeout * 800);
    apr_socket_timeout_set(socket, interval);
}

void Connector::setTimeoutHandler(TimeoutHandler* handler){
    timeoutHandler = handler;
}

void Connector::run(){
    try{
	while(!closed){
	    apr_size_t bytes(inbuf.available());
            if(bytes < 1){
                THROW_QPID_ERROR(INTERNAL_ERROR, "Frame exceeds buffer size.");
            }
	    checkIdle(apr_socket_recv(socket, inbuf.start(), &bytes));

	    if(bytes > 0){
		inbuf.move(bytes);
		inbuf.flip();//position = 0, limit = total data read
		
		AMQFrame frame;
		while(frame.decode(inbuf)){
                    if(debug) std::cout << "RECV: " << frame << std::endl; 
		    input->received(&frame);
		}
                //need to compact buffer to preserve any 'extra' data
                inbuf.compact();
	    }
	}
    }catch(QpidError error){
	std::cout << "Error [" << error.code << "] " << error.msg << " (" << error.file << ":" << error.line << ")" << std::endl;
    }
}
