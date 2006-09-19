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
#include "LFAcceptor.h"
#include "APRBase.h"

using namespace qpid::concurrent;
using namespace qpid::io;

LFAcceptor::LFAcceptor(bool _debug, int c, int worker_threads, int m) : processor(aprPool.pool, worker_threads, 1000, 5000000),
                                                                        connectionBacklog(c),
                                                                        max_connections_per_processor(m), 
                                                                        debug(_debug){

}


void LFAcceptor::bind(int port, SessionHandlerFactory* factory){
    apr_socket_t* socket;
    apr_sockaddr_t* address;
    CHECK_APR_SUCCESS(apr_sockaddr_info_get(&address, APR_ANYADDR, APR_UNSPEC, port, APR_IPV4_ADDR_OK, aprPool.pool));
    CHECK_APR_SUCCESS(apr_socket_create(&socket, APR_INET, SOCK_STREAM, APR_PROTO_TCP, aprPool.pool));
    CHECK_APR_SUCCESS(apr_socket_opt_set(socket, APR_SO_REUSEADDR, 1));
    CHECK_APR_SUCCESS(apr_socket_bind(socket, address));
    CHECK_APR_SUCCESS(apr_socket_listen(socket, connectionBacklog));
    running = true;
    processor.start();

    std::cout << "Listening on port " << port << "..." << std::endl;
    while(running){
        apr_socket_t* client;
        apr_status_t status = apr_socket_accept(&client, socket, aprPool.pool);
        if(status == APR_SUCCESS){
            //make this socket non-blocking:
            CHECK_APR_SUCCESS(apr_socket_timeout_set(client, 0));
            CHECK_APR_SUCCESS(apr_socket_opt_set(client, APR_SO_NONBLOCK, 1));
            CHECK_APR_SUCCESS(apr_socket_opt_set(client, APR_TCP_NODELAY, 1));
            CHECK_APR_SUCCESS(apr_socket_opt_set(client, APR_SO_SNDBUF, 32768));
            CHECK_APR_SUCCESS(apr_socket_opt_set(client, APR_SO_RCVBUF, 32768));
            LFSessionContext* session = new LFSessionContext(aprPool.pool, client, &processor, debug);
            session->init(factory->create(session));
        }else{
            running = false;
            if(status != APR_EINTR){
                std::cout << "ERROR: " << get_desc(status) << std::endl;
            }
        }
    }

    processor.stop();
    CHECK_APR_SUCCESS(apr_socket_close(socket));
}


LFAcceptor::~LFAcceptor(){
}

LFAcceptor::APRPool::APRPool(){
    APRBase::increment();
    CHECK_APR_SUCCESS(apr_pool_create(&pool, NULL));
}

LFAcceptor::APRPool::~APRPool(){
    apr_pool_destroy(pool);
    APRBase::decrement();
}
