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
#include <iostream>
#include "qpid/QpidError.h"
#include "qpid/sys/Time.h"
#include "Connector.h"

namespace qpid {
namespace client {

using namespace qpid::sys;
using namespace qpid::framing;
using qpid::QpidError;

Connector::Connector(
    ProtocolVersion ver, bool _debug, uint32_t buffer_size
) : debug(_debug),
    receive_buffer_size(buffer_size),
    send_buffer_size(buffer_size),
    version(ver), 
    closed(true),
    timeout(0),
    idleIn(0), idleOut(0), 
    timeoutHandler(0),
    shutdownHandler(0),
    inbuf(receive_buffer_size), 
    outbuf(send_buffer_size)
{ }

Connector::~Connector(){ }

void Connector::connect(const std::string& host, int port){
    socket = Socket::createTcp();
    socket.connect(host, port);
    closed = false;
    receiver = Thread(this);
}

void Connector::init(){
    ProtocolInitiation init(version);
    writeBlock(&init);
}

void Connector::close(){
    if (markClosed()) {
        socket.close();
        receiver.join();
    }
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

void Connector::send(AMQFrame* f){
    std::auto_ptr<AMQFrame> frame(f);
    AMQBody::shared_ptr body = frame->getBody();
    writeBlock(frame.get());
    if(debug) std::cout << "SENT: " << *frame << std::endl; 
}

void Connector::writeBlock(AMQDataBlock* data){
    Mutex::ScopedLock l(writeLock);
    data->encode(outbuf);
    //transfer data to wire
    outbuf.flip();
    writeToSocket(outbuf.start(), outbuf.available());
    outbuf.clear();
}

void Connector::writeToSocket(char* data, size_t available){
    size_t written = 0;
    while(written < available && !closed){
	ssize_t sent = socket.send(data + written, available-written);
        if(sent > 0) {
            lastOut = now();
            written += sent;
        }
    }
}

void Connector::handleClosed(){
    if (markClosed()) {
        socket.close();
        if(shutdownHandler) shutdownHandler->shutdown();
    }
}

bool Connector::markClosed(){
    if (closed) {
        return false;
    } else {
        closed = true;
        return true;
    }
}

void Connector::checkIdle(ssize_t status){
    if(timeoutHandler){
        AbsTime t = now();
        if(status == Socket::SOCKET_TIMEOUT) {
            if(idleIn && (Duration(lastIn, t) > idleIn)){
                timeoutHandler->idleIn();
            }
        }
        else if(status == 0 || status == Socket::SOCKET_EOF) {
            handleClosed();
        }
        else {
            lastIn = t;
        }
        if(idleOut && (Duration(lastOut, t) > idleOut)){
            timeoutHandler->idleOut();
        }
    }
}

void Connector::setReadTimeout(uint16_t t){
    idleIn = t * TIME_SEC;//t is in secs
    if(idleIn && (!timeout || idleIn < timeout)){
        timeout = idleIn;
        setSocketTimeout();
    }

}

void Connector::setWriteTimeout(uint16_t t){
    idleOut = t * TIME_SEC;//t is in secs
    if(idleOut && (!timeout || idleOut < timeout)){
        timeout = idleOut;
        setSocketTimeout();
    }
}

void Connector::setSocketTimeout(){
    socket.setTimeout(timeout);
}

void Connector::setTimeoutHandler(TimeoutHandler* handler){
    timeoutHandler = handler;
}

void Connector::run(){
    try{
	while(!closed){
            ssize_t available = inbuf.available();
            if(available < 1){
                THROW_QPID_ERROR(INTERNAL_ERROR, "Frame exceeds buffer size.");
            }
            ssize_t received = socket.recv(inbuf.start(), available);
	    checkIdle(received);

	    if(!closed && received > 0){
		inbuf.move(received);
		inbuf.flip();//position = 0, limit = total data read
		
		AMQFrame frame(version);
		while(frame.decode(inbuf)){
                    if(debug) std::cout << "RECV: " << frame << std::endl; 
		    input->received(&frame);
		}
                //need to compact buffer to preserve any 'extra' data
                inbuf.compact();
	    }
	}
    } catch (const std::exception& e) {
	std::cout << e.what() << std::endl;
        handleClosed();
    }
}

}} // namespace qpid::client
