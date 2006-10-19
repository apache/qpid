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
#include "qpid/concurrent/APRBase.h"
#include "qpid/io/APRSocket.h"
#include <assert.h>
#include <iostream>

using namespace qpid::io;
using namespace qpid::framing;
using namespace qpid::concurrent;

APRSocket::APRSocket(apr_socket_t* _socket) : socket(_socket), closed(false){

}

void APRSocket::read(qpid::framing::Buffer& buffer){
    apr_size_t bytes;
    bytes = buffer.available();
    apr_status_t s = apr_socket_recv(socket, buffer.start(), &bytes);
    buffer.move(bytes);
    if(APR_STATUS_IS_TIMEUP(s)){
        //timed out
    }else if(APR_STATUS_IS_EOF(s)){
        close();
    }
}

void APRSocket::write(qpid::framing::Buffer& buffer){
    apr_size_t bytes;
    do{
        bytes = buffer.available();
        apr_socket_send(socket, buffer.start(), &bytes);
        buffer.move(bytes);    
    }while(bytes > 0);
}

void APRSocket::close(){
    if(!closed){
        std::cout << "Closing socket " << socket << "@" << this << std::endl;
        CHECK_APR_SUCCESS(apr_socket_close(socket));
        closed = true;
    }
}

bool APRSocket::isOpen(){
    return !closed;
}

u_int8_t APRSocket::read(){
    char data[1];
    apr_size_t bytes = 1;
    apr_status_t s = apr_socket_recv(socket, data, &bytes);
    if(APR_STATUS_IS_EOF(s) || bytes == 0){
        return 0;
    }else{
        return *data;
    }
}

APRSocket::~APRSocket(){
}
