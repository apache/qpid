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
#include "qpid/sys/Thread.h"
#include "qpid/sys/rdma/RdmaIO.h"
#include "qpid/sys/rdma/rdma_exception.h"

#include <arpa/inet.h>

#include <vector>
#include <queue>
#include <string>
#include <iostream>

#include <boost/bind.hpp>

using std::vector;
using std::queue;
using std::string;
using std::cout;
using std::cerr;

using qpid::sys::Thread;
using qpid::sys::SocketAddress;
using qpid::sys::Poller;

// All the accepted connections
namespace qpid {
namespace tests {

struct Buffer {
    char* bytes() const {return bytes_;}
    int32_t byteCount() const {return size;}

    Buffer(const int32_t s):
        bytes_(new char[s]),
        size(s)
    {
    }

    ~Buffer() {
        delete [] bytes_;
    }
private:
    char* bytes_;
    int32_t size;
};

struct ConRec {
    Rdma::Connection::intrusive_ptr connection;
    Rdma::AsynchIO* data;
    queue<Buffer*> queuedWrites;

    ConRec(Rdma::Connection::intrusive_ptr c) :
        connection(c)
    {}
};

void dataError(Rdma::AsynchIO&) {
    cout << "Data error:\n";
}

void idle(ConRec* cr, Rdma::AsynchIO& a) {
    // Need to make sure full is not called as it would reorder messages
    while (!cr->queuedWrites.empty() && a.writable()) {
        Rdma::Buffer* rbuf = a.getSendBuffer();
        if (!rbuf) break;
        Buffer* buf = cr->queuedWrites.front();
        cr->queuedWrites.pop();
        std::copy(buf->bytes(), buf->bytes()+buf->byteCount(), rbuf->bytes());
        rbuf->dataCount(buf->byteCount());
        delete buf;
        a.queueWrite(rbuf);
    }
}

void data(ConRec* cr, Rdma::AsynchIO& a, Rdma::Buffer* b) {
    // Echo data back
    Rdma::Buffer* buf = 0;
    if (cr->queuedWrites.empty() && a.writable()) {
        buf = a.getSendBuffer();
    }
    if (buf) {
        std::copy(b->bytes(), b->bytes()+b->dataCount(), buf->bytes());
        buf->dataCount(b->dataCount());
        a.queueWrite(buf);
    } else {
        Buffer* buf = new Buffer(b->dataCount());
        std::copy(b->bytes(), b->bytes()+b->dataCount(), buf->bytes());
        cr->queuedWrites.push(buf);
        // Try to empty queue
        idle(cr, a);
    }
}

void full(ConRec*, Rdma::AsynchIO&, Rdma::Buffer*) {
    // Shouldn't ever be called
    cout << "!";
}

void drained(Rdma::AsynchIO&) {
    cout << "Drained:\n";
}

void disconnected(const Rdma::Connection::intrusive_ptr& ci) {
    ConRec* cr = ci->getContext<ConRec>();
    cr->connection->disconnect();
    cr->data->drainWriteQueue(drained);
    delete cr;
    cout << "Disconnected: " << cr << "\n";
}

void connectionError(const Rdma::Connection::intrusive_ptr& ci, Rdma::ErrorType) {
    ConRec* cr = ci->getContext<ConRec>();
    cr->connection->disconnect();
    if (cr) {
        cr->data->drainWriteQueue(drained);
        delete cr;
    }
    cout << "Connection error: " << cr << "\n";
}

bool connectionRequest(const Rdma::Connection::intrusive_ptr& ci,  const Rdma::ConnectionParams& cp) {
    cout << "Incoming connection: ";

    // For fun reject alternate connection attempts
    static bool x = false;
    x = true;

    // Must create aio here so as to prepost buffers *before* we accept connection
    if (x) {
        ConRec* cr = new ConRec(ci);
        Rdma::AsynchIO* aio =
            new Rdma::AsynchIO(ci->getQueuePair(),
                cp.rdmaProtocolVersion,
                cp.maxRecvBufferSize, cp.initialXmitCredit, Rdma::DEFAULT_WR_ENTRIES,
                boost::bind(data, cr, _1, _2),
                boost::bind(idle, cr, _1),
                boost::bind(full, cr, _1, _2),
                dataError);
        ci->addContext(cr);
        cr->data = aio;
        cout << "Accept=>" << cr << "\n";
    } else {
        cout << "Reject\n";
    }

    return x;
}

void connected(Poller::shared_ptr poller, const Rdma::Connection::intrusive_ptr& ci) {
    static int cnt = 0;
    ConRec* cr = ci->getContext<ConRec>();
    cout << "Connected: " << cr << "(" << ++cnt << ")\n";

    cr->data->start(poller);
}

}} // namespace qpid::tests

using namespace qpid::tests;

int main(int argc, char* argv[]) {
    vector<string> args(&argv[0], &argv[argc]);

    std::string port = (args.size() < 2) ? "20079" : args[1];
    cout << "Listening on port: " << port << "\n";

    try {
        boost::shared_ptr<Poller> p(new Poller());

        Rdma::Listener a(
            Rdma::ConnectionParams(16384, Rdma::DEFAULT_WR_ENTRIES),
            boost::bind(connected, p, _1),
            connectionError,
            disconnected,
            connectionRequest);


        SocketAddress sa("", port);
        a.start(p, sa);

        // The poller loop blocks all signals so run in its own thread
        Thread t(*p);

        ::pause();
        p->shutdown();
        t.join();
    } catch (Rdma::Exception& e) {
        int err = e.getError();
        cerr << "Error: " << e.what() << "(" << err << ")\n";
    }
}
