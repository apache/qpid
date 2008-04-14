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
        : closed(false), port(listener.listen())
    {
        int r=::pipe(closePipe);
        if (r<0) throwErrno(QPID_MSG("::pipe returned " << r));
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
        write(closePipe[1], this, 1); // Random byte to closePipe
        thread.join();
        client.close();
        ::close(closePipe[0]);
        ::close(closePipe[1]);
    }

    bool isClosed() const {
        qpid::sys::Mutex::ScopedLock l(lock);
        return closed;
    }

    uint16_t getPort() const { return port; }
    
  private:
    static void throwErrno(const std::string& msg) {
        throw qpid::Exception(msg+":"+qpid::strError(errno));
    }
    static void throwIf(bool condition, const std::string& msg) {
        if (condition) throw qpid::Exception(msg);
    }
    
    struct FdSet : fd_set {
        FdSet() : maxFd(0) { clear(); }
        void clear() { FD_ZERO(this); }
        void set(int fd) { FD_SET(fd, this); maxFd = std::max(maxFd, fd); }
        bool isSet(int fd) const { return FD_ISSET(fd, this); }
        bool operator[](int fd) const { return isSet(fd); }

        int maxFd;
    };

    enum { RD=1, WR=2, ER=4 };
    
    struct Selector {
        FdSet rd, wr, er;

        void set(int fd, int sets) {
            if (sets & RD) rd.set(fd);
            if (sets & WR) wr.set(fd);
            if (sets & ER) er.set(fd);
        }
        
        int select() {
            for (;;) {
                int maxFd = std::max(rd.maxFd, std::max(wr.maxFd, er.maxFd));
                int r = ::select(maxFd + 1, &rd, &wr, &er, NULL);
                if (r == -1 && errno == EINTR) continue;
                if (r < 0) throwErrno(QPID_MSG("select returned " <<r));
                return r;
            }
        }
    };

    void run() {
        std::auto_ptr<qpid::sys::Socket> server;
        try {
            // Accept incoming connections, watch closePipe.
            Selector accept;
            accept.set(listener.toFd(), RD|ER);
            accept.set(closePipe[0], RD|ER);
            accept.select();
            throwIf(accept.rd[closePipe[0]], "Closed by close()");
            throwIf(!accept.rd[listener.toFd()],"Accept failed");
            server.reset(listener.accept(0, 0));

            // Pump data between client & server sockets, watch closePipe.
            char buffer[1024];
            for (;;) {
                Selector select;
                select.set(server->toFd(), RD|ER);
                select.set(client.toFd(), RD|ER);
                select.set(closePipe[0], RD|ER);
                select.select();
                throwIf(select.rd[closePipe[0]], "Closed by close()");
                // Read even if fd is in error to throw a useful exception.
                bool gotData=false;
                if (select.rd[server->toFd()] || select.er[server->toFd()]) {
                    client.write(buffer, server->read(buffer, sizeof(buffer)));
                    gotData=true;
                }
                if (select.rd[client.toFd()] || select.er[client.toFd()]) {
                    server->write(buffer, client.read(buffer, sizeof(buffer)));
                    gotData=true;
                }
                throwIf(!gotData, "No data from select()");
            }
        }
        catch (const std::exception& e) {
            QPID_LOG(debug, "SocketProxy::run exiting: " << e.what());
        }
        if (server.get()) server->close();
        close(); 
    }

    mutable qpid::sys::Mutex lock;
    bool closed;
    qpid::sys::Socket client, listener;
    uint16_t port;
    int closePipe[2];
    qpid::sys::Thread thread;
};

#endif
