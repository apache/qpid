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
#include <QpidError.h>
#include <sys/Time.h>
#include "Connector.h"

using namespace qpid::sys;
using namespace qpid::client;
using namespace qpid::framing;
using qpid::QpidError;

Connector::Connector(const qpid::framing::ProtocolVersion& pVersion, bool _debug, u_int32_t buffer_size) :
    debug(_debug),
    receive_buffer_size(buffer_size),
    send_buffer_size(buffer_size),
    version(pVersion), 
    closed(true),
    lastIn(0), lastOut(0),
    timeout(0),
    idleIn(0), idleOut(0), 
    timeoutHandler(0),
    shutdownHandler(0),
    inbuf(receive_buffer_size), 
    outbuf(send_buffer_size){ }

Connector::~Connector(){ }

void Connector::connect(const std::string& host, int port, bool tcpNoDelay){
    socket = Socket::createTcp();
    if (tcpNoDelay) {
        socket.setTcpNoDelay(true);
    }
    socket.connect(host, port);
    closed = false;
    receiver = Thread(this);
}

void Connector::init(ProtocolInitiation* header){
    writeBlock(header);
    delete header;
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

void Connector::send(AMQDataBlock* frame){
    writeBlock(frame);    
    if(debug) std::cout << "SENT: " << *frame << std::endl; 
    delete frame;
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
            lastOut = now() * TIME_MSEC;
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
        Time t = now() * TIME_MSEC;
        if(status == Socket::SOCKET_TIMEOUT) {
            if(idleIn && (t - lastIn > idleIn)){
                timeoutHandler->idleIn();
            }
        }else if(status == Socket::SOCKET_EOF){
            handleClosed();
        }else{
            lastIn = t;
        }
        if(idleOut && (t - lastOut > idleOut)){
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
    socket.setTimeout(timeout*TIME_MSEC);
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
    }catch(QpidError error){
	std::cout << "Error [" << error.code << "] " << error.msg
                  << " (" << error.location.file << ":" << error.location.line
                  << ")" << std::endl;
        handleClosed();
    }
}
