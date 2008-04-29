#include "RdmaIO.h"

#include <iostream>
#include <boost/bind.hpp>

namespace Rdma {
    AsynchIO::AsynchIO(
            QueuePair::intrusive_ptr q,
            int s,
            ReadCallback rc,
            IdleCallback ic,
            FullCallback fc,
            ErrorCallback ec
    ) :
        qp(q),
        dataHandle(*qp, boost::bind(&AsynchIO::dataEvent, this, _1), 0, 0),
        bufferSize(s),
        recvBufferCount(DEFAULT_WR_ENTRIES),
        xmitBufferCount(DEFAULT_WR_ENTRIES),
        outstandingWrites(0),
        readCallback(rc),
        idleCallback(ic),
        fullCallback(fc),
        errorCallback(ec)
    {
        qp->nonblocking();
        qp->notifyRecv();
        qp->notifySend();

        // Prepost some recv buffers before we go any further
        for (int i = 0; i<recvBufferCount; ++i) {
            Buffer* b = qp->createBuffer(bufferSize);
            buffers.push_front(b);
            b->dataCount = b->byteCount;
            qp->postRecv(b);
        }
    }

    AsynchIO::~AsynchIO() {
        // The buffers ptr_deque automatically deletes all the buffers we've allocated
    }

    void AsynchIO::start(Poller::shared_ptr poller) {
        dataHandle.startWatch(poller);
    }

    // TODO: Currently we don't prevent write buffer overrun we just advise
    // when to stop writing.
    void AsynchIO::queueWrite(Buffer* buff) {
        qp->postSend(buff);
        ++outstandingWrites;
        if (outstandingWrites >= xmitBufferCount) {
            fullCallback(*this);
        }
    }

    void AsynchIO::notifyPendingWrite() {
        // Just perform the idle callback (if possible)
        if (outstandingWrites < xmitBufferCount) {
            idleCallback(*this);
        }
    }

    void AsynchIO::queueWriteClose() {
    }

    Buffer* AsynchIO::getBuffer() {
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(bufferQueueLock);
        if (bufferQueue.empty()) {
            Buffer* b = qp->createBuffer(bufferSize);
            buffers.push_front(b);
            b->dataCount = 0;
            return b;
        } else {
            Buffer* b = bufferQueue.front();
            bufferQueue.pop_front();
            b->dataCount = 0;
            b->dataStart = 0;
            return b;
        }

    }

    void AsynchIO::dataEvent(DispatchHandle&) {
        QueuePair::intrusive_ptr q = qp->getNextChannelEvent();

        // If no event do nothing
        if (!q)
            return;

        assert(q == qp);

        // Re-enable notification for queue
        qp->notifySend();
        qp->notifyRecv();

        // Repeat until no more events
        do {
            QueuePairEvent e(qp->getNextEvent());
            if (!e)
                return;

            ::ibv_wc_status status = e.getEventStatus();
            if (status != IBV_WC_SUCCESS) {
                errorCallback(*this);
                return;
            }

            // Test if recv (or recv with imm)
            //::ibv_wc_opcode eventType = e.getEventType();
            Buffer* b = e.getBuffer();
            QueueDirection dir = e.getDirection();
            if (dir == RECV) {
                readCallback(*this, b);
                // At this point the buffer has been consumed so put it back on the recv queue
                qp->postRecv(b);
            } else {
                {
                qpid::sys::ScopedLock<qpid::sys::Mutex> l(bufferQueueLock);
                bufferQueue.push_front(b);
                }
                --outstandingWrites;
                // TODO: maybe don't call idle unless we're low on write buffers
                idleCallback(*this);
            }
        } while (true);
    }

    Listener::Listener(
        const sockaddr& src,
        ConnectedCallback cc,
        ErrorCallback errc,
        DisconnectedCallback dc,
        ConnectionRequestCallback crc
    ) :
        src_addr(src),
        ci(Connection::make()),
        handle(*ci, boost::bind(&Listener::connectionEvent, this, _1), 0, 0),
        connectedCallback(cc),
        errorCallback(errc),
        disconnectedCallback(dc),
        connectionRequestCallback(crc)
    {
        ci->nonblocking();
    }

    void Listener::start(Poller::shared_ptr poller) {
        ci->bind(src_addr);
        ci->listen();
        handle.startWatch(poller);
    }

    void Listener::connectionEvent(DispatchHandle&) {
        ConnectionEvent e(ci->getNextEvent());

        // If (for whatever reason) there was no event do nothing
        if (!e)
            return;

        // Important documentation ommision the new rdma_cm_id
        // you get from CONNECT_REQUEST has the same context info
        // as its parent listening rdma_cm_id
        ::rdma_cm_event_type eventType = e.getEventType();
        Rdma::Connection::intrusive_ptr id = e.getConnection();

        switch (eventType) {
        case RDMA_CM_EVENT_CONNECT_REQUEST: {
            bool accept = true;
            // Extract connection parameters and private data from event
            ::rdma_conn_param conn_param = e.getConnectionParam();

            if (connectionRequestCallback)
                //TODO: pass private data to callback (and accept new private data for accept somehow)
                accept = connectionRequestCallback(id);
            if (accept) {
                // Accept connection
                id->accept(conn_param);
            } else {
                //Reject connection
                id->reject();
            }

            break;
        }
        case RDMA_CM_EVENT_ESTABLISHED:
            connectedCallback(id);
            break;
        case RDMA_CM_EVENT_DISCONNECTED:
            disconnectedCallback(id);
            break;
        case RDMA_CM_EVENT_CONNECT_ERROR:
            errorCallback(id);
            break;
        default:
            std::cerr << "Warning: unexpected response to listen - " << eventType << "\n";
        }
    }

    Connector::Connector(
        const sockaddr& dst,
        ConnectedCallback cc,
        ErrorCallback errc,
        DisconnectedCallback dc,
        RejectedCallback rc
    ) :
        dst_addr(dst),
        ci(Connection::make()),
        handle(*ci, boost::bind(&Connector::connectionEvent, this, _1), 0, 0),
        connectedCallback(cc),
        errorCallback(errc),
        disconnectedCallback(dc),
        rejectedCallback(rc)
    {
        ci->nonblocking();
    }

    void Connector::start(Poller::shared_ptr poller) {
        ci->resolve_addr(dst_addr);
        handle.startWatch(poller);
    }

    void Connector::connectionEvent(DispatchHandle&) {
        ConnectionEvent e(ci->getNextEvent());

        // If (for whatever reason) there was no event do nothing
        if (!e)
            return;

        ::rdma_cm_event_type eventType = e.getEventType();
        switch (eventType) {
        case RDMA_CM_EVENT_ADDR_RESOLVED:
            // RESOLVE_ADDR
            ci->resolve_route();
            break;
        case RDMA_CM_EVENT_ADDR_ERROR:
            // RESOLVE_ADDR
            errorCallback(ci);
            break;
        case RDMA_CM_EVENT_ROUTE_RESOLVED:
            // RESOLVE_ROUTE:
            ci->connect();
            break;
        case RDMA_CM_EVENT_ROUTE_ERROR:
            // RESOLVE_ROUTE:
            errorCallback(ci);
            break;
        case RDMA_CM_EVENT_CONNECT_ERROR:
            // CONNECTING
            errorCallback(ci);
            break;
        case RDMA_CM_EVENT_UNREACHABLE:
            // CONNECTING
            errorCallback(ci);
            break;
        case RDMA_CM_EVENT_REJECTED:
            // CONNECTING
            rejectedCallback(ci);
            break;
        case RDMA_CM_EVENT_ESTABLISHED:
            // CONNECTING
            connectedCallback(ci);
            break;
        case RDMA_CM_EVENT_DISCONNECTED:
            // ESTABLISHED
            disconnectedCallback(ci);
            break;
        default:
            std::cerr << "Warning: unexpected event in connect: " << eventType << "\n";
        }
    }
}
