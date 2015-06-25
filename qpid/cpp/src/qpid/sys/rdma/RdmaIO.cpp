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
#include "qpid/sys/rdma/RdmaIO.h"

#include "qpid/log/Statement.h"

#include <string>
#include <boost/bind.hpp>

using qpid::sys::SocketAddress;
using qpid::sys::DispatchHandle;
using qpid::sys::Poller;
using qpid::sys::ScopedLock;
using qpid::sys::Mutex;

namespace Rdma {
    // Set packing as these are 'on the wire' structures
#   pragma pack(push, 1)

    // Header structure for each transmitted frame
    struct FrameHeader {
        const static uint32_t FlagsMask = 0xf0000000;
        uint32_t data; // written in network order
        
        FrameHeader() {}
        FrameHeader(uint32_t credit, uint32_t flags = 0) {
            data = htonl((credit & ~FlagsMask) | (flags & FlagsMask));
        }
        
        uint32_t credit() const {
            return ntohl(data) & ~FlagsMask;
        }
        
        uint32_t flags() const {
            return ntohl(data) & FlagsMask;
        }
    };

    const size_t FrameHeaderSize = sizeof(FrameHeader);

    // Structure for Connection Parameters on the network
    //
    // The original version (now called 0) of these parameters had a couple of mistakes:
    // * No way to version the protocol (need to introduce a new protocol for iWarp)
    // * Used host order int32 (but only deployed on LE archs as far as we know)
    //   so effectively was LE on the wire which is the opposite of network order.
    //
    // Fortunately the values sent were sufficiently restricted that a 16 bit short could
    // be carved out to indicate the protocol version as these bits were always sent as 0.
    //
    // So the current version of parameters uses the last 2 bytes to indicate the protocol
    // version, if this is 0 then we interpret the rest of the struct without byte swapping
    // to remain compatible with the previous protocol.
    struct NConnectionParams {
        uint32_t maxRecvBufferSize;
        uint16_t initialXmitCredit;
        uint16_t rdmaProtocolVersion;

        NConnectionParams(const ConnectionParams& c) :
            maxRecvBufferSize(c.rdmaProtocolVersion ? htonl(c.maxRecvBufferSize) : c.maxRecvBufferSize),
            initialXmitCredit(c.rdmaProtocolVersion ? htons(c.initialXmitCredit) : c.initialXmitCredit),
            // 0 is the same with/without byteswapping!
            rdmaProtocolVersion(htons(c.rdmaProtocolVersion))
        {}
        
        operator ConnectionParams() const {
            return 
            	ConnectionParams(
            	    rdmaProtocolVersion ? ntohl(maxRecvBufferSize) : maxRecvBufferSize,
            	    rdmaProtocolVersion ? ntohs(initialXmitCredit) : initialXmitCredit,
            	    ntohs(rdmaProtocolVersion));
        }
    };
#   pragma pack(pop)

    class IOException : public std::exception {
        std::string s;

    public:
        IOException(std::string s0): s(s0) {}
        ~IOException() throw() {}
        
        const char* what() const throw() {
            return s.c_str();
        }
    };

    AsynchIO::AsynchIO(
            QueuePair::intrusive_ptr q,
            int version,
            int size,
            int xCredit,
            int rCount,
            ReadCallback rc,
            IdleCallback ic,
            FullCallback fc,
            ErrorCallback ec
    ) :
        protocolVersion(version),
        bufferSize(size),
        recvCredit(0),
        xmitCredit(xCredit),
        recvBufferCount(rCount),
        xmitBufferCount(xCredit),
        outstandingWrites(0),
        draining(false),
        state(IDLE),
        qp(q),
        dataHandle(*qp, boost::bind(&AsynchIO::dataEvent, this), 0, 0),
        readCallback(rc),
        idleCallback(ic),
        fullCallback(fc),
        errorCallback(ec),
        pendingWriteAction(boost::bind(&AsynchIO::writeEvent, this))
    {
        if (protocolVersion > maxSupportedProtocolVersion)
             throw IOException("Unsupported Rdma Protocol");
        qp->nonblocking();
        qp->notifyRecv();
        qp->notifySend();

        // Prepost recv buffers before we go any further
        qp->allocateRecvBuffers(recvBufferCount, bufferSize+FrameHeaderSize);

        // Create xmit buffers, reserve space for frame header.
        qp->createSendBuffers(xmitBufferCount, bufferSize, FrameHeaderSize);
    }

    AsynchIO::~AsynchIO() {
        // Warn if we are deleting whilst there are still unreclaimed write buffers
        if ( outstandingWrites>0 )
            QPID_LOG(error, "RDMA: qp=" << qp << ": Deleting queue before all write buffers finished");

        // Turn off callbacks if necessary (before doing the deletes)
        if (state != STOPPED) {
            QPID_LOG(error, "RDMA: qp=" << qp << ": Deleting queue whilst not shutdown");
            dataHandle.stopWatch();
        }
        // TODO: It might turn out to be more efficient in high connection loads to reuse the
        // buffers rather than having to reregister them all the time (this would be straightforward if all 
        // connections haver the same buffer size and harder otherwise)
    }

    void AsynchIO::start(Poller::shared_ptr poller) {
        dataHandle.startWatch(poller);
    }

    // State constraints
    // On entry: None
    // On exit: STOPPED
    // Mark for deletion/Delete this object when we have no outstanding writes
    void AsynchIO::stop(NotifyCallback nc) {
        ScopedLock<Mutex> l(stateLock);
        state = STOPPED;
        notifyCallback = nc;
        dataHandle.call(boost::bind(&AsynchIO::doStoppedCallback, this));
    }

    namespace {
        void requestedCall(AsynchIO* aio, AsynchIO::RequestCallback callback) {
            assert(callback);
            callback(*aio);
        }
    }

    void AsynchIO::requestCallback(RequestCallback callback) {
        // TODO creating a function object every time isn't all that
        // efficient - if this becomes heavily used do something better (what?)
        assert(callback);
        dataHandle.call(boost::bind(&requestedCall, this, callback));
    }

    // Mark writing closed (so we don't accept any more writes or make any idle callbacks)
    void AsynchIO::drainWriteQueue(NotifyCallback nc) {
        draining = true;
        notifyCallback = nc;
    }

    void AsynchIO::queueBuffer(Buffer* buff, int credit) {
        switch (protocolVersion) {
        case 0:
            if (!buff) {
                Buffer* ob = getSendBuffer();
                // Have to send something as adapters hate it when you try to transfer 0 bytes
                char* bytes = ob->bytes();
                bytes[0] = 0xFF & (credit >> 24);
                bytes[1] = 0xFF & (credit >> 16);
                bytes[2] = 0xFF & (credit >>  8);
                bytes[3] = 0xFF & (credit      );
                ob->dataCount(sizeof(uint32_t));
                qp->postSend(credit | IgnoreData, ob);        
            } else if (credit > 0) {
                qp->postSend(credit, buff);
            } else {
                qp->postSend(buff);
            }
            break;
        case 1:
            if (!buff)
                buff = getSendBuffer();
            // Add FrameHeader after frame data
            FrameHeader header(credit);
            assert(buff->dataCount() <= buff->byteCount());   // ensure app data doesn't impinge on reserved space.
            ::memcpy(buff->bytes()+buff->dataCount(), &header, FrameHeaderSize);
            buff->dataCount(buff->dataCount()+FrameHeaderSize);
            qp->postSend(buff);
            break;
        }
    }
    
    Buffer* AsynchIO::extractBuffer(const QueuePairEvent& e) {
        Buffer* b = e.getBuffer();
        switch (protocolVersion) {
        case 0: {
            bool dataPresent = true;
            // Get our xmitCredit if it was sent
            if (e.immPresent() ) {
                assert(xmitCredit>=0);
                xmitCredit += (e.getImm() & ~FlagsMask);
                dataPresent = ((e.getImm() & IgnoreData) == 0);
                assert(xmitCredit>0);
            }
            if (!dataPresent) {
                b->dataCount(0);
            }
            break;
        }
        case 1:            
            b->dataCount(b->dataCount()-FrameHeaderSize);
            FrameHeader header;
            ::memcpy(&header, b->bytes()+b->dataCount(), FrameHeaderSize);
            assert(xmitCredit>=0);
            xmitCredit += header.credit();
            assert(xmitCredit>=0);            
            break;
        }
        
        return b;
    }

    void AsynchIO::queueWrite(Buffer* buff) {
        // Make sure we don't overrun our available buffers
        // either at our end or the known available at the peers end
        if (writable()) {
            // TODO: We might want to batch up sending credit
            int creditSent = recvCredit & ~FlagsMask;
            queueBuffer(buff, creditSent);
            recvCredit -= creditSent;
            ++outstandingWrites;
            --xmitCredit;
            assert(xmitCredit>=0);
        } else {
            if (fullCallback) {
                fullCallback(*this, buff);
            } else {
                QPID_LOG(error, "RDMA: qp=" << qp << ": Write queue full, but no callback, throwing buffer away");
                returnSendBuffer(buff);
            }
        }
    }

    // State constraints
    // On entry: None
    // On exit: NOTIFY_PENDING || STOPPED 
    void AsynchIO::notifyPendingWrite() {
        ScopedLock<Mutex> l(stateLock);
        switch (state) {
        case IDLE:
            dataHandle.call(pendingWriteAction);
            // Fall Thru
        case NOTIFY:
            state = NOTIFY_PENDING;
            break;
        case NOTIFY_PENDING:
        case STOPPED:
            break;
        }
    }

    // State constraints
    // On entry: IDLE || STOPPED
    // On exit: IDLE || STOPPED
    void AsynchIO::dataEvent() {
        {
        ScopedLock<Mutex> l(stateLock);

        if (state == STOPPED) return;
        
        state = NOTIFY_PENDING;
        }
        processCompletions();

        writeEvent();
    }
    
    // State constraints
    // On entry: NOTIFY_PENDING || STOPPED
    // On exit: IDLE || STOPPED
    void AsynchIO::writeEvent() {
        State newState;
        do {
            {
            ScopedLock<Mutex> l(stateLock);

            switch (state) {
            case STOPPED:
                return;
            default:
                state = NOTIFY;
            }
            }

            doWriteCallback();

            {
            ScopedLock<Mutex> l(stateLock);

            newState = state;
            switch (newState) {
            case NOTIFY_PENDING:
            case STOPPED:
                break;
            default:
                state = IDLE;
            }
            }
        } while (newState == NOTIFY_PENDING);
    }

    void AsynchIO::processCompletions() {
        QueuePair::intrusive_ptr q = qp->getNextChannelEvent();

        // Re-enable notification for queue:
        // This needs to happen before we could do anything that could generate more work completion
        // events (ie the callbacks etc. in the following).
        // This can't make us reenter this code as the handle attached to the completion queue will still be
        // disabled by the poller until we leave this code
        qp->notifyRecv();
        qp->notifySend();

        int recvEvents = 0;
        int sendEvents = 0;

        // If no event do nothing
        if (!q)
            return;

        assert(q == qp);

        // Repeat until no more events
        do {
            QueuePairEvent e(qp->getNextEvent());
            if (!e)
                break;

            ::ibv_wc_status status = e.getEventStatus();
            if (status != IBV_WC_SUCCESS) {
                // Need special check for IBV_WC_WR_FLUSH_ERR here
                // we will get this for every send/recv queue entry that was pending
                // when disconnected, these aren't real errors and mostly need to be ignored
                if (status == IBV_WC_WR_FLUSH_ERR) {
                    QueueDirection dir = e.getDirection();
                    if (dir == SEND) {
                        Buffer* b = e.getBuffer();
                        ++sendEvents;
                        returnSendBuffer(b);
                        --outstandingWrites;
                    } else {
                        ++recvEvents;
                    }
                    continue;
                }
                errorCallback(*this);
                // TODO: Probably need to flush queues at this point
                return;
            }

            // Test if recv (or recv with imm)
            //::ibv_wc_opcode eventType = e.getEventType();
            QueueDirection dir = e.getDirection();
            if (dir == RECV) {
                ++recvEvents;

                Buffer* b = extractBuffer(e);

                // if there was no data sent then the message was only to update our credit
                if ( b->dataCount() > 0 ) {
                    readCallback(*this, b);
                }

                // At this point the buffer has been consumed so put it back on the recv queue
                // TODO: Is this safe to do if the connection is disconnected already?
                qp->postRecv(b);

                // Received another message
                ++recvCredit;

                // Send recvCredit if it is large enough (it will have got this large because we've not sent anything recently)
                if (recvCredit > recvBufferCount/2) {
                    // TODO: This should use RDMA write with imm as there might not ever be a buffer to receive this message
                    // but this is a little unlikely, as to get in this state we have to have received messages without sending any
                    // for a while so its likely we've received an credit update from the far side.
                    if (writable()) {
                        int creditSent = recvCredit & ~FlagsMask;
                        queueBuffer(0, creditSent);
                        recvCredit -= creditSent;
                        ++outstandingWrites;
                        --xmitCredit;
                        assert(xmitCredit>=0);
                    } else {
                        QPID_LOG(warning, "RDMA: qp=" << qp << ":  Unable to send unsolicited credit");
                    }
                }
            } else {
                Buffer* b = e.getBuffer();
                ++sendEvents;
                returnSendBuffer(b);
                --outstandingWrites;
            }
        } while (true);

        // Not sure if this is expected or not 
        if (recvEvents == 0 && sendEvents == 0) {
            QPID_LOG(debug, "RDMA: qp=" << qp << ":  Got channel event with no recv/send completions");
        }
    }

    void AsynchIO::doWriteCallback() {
        // TODO: maybe don't call idle unless we're low on write buffers
        // Keep on calling the idle routine as long as we are writable and we got something to write last call

        // Do callback even if there are no available free buffers as the application itself might be
        // holding onto buffers
        while (writable()) {
            int xc = xmitCredit;
            idleCallback(*this);
            // Check whether we actually wrote anything
            if (xmitCredit == xc) {
                QPID_LOG(debug, "RDMA: qp=" << qp << ": Called for data, but got none: xmitCredit=" << xmitCredit);
                return;
            }
        }
        
        checkDrained();
    }

    void AsynchIO::checkDrained() {
        // If we've got all the write confirmations and we're draining
        // We might get deleted in the drained callback so return immediately
        if (draining) {
            if (outstandingWrites == 0) {
                 draining = false;
                 NotifyCallback nc;
                 nc.swap(notifyCallback);
                 nc(*this);
            }
            return;
        }
    }
    
    void AsynchIO::doStoppedCallback() {
        // Ensure we can't get any more callbacks (except for the stopped callback)
        dataHandle.stopWatch();

        NotifyCallback nc;
        nc.swap(notifyCallback);
        nc(*this);
    }

    ConnectionManager::ConnectionManager(
        ErrorCallback errc,
        DisconnectedCallback dc
    ) :
        state(IDLE),
        ci(Connection::make()),
        handle(*ci, boost::bind(&ConnectionManager::event, this, _1), 0, 0),
        errorCallback(errc),
        disconnectedCallback(dc)
    {
        QPID_LOG(debug, "RDMA: ci=" << ci << ": Creating ConnectionManager");
        ci->nonblocking();
    }

    ConnectionManager::~ConnectionManager()
    {
        QPID_LOG(debug, "RDMA: ci=" << ci << ": Deleting ConnectionManager");
    }

    void ConnectionManager::start(Poller::shared_ptr poller, const qpid::sys::SocketAddress& addr) {
        startConnection(ci, addr);
        handle.startWatch(poller);
    }

    void ConnectionManager::doStoppedCallback() {
        // Ensure we can't get any more callbacks (except for the stopped callback)
        handle.stopWatch();

        NotifyCallback nc;
        nc.swap(notifyCallback);
        nc(*this);
    }

    void ConnectionManager::stop(NotifyCallback nc) {
        state = STOPPED;
        notifyCallback = nc;
        handle.call(boost::bind(&ConnectionManager::doStoppedCallback, this));
    }

    void ConnectionManager::event(DispatchHandle&) {
        if (state.get() == STOPPED) return;
        connectionEvent(ci);
    }

    Listener::Listener(
        const ConnectionParams& cp,
        EstablishedCallback ec,
        ErrorCallback errc,
        DisconnectedCallback dc,
        ConnectionRequestCallback crc
    ) :
        ConnectionManager(errc, dc),
        checkConnectionParams(cp),
        connectionRequestCallback(crc),
        establishedCallback(ec)
    {
    }

    void Listener::startConnection(Connection::intrusive_ptr ci, const qpid::sys::SocketAddress& addr) {
        ci->bind(addr);
        ci->listen();
    }

    namespace {
        const int64_t PoisonContext = -1;
    }

    void Listener::connectionEvent(Connection::intrusive_ptr ci) {
        ConnectionEvent e(ci->getNextEvent());

        // If (for whatever reason) there was no event do nothing
        if (!e)
            return;

        // Important documentation ommision the new rdma_cm_id
        // you get from CONNECT_REQUEST has the same context info
        // as its parent listening rdma_cm_id
        ::rdma_cm_event_type eventType = e.getEventType();
        ::rdma_conn_param conn_param = e.getConnectionParam();
        Rdma::Connection::intrusive_ptr id = e.getConnection();

        // Check for previous disconnection (it appears that you actually can get connection
        // request events after a disconnect event in rare circumstances)
        if (reinterpret_cast<int64_t>(id->getContext<void*>())==PoisonContext)
            return;

        switch (eventType) {
        case RDMA_CM_EVENT_CONNECT_REQUEST: {
            // Make sure peer has sent params we can use
            if (!conn_param.private_data || conn_param.private_data_len < sizeof(NConnectionParams)) {
                QPID_LOG(warning, "Rdma: rejecting connection attempt: unusable connection parameters");
                id->reject();
                break;
            }
            
            const NConnectionParams* rcp = static_cast<const NConnectionParams*>(conn_param.private_data);
            ConnectionParams cp = *rcp;

            // Reject if requested msg size is bigger than we allow
            if (
                cp.maxRecvBufferSize > checkConnectionParams.maxRecvBufferSize ||
                cp.initialXmitCredit > checkConnectionParams.initialXmitCredit
            ) {
                QPID_LOG(warning, "Rdma: rejecting connection attempt: connection parameters out of range: ("
                  << cp.maxRecvBufferSize << ">" << checkConnectionParams.maxRecvBufferSize << " || "
                  << cp.initialXmitCredit << ">" << checkConnectionParams.initialXmitCredit
                  << ")");
                id->reject(&checkConnectionParams);
                break;
            }

            bool accept = true;
            if (connectionRequestCallback)
                accept = connectionRequestCallback(id, cp);

            if (accept) {
                // Accept connection
                cp.initialXmitCredit = checkConnectionParams.initialXmitCredit;
                id->accept(conn_param, rcp);
            } else {
                // Reject connection
                QPID_LOG(warning, "Rdma: rejecting connection attempt: application policy");
                id->reject();
            }
            break;
        }
        case RDMA_CM_EVENT_ESTABLISHED:
            establishedCallback(id);
            break;
        case RDMA_CM_EVENT_DISCONNECTED:
            disconnectedCallback(id);
            // Poison the id context so that we do no more callbacks on it
            id->removeContext();
            id->addContext(reinterpret_cast<void*>(PoisonContext));
            break;
        case RDMA_CM_EVENT_CONNECT_ERROR:
            errorCallback(id, CONNECT_ERROR);
            break;
        default:
            // Unexpected response
            errorCallback(id, UNKNOWN);
            //std::cerr << "Warning: unexpected response to listen - " << eventType << "\n";
        }
    }

    Connector::Connector(
        const ConnectionParams& cp,
        ConnectedCallback cc,
        ErrorCallback errc,
        DisconnectedCallback dc,
        RejectedCallback rc
    ) :
        ConnectionManager(errc, dc),
        connectionParams(cp),
        rejectedCallback(rc),
        connectedCallback(cc)
    {
    }

    void Connector::startConnection(Connection::intrusive_ptr ci, const qpid::sys::SocketAddress& addr) {
        ci->resolve_addr(addr);
    }

    void Connector::connectionEvent(Connection::intrusive_ptr ci) {
        ConnectionEvent e(ci->getNextEvent());

        // If (for whatever reason) there was no event do nothing
        if (!e)
            return;

        ::rdma_cm_event_type eventType = e.getEventType();
        ::rdma_conn_param conn_param = e.getConnectionParam();
        Rdma::Connection::intrusive_ptr id = e.getConnection();
        switch (eventType) {
        case RDMA_CM_EVENT_ADDR_RESOLVED:
            // RESOLVE_ADDR
            ci->resolve_route();
            break;
        case RDMA_CM_EVENT_ADDR_ERROR:
            // RESOLVE_ADDR
            errorCallback(ci, ADDR_ERROR);
            break;
        case RDMA_CM_EVENT_ROUTE_RESOLVED: {
            // RESOLVE_ROUTE:
            NConnectionParams rcp(connectionParams);
            ci->connect(&rcp);
            break;
        }
        case RDMA_CM_EVENT_ROUTE_ERROR:
            // RESOLVE_ROUTE:
            errorCallback(ci, ROUTE_ERROR);
            break;
        case RDMA_CM_EVENT_CONNECT_ERROR:
            // CONNECTING
            errorCallback(ci, CONNECT_ERROR);
            break;
        case RDMA_CM_EVENT_UNREACHABLE:
            // CONNECTING
            errorCallback(ci, UNREACHABLE);
            break;
        case RDMA_CM_EVENT_REJECTED: {
            // CONNECTING
            
            // We can get this event if our peer is not running on the other side
            // in this case we could get nearly anything in the private data:
            // From private_data == 0 && private_data_len == 0 (Chelsio iWarp)
            // to 148 bytes of zeros (Mellanox IB)
            // 
            // So assume that if the the private data is absent or not the size of 
            // the connection parameters it isn't valid
            ConnectionParams cp(0, 0, 0);
            if (conn_param.private_data && conn_param.private_data_len == sizeof(NConnectionParams)) {
                // Extract private data from event
                const NConnectionParams* rcp = static_cast<const NConnectionParams*>(conn_param.private_data);
                cp = *rcp;
            }
            rejectedCallback(ci, cp);
            break;
        }
        case RDMA_CM_EVENT_ESTABLISHED: {
            // CONNECTING
            // Extract private data from event
            assert(conn_param.private_data && conn_param.private_data_len >= sizeof(NConnectionParams));
            const NConnectionParams* rcp = static_cast<const NConnectionParams*>(conn_param.private_data);
            ConnectionParams cp = *rcp;
            connectedCallback(ci, cp);
            break;
        }
        case RDMA_CM_EVENT_DISCONNECTED:
            // ESTABLISHED
            disconnectedCallback(ci);
            break;
        default:
            QPID_LOG(warning, "RDMA: Unexpected event in connect: " << eventType);
        }
    }
}
