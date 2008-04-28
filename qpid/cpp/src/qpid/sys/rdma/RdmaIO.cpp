#include "RdmaIO.h"

#include <iostream>
#include <boost/bind.hpp>

namespace Rdma {
    AsynchIO::AsynchIO(
            QueuePair::intrusive_ptr q,
            int s,
            ReadCallback rc,
            IdleCallback ic,
            ErrorCallback ec
    ) :
        qp(q),
        dataHandle(*qp, boost::bind(&AsynchIO::dataEvent, this, _1), 0, 0),
        bufferSize(s),
        recvBufferCount(DEFAULT_WR_ENTRIES),
        readCallback(rc),
        idleCallback(ic),
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

    void AsynchIO::queueReadBuffer(Buffer*) {
    }

    void AsynchIO::queueWrite(Buffer* buff) {
        qp->postSend(buff);
    }

    void AsynchIO::notifyPendingWrite() {
    }

    void AsynchIO::queueWriteClose() {
    }

    Buffer* AsynchIO::getBuffer() {
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
                bufferQueue.push_front(b);
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
        connectionRequestCallback(crc),
        state(IDLE)
    {
        ci->nonblocking();
    }

    void Listener::start(Poller::shared_ptr poller) {
        ci->bind(src_addr);
        ci->listen();
        state = LISTENING;
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
        rejectedCallback(rc),
        state(IDLE)
    {
        ci->nonblocking();
    }

    void Connector::start(Poller::shared_ptr poller) {
        ci->resolve_addr(dst_addr);
        state = RESOLVE_ADDR;
        handle.startWatch(poller);
    }

    void Connector::connectionEvent(DispatchHandle&) {
        ConnectionEvent e(ci->getNextEvent());

        // If (for whatever reason) there was no event do nothing
        if (!e)
            return;

        ::rdma_cm_event_type eventType = e.getEventType();
#if 1
        switch (eventType) {
        case RDMA_CM_EVENT_ADDR_RESOLVED:
            // RESOLVE_ADDR
            state = RESOLVE_ROUTE;
            ci->resolve_route();
            break;
        case RDMA_CM_EVENT_ADDR_ERROR:
            // RESOLVE_ADDR
            state = ERROR;
            errorCallback(ci);
            break;
        case RDMA_CM_EVENT_ROUTE_RESOLVED:
            // RESOLVE_ROUTE:
            state = CONNECTING;
            ci->connect();
            break;
        case RDMA_CM_EVENT_ROUTE_ERROR:
            // RESOLVE_ROUTE:
            state = ERROR;
            errorCallback(ci);
            break;
        case RDMA_CM_EVENT_CONNECT_ERROR:
            // CONNECTING
            state = ERROR;
            errorCallback(ci);
            break;
        case RDMA_CM_EVENT_UNREACHABLE:
            // CONNECTING
            state = ERROR;
            errorCallback(ci);
            break;
        case RDMA_CM_EVENT_REJECTED:
            // CONNECTING
            state = REJECTED;
            rejectedCallback(ci);
            break;
        case RDMA_CM_EVENT_ESTABLISHED:
            // CONNECTING
            state = ESTABLISHED;
            connectedCallback(ci);
            break;
        case RDMA_CM_EVENT_DISCONNECTED:
            // ESTABLISHED
            state = DISCONNECTED;
            disconnectedCallback(ci);
            break;
        default:
            std::cerr << "Warning: unexpected event in " << state << " state - " << eventType << "\n";
            state = ERROR;
        }
#else
        switch (state) {
        case IDLE:
            std::cerr << "Warning: event in IDLE state\n";
            break;
        case RESOLVE_ADDR:
            switch (eventType) {
            case RDMA_CM_EVENT_ADDR_RESOLVED:
                state = RESOLVE_ROUTE;
                ci->resolve_route();
                break;
            case RDMA_CM_EVENT_ADDR_ERROR:
                state = ERROR;
                errorCallback(ci);
                break;
            default:
                state = ERROR;
                std::cerr << "Warning: unexpected response to resolve_addr - " << eventType << "\n";
            }
            break;
        case RESOLVE_ROUTE:
            switch (eventType) {
            case RDMA_CM_EVENT_ROUTE_RESOLVED:
                state = CONNECTING;
                ci->connect();
                break;
            case RDMA_CM_EVENT_ROUTE_ERROR:
                state = ERROR;
                errorCallback(ci);
                break;
            default:
                state = ERROR;
                std::cerr << "Warning: unexpected response to resolve_route - " << eventType << "\n";
            }
            break;
        case CONNECTING:
            switch (eventType) {
            case RDMA_CM_EVENT_CONNECT_RESPONSE:
                std::cerr << "connect_response\n";
                break;
            case RDMA_CM_EVENT_CONNECT_ERROR:
                state = ERROR;
                errorCallback(ci);
                break;
            case RDMA_CM_EVENT_UNREACHABLE:
                state = ERROR;
                errorCallback(ci);
                break;
            case RDMA_CM_EVENT_REJECTED:
                state = REJECTED;
                rejectedCallback(ci);
                break;
            case RDMA_CM_EVENT_ESTABLISHED:
                state = ESTABLISHED;
                connectedCallback(ci);
                break;
            default:
                state = ERROR;
                std::cerr << "Warning: unexpected response to connect - " << eventType << "\n";
            }
            break;
        case ESTABLISHED:
            switch (eventType) {
            case RDMA_CM_EVENT_DISCONNECTED:
                disconnectedCallback(ci);
                break;
            default:
                std::cerr << "Warning: unexpected event in ESTABLISHED state - " << eventType << "\n";
            }
            break;
        case REJECTED:
            std::cerr << "Warning: event in REJECTED state - " << eventType << "\n";
            break;
        case ERROR:
            std::cerr << "Warning: event in ERROR state - " << eventType << "\n";
            break;
        case LISTENING:
        case ACCEPTING:
            std::cerr << "Warning: in an illegal state (and received event!) - " << eventType << "\n";
            break;
        }
#endif
    }
}
