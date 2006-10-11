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
#include "BlockingAPRAcceptor.h"
#include "APRBase.h"
#include "APRThreadFactory.h"

using namespace qpid::concurrent;
using namespace qpid::framing;
using namespace qpid::io;

BlockingAPRAcceptor::BlockingAPRAcceptor(bool _debug, int c) :
    debug(_debug),
    threadFactory(new APRThreadFactory()),
    connectionBacklog(c)
{
    APRBase::increment();
    CHECK_APR_SUCCESS(apr_pool_create(&apr_pool, NULL));
}

void BlockingAPRAcceptor::bind(int port, SessionHandlerFactory* factory){
    apr_sockaddr_t* address;
    CHECK_APR_SUCCESS(apr_sockaddr_info_get(&address, APR_ANYADDR, APR_UNSPEC, port, APR_IPV4_ADDR_OK, apr_pool));
    CHECK_APR_SUCCESS(apr_socket_create(&socket, APR_INET, SOCK_STREAM, APR_PROTO_TCP, apr_pool));
    CHECK_APR_SUCCESS(apr_socket_bind(socket, address));
    CHECK_APR_SUCCESS(apr_socket_listen(socket, connectionBacklog));
    running = true;
    std::cout << "Listening on port " << port << "..." << std::endl;
    while(running){
        apr_socket_t* client;
        apr_status_t status = apr_socket_accept(&client, socket, apr_pool);
        if(status == APR_SUCCESS){
            //configure socket:
            CHECK_APR_SUCCESS(apr_socket_timeout_set(client, 1000000/* i.e. 1 sec*/));
            CHECK_APR_SUCCESS(apr_socket_opt_set(client, APR_TCP_NODELAY, 1));
            CHECK_APR_SUCCESS(apr_socket_opt_set(client, APR_SO_SNDBUF, 32768));
            CHECK_APR_SUCCESS(apr_socket_opt_set(client, APR_SO_RCVBUF, 32768));
            
            BlockingAPRSessionContext* session = new BlockingAPRSessionContext(client, threadFactory, this, debug);
            session->init(factory->create(session));
            sessions.push_back(session);
        }else{
            running = false;
            if(status != APR_EINTR){
                std::cout << "ERROR: " << get_desc(status) << std::endl;
            }
        }
    }
    for(iterator i = sessions.begin(); i < sessions.end(); i++){
        (*i)->shutdown();
    }

    CHECK_APR_SUCCESS(apr_socket_close(socket));
}

BlockingAPRAcceptor::~BlockingAPRAcceptor(){
    delete threadFactory;
    apr_pool_destroy(apr_pool);
    APRBase::decrement();
}


void BlockingAPRAcceptor::closed(BlockingAPRSessionContext* session){
    sessions.erase(find(sessions.begin(), sessions.end(), session));
    delete this;
}

