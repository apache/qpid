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
#include <sys/Acceptor.h>
#include <sys/SessionHandlerFactory.h>
#include <apr_pools.h>
#include "LFProcessor.h"
#include "LFSessionContext.h"
#include "APRBase.h"
#include "APRPool.h"

namespace qpid {
namespace sys {

class APRAcceptor : public Acceptor
{
  public:
    APRAcceptor(int16_t port, int backlog, int threads, bool trace);
    ~APRAcceptor();
    virtual int16_t getPort() const;
    virtual void run(qpid::sys::SessionHandlerFactory* factory);
    virtual void shutdown();

  private:
    void shutdownImpl();

  private:
    int16_t port;
    bool trace;
    LFProcessor processor;
    apr_socket_t* socket;
    volatile bool running;
    Mutex shutdownLock;
    apr_pool_t* pool;
};

// Define generic Acceptor::create() to return APRAcceptor.
Acceptor::shared_ptr Acceptor::create(int16_t port, int backlog, int threads, bool trace)
{
    return Acceptor::shared_ptr(new APRAcceptor(port, backlog, threads, trace));
}
// Must define Acceptor virtual dtor.
Acceptor::~Acceptor() {
}

APRAcceptor::~APRAcceptor() {
    APRPool::free(pool);
}

APRAcceptor::APRAcceptor(int16_t port_, int backlog, int threads, bool trace_) :
    port(port_),
    trace(trace_),
    processor(threads, 1000, 5000000)
{
    pool = APRPool::get();
    apr_sockaddr_t* address;
    CHECK_APR_SUCCESS(apr_sockaddr_info_get(&address, APR_ANYADDR, APR_UNSPEC, port, APR_IPV4_ADDR_OK, pool));
    CHECK_APR_SUCCESS(apr_socket_create(&socket, APR_INET, SOCK_STREAM, APR_PROTO_TCP, pool));
    CHECK_APR_SUCCESS(apr_socket_opt_set(socket, APR_SO_REUSEADDR, 1));
    CHECK_APR_SUCCESS(apr_socket_bind(socket, address));
    CHECK_APR_SUCCESS(apr_socket_listen(socket, backlog));
}

int16_t APRAcceptor::getPort() const {
    apr_sockaddr_t* address;
    CHECK_APR_SUCCESS(apr_socket_addr_get(&address, APR_LOCAL, socket));
    return address->port;
}

void APRAcceptor::run(SessionHandlerFactory* factory) {
    running = true;
    processor.start();
    std::cout << "Listening on port " << getPort() << "..." << std::endl;
    while(running){
        apr_socket_t* client;
        apr_status_t status = apr_socket_accept(&client, socket, pool);
        if(status == APR_SUCCESS){
            //make this socket non-blocking:
            CHECK_APR_SUCCESS(apr_socket_timeout_set(client, 0));
            CHECK_APR_SUCCESS(apr_socket_opt_set(client, APR_SO_NONBLOCK, 1));
            CHECK_APR_SUCCESS(apr_socket_opt_set(client, APR_TCP_NODELAY, 1));
            CHECK_APR_SUCCESS(apr_socket_opt_set(client, APR_SO_SNDBUF, 32768));
            CHECK_APR_SUCCESS(apr_socket_opt_set(client, APR_SO_RCVBUF, 32768));
            LFSessionContext* session = new LFSessionContext(client, &processor, trace);
            session->init(factory->create(session));
        }else{
            Mutex::ScopedLock locker(shutdownLock);                
            if(running) {
                if(status != APR_EINTR){
                    std::cout << "ERROR: " << get_desc(status) << std::endl;
                }
                shutdownImpl();
            }
        }
    }
}

void APRAcceptor::shutdown() {
    Mutex::ScopedLock locker(shutdownLock);                
    if (running) {
        shutdownImpl();
    }
}

void APRAcceptor::shutdownImpl() {
    Mutex::ScopedLock locker(shutdownLock);                
    running = false;
    processor.stop();
    CHECK_APR_SUCCESS(apr_socket_close(socket));
}


}}
