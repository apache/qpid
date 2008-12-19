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
#include "qpid/sys/Poller.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Mutex.h"
#include "qpid/client/Connection.h"
#include "qpid/log/Statement.h"

#include <algorithm>

/**
 * A simple socket proxy that forwards to another socket. 
 * Used between client & local broker to simulate network failures.
 */
class SocketProxy : private qpid::sys::Runnable
{
  public:
    /** Connect to connectPort on host, start a forwarding thread.
     * Listen for connection on getPort().
     */
    SocketProxy(int connectPort, const std::string host="localhost")
        : closed(false), port(listener.listen()), dropClient(), dropServer()
    {
        client.connect(host, connectPort);
        thread = qpid::sys::Thread(static_cast<qpid::sys::Runnable*>(this));
    }
    
    ~SocketProxy() { close(); }

    /** Simulate a network disconnect. */
    void close() {
        {
            qpid::sys::Mutex::ScopedLock l(lock);
            if (closed) return;
            closed=true;
        }
        poller.shutdown();
        if (thread.id() != qpid::sys::Thread::current().id())
        thread.join();
        client.close();
    }

    /** Simulate lost packets, drop data from client */
    void dropClientData(bool drop=true) { dropClient=drop; }

    /** Simulate lost packets, drop data from server */
    void dropServerData(bool drop=true) { dropServer=drop; }

    bool isClosed() const {
        qpid::sys::Mutex::ScopedLock l(lock);
        return closed;
    }

    uint16_t getPort() const { return port; }
    
  private:
    static void throwErrno(const std::string& msg) {
        throw qpid::Exception(msg+":"+qpid::sys::strError(errno));
    }
    static void throwIf(bool condition, const std::string& msg) {
        if (condition) throw qpid::Exception(msg);
    }

    void run() {
        std::auto_ptr<qpid::sys::Socket> server;
        try {
            qpid::sys::PollerHandle listenerHandle(listener);
            poller.addFd(listenerHandle, qpid::sys::Poller::INPUT);
            qpid::sys::Poller::Event event = poller.wait();
            throwIf(event.type == qpid::sys::Poller::SHUTDOWN, "SocketProxy: Closed by close()");
            throwIf(!(event.type == qpid::sys::Poller::READABLE && event.handle == &listenerHandle), "SocketProxy: Accept failed");

            poller.delFd(listenerHandle);
            server.reset(listener.accept(0, 0));

            // Pump data between client & server sockets
            qpid::sys::PollerHandle clientHandle(client);
            qpid::sys::PollerHandle serverHandle(*server);
            poller.addFd(clientHandle, qpid::sys::Poller::INPUT);
            poller.addFd(serverHandle, qpid::sys::Poller::INPUT);
            char buffer[1024];
            for (;;) {
                qpid::sys::Poller::Event event = poller.wait();
                throwIf(event.type == qpid::sys::Poller::SHUTDOWN, "SocketProxy: Closed by close()");
                throwIf(event.type == qpid::sys::Poller::DISCONNECTED, "SocketProxy: client/server disconnected");
                if (event.handle == &serverHandle) {
                    ssize_t n = server->read(buffer, sizeof(buffer));
                    if (!dropServer) client.write(buffer, n);
                    poller.rearmFd(serverHandle);
                } else if (event.handle == &clientHandle) {
                    ssize_t n = client.read(buffer, sizeof(buffer));
                    if (!dropClient) server->write(buffer, n);
                    poller.rearmFd(clientHandle);
                } else {
                    throwIf(true, "SocketProxy: No handle ready");
                }
            }
        }
        catch (const std::exception& e) {
            QPID_LOG(debug, "SocketProxy::run exception: " << e.what());
        }
        try {
        if (server.get()) server->close();
        close(); 
    }
        catch (const std::exception& e) {
            QPID_LOG(debug, "SocketProxy::run exception in client/server close()" << e.what());
        }
    }

    mutable qpid::sys::Mutex lock;
    bool closed;
    qpid::sys::Poller poller;
    qpid::sys::Socket client, listener;
    uint16_t port;
    qpid::sys::Thread thread;
    bool dropClient, dropServer;
};

#endif
