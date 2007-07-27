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
#include "qpid/log/Statement.h"
#include "qpid/sys/Acceptor.h"
#include "qpid/sys/ConnectionInputHandlerFactory.h"
#include "qpid/sys/Mutex.h"
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
    virtual uint16_t getPort() const;
    virtual std::string getHost() const;
    virtual void run(qpid::sys::ConnectionInputHandlerFactory* factory);
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
};

// Define generic Acceptor::create() to return APRAcceptor.
Acceptor::shared_ptr Acceptor::create(int16_t port, int backlog, int threads, bool trace)
{
    return Acceptor::shared_ptr(new APRAcceptor(port, backlog, threads, trace));
}

APRAcceptor::APRAcceptor(int16_t port_, int backlog, int threads, bool trace_) :
    port(port_),
    trace(trace_),
    processor(APRPool::get(), threads, 1000, 5000000),
    running(false)
{
    apr_sockaddr_t* address;
    CHECK_APR_SUCCESS(apr_sockaddr_info_get(&address, APR_ANYADDR, APR_UNSPEC, port, APR_IPV4_ADDR_OK, APRPool::get()));
    CHECK_APR_SUCCESS(apr_socket_create(&socket, APR_INET, SOCK_STREAM, APR_PROTO_TCP, APRPool::get()));
    CHECK_APR_SUCCESS(apr_socket_opt_set(socket, APR_SO_REUSEADDR, 1));
    CHECK_APR_SUCCESS(apr_socket_bind(socket, address));
    CHECK_APR_SUCCESS(apr_socket_listen(socket, backlog));
}

std::string APRAcceptor::getHost() const {
    apr_sockaddr_t* address;
    CHECK_APR_SUCCESS(apr_socket_addr_get(&address, APR_LOCAL, socket));
    return address->hostname;
}

uint16_t APRAcceptor::getPort() const {
    apr_sockaddr_t* address;
    CHECK_APR_SUCCESS(apr_socket_addr_get(&address, APR_LOCAL, socket));
    return address->port;
}

void APRAcceptor::run(ConnectionInputHandlerFactory* factory) {
    running = true;
    processor.start();
    QPID_LOG(info, "Listening on port " << getPort());
    while(running) {
            apr_socket_t* client;
            apr_status_t status = apr_socket_accept(&client, socket, APRPool::get());
        if(status == APR_SUCCESS){
            //make this socket non-blocking:
            CHECK_APR_SUCCESS(apr_socket_timeout_set(client, 0));
            CHECK_APR_SUCCESS(apr_socket_opt_set(client, APR_SO_NONBLOCK, 1));
            CHECK_APR_SUCCESS(apr_socket_opt_set(client, APR_TCP_NODELAY, 1));
            CHECK_APR_SUCCESS(apr_socket_opt_set(client, APR_SO_SNDBUF, 32768));
            CHECK_APR_SUCCESS(apr_socket_opt_set(client, APR_SO_RCVBUF, 32768));
            LFSessionContext* session = new LFSessionContext(APRPool::get(), client, &processor, trace);
            session->init(factory->create(session));
        }else{
            Mutex::ScopedLock locker(shutdownLock);                
            if(running) {
                if(status != APR_EINTR){
                    QPID_LOG(error, get_desc(status));
                }
                shutdownImpl();
            }
        }
    }
}

void APRAcceptor::shutdown() {
    Mutex::ScopedLock locker(shutdownLock);                
    if (running) 
        shutdownImpl();
}

void APRAcceptor::shutdownImpl() {
    running = false;
    processor.stop();
    CHECK_APR_SUCCESS(apr_socket_close(socket));
}


}}
