#include "RdmaIO.h"

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

using qpid::sys::Poller;
using qpid::sys::Dispatcher;

// All the accepted connections
struct ConRec {
    Rdma::Connection::intrusive_ptr connection;
    Rdma::AsynchIO* data;
    int outstandingWrites;
    queue<Rdma::Buffer*> queuedWrites;

    ConRec(Rdma::Connection::intrusive_ptr c) :
        connection(c),
        outstandingWrites(0)
    {}
};

void dataError(Rdma::AsynchIO&) {
    cout << "Data error:\n";
}

void data(ConRec* cr, Rdma::AsynchIO& a, Rdma::Buffer* b) {
    // Echo data back
    Rdma::Buffer* buf = a.getBuffer();
    std::copy(b->bytes+b->dataStart, b->bytes+b->dataStart+b->dataCount, buf->bytes);
    buf->dataCount = b->dataCount;
    if (cr->outstandingWrites < 3*Rdma::DEFAULT_WR_ENTRIES/4) {
        a.queueWrite(buf);
        ++(cr->outstandingWrites);
    } else {
        cr->queuedWrites.push(buf);
    }
}

void idle(ConRec* cr, Rdma::AsynchIO& a) {
    --(cr->outstandingWrites);
    //if (cr->outstandingWrites < Rdma::DEFAULT_WR_ENTRIES/4)
        while (!cr->queuedWrites.empty() && cr->outstandingWrites < 3*Rdma::DEFAULT_WR_ENTRIES/4) {
            Rdma::Buffer* buf = cr->queuedWrites.front();
            cr->queuedWrites.pop();
            a.queueWrite(buf);
            ++(cr->outstandingWrites);
        }
}

void disconnected(Rdma::Connection::intrusive_ptr& ci) {
    ConRec* cr = ci->getContext<ConRec>();
    cr->connection->disconnect();
    delete cr->data;
    delete cr;
    cout << "Disconnected: " << cr << "\n";
}

void connectionError(Rdma::Connection::intrusive_ptr& ci) {
    ConRec* cr = ci->getContext<ConRec>();
    cr->connection->disconnect();
    if (cr) {
        delete cr->data;
        delete cr;
    }
    cout << "Connection error: " << cr << "\n";
}

bool connectionRequest(Rdma::Connection::intrusive_ptr& ci) {
    cout << "Incoming connection: ";

    // For fun reject alternate connection attempts
    static bool x = false;
    x ^= 1;

    // Must create aio here so as to prepost buffers *before* we accept connection
    if (x) {
        ConRec* cr = new ConRec(ci);
        Rdma::AsynchIO* aio =
            new Rdma::AsynchIO(ci->getQueuePair(), 8000,
                boost::bind(data, cr, _1, _2),
                boost::bind(idle, cr, _1),
                dataError);
        ci->addContext(cr);
        cr->data = aio;
        cout << "Accept=>" << cr << "\n";
    } else {
        cout << "Reject\n";
    }

    return x;
}

void connected(Poller::shared_ptr poller, Rdma::Connection::intrusive_ptr& ci) {
    static int cnt = 0;
    ConRec* cr = ci->getContext<ConRec>();
    cout << "Connected: " << cr << "(" << ++cnt << ")\n";

    cr->data->start(poller);
}

int main(int argc, char* argv[]) {
    vector<string> args(&argv[0], &argv[argc]);

    ::sockaddr_in sin;

    int port = (args.size() < 2) ? 20079 : atoi(args[1].c_str());
    cout << "Listening on port: " << port << "\n";

    sin.sin_family      = AF_INET;
    sin.sin_port        = htons(port);
    sin.sin_addr.s_addr = INADDR_ANY;

    try {
        boost::shared_ptr<Poller> p(new Poller());
        Dispatcher d(p);

        Rdma::Listener a((const sockaddr&)(sin),
            boost::bind(connected, p, _1),
            connectionError,
            disconnected,
            connectionRequest);


        a.start(p);
        d.run();
    } catch (Rdma::Exception& e) {
        int err = e.getError();
        cerr << "Error: " << e.what() << "(" << err << ")\n";
    }
}
