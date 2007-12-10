#ifndef SOCKETPROXY_H
#define SOCKETPROXY_H

/*
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

#include "qpid/sys/Socket.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Thread.h"

/**
 * A simple socket proxy that forwards to another socket. Used between
 * client & broker to simulate network failures.
 */
struct SocketProxy : public qpid::sys::Runnable
{
    int port;             // Port bound to server socket.
    qpid::sys::Socket client, server; // Client & server sockets.

    SocketProxy(const std::string& host, int port) { init(host,port); }
    SocketProxy(int port) { init("localhost",port); }

    ~SocketProxy() { client.close(); server.close(); thread.join(); }
    
  private:

    void init(const std::string& host, int port) {
        client.connect(host,port);
        port = server.listen();
        thread=qpid::sys::Thread(this);
    }

    void run() {
        do {
            ssize_t recv = server.recv(buffer, sizeof(buffer));
            if (recv <= 0) return;
            ssize_t sent=client.send(buffer, recv);
            if (sent < 0) return;
            assert(sent == recv); // Assumes we can send as we receive.
        } while (true);
    }

    qpid::sys::Thread thread;
    char buffer[64*1024];
};

/** A local client connection via a socket proxy. */
struct ProxyConnection : public qpid::client::Connection {
    SocketProxy proxy;
    qpid::client::Session_0_10 session;
    
    ProxyConnection(const std::string& host, int port) : proxy(port) {
        open(host, proxy.port);
        session=newSession();
    }

    ProxyConnection(int port) : proxy(port) {
        open("localhost", proxy.port);
        session=newSession();
    }
};

#endif
