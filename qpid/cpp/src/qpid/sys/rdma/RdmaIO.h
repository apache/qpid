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
#ifndef Rdma_Acceptor_h
#define Rdma_Acceptor_h

#include "qpid/sys/rdma/rdma_wrap.h"

#include "qpid/sys/AtomicValue.h"
#include "qpid/sys/Dispatcher.h"
#include "qpid/sys/DispatchHandle.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/SocketAddress.h"

#include <netinet/in.h>

#include <boost/function.hpp>

namespace Rdma {

    class Connection;

    class AsynchIO
    {
        typedef boost::function1<void, AsynchIO&> ErrorCallback;
        typedef boost::function2<void, AsynchIO&, Buffer*> ReadCallback;
        typedef boost::function1<void, AsynchIO&> IdleCallback;
        typedef boost::function2<void, AsynchIO&, Buffer*> FullCallback;
        typedef boost::function1<void, AsynchIO&> NotifyCallback;

        int protocolVersion;
        int bufferSize;
        int recvCredit;
        int xmitCredit;
        int recvBufferCount;
        int xmitBufferCount;
        int outstandingWrites;
        bool draining;
        enum State {IDLE, NOTIFY, NOTIFY_PENDING, STOPPED};
        State state;
        qpid::sys::Mutex stateLock;
        QueuePair::intrusive_ptr qp;
        qpid::sys::DispatchHandleRef dataHandle;

        ReadCallback readCallback;
        IdleCallback idleCallback;
        FullCallback fullCallback;
        ErrorCallback errorCallback;
        NotifyCallback notifyCallback;
        qpid::sys::DispatchHandle::Callback pendingWriteAction;

    public:
        typedef boost::function1<void, AsynchIO&> RequestCallback;

        // TODO: Instead of specifying a buffer size specify the amount of memory the AsynchIO class can use
        // for buffers both read and write (allocate half to each up front) and fail if we cannot allocate that much
        // locked memory
        AsynchIO(
            QueuePair::intrusive_ptr q,
            int version,
            int size,
            int xCredit,
            int rCount,
            ReadCallback rc,
            IdleCallback ic,
            FullCallback fc,
            ErrorCallback ec
        );
        ~AsynchIO();

        void start(qpid::sys::Poller::shared_ptr poller);
        bool writable() const;
        void queueWrite(Buffer* buff);
        void notifyPendingWrite();
        void drainWriteQueue(NotifyCallback);
        void stop(NotifyCallback);
        void requestCallback(RequestCallback);
        int incompletedWrites() const;
        Buffer* getSendBuffer();
        void returnSendBuffer(Buffer*);

    private:
        const static int maxSupportedProtocolVersion = 1;

        // Constants for the peer-peer command messages
        // These are sent in the high bits if the imm data of an rdma message
        // The low bits are used to send the credit
        const static int FlagsMask = 0xF0000000; // Mask for all flag bits - be sure to update this if you add more command bits
        const static int IgnoreData = 0x10000000; // Message contains no application data

        void dataEvent();
        void writeEvent();
        void processCompletions();
        void doWriteCallback();
        void checkDrained();
        void doStoppedCallback();
        
        void queueBuffer(Buffer* buff, int credit);
        Buffer* extractBuffer(const QueuePairEvent& e);
    };

    // We're only writable if:
    // * not draining write queue
    // * we've got space in the transmit queue
    // * we've got credit to transmit
    // * if there's only 1 transmit credit we must send some credit
    inline bool AsynchIO::writable() const {
        assert(xmitCredit>=0);
        return !draining &&
               outstandingWrites < xmitBufferCount &&
               xmitCredit > 0 &&
               ( xmitCredit > 1 || recvCredit > 0);
    }

    inline int AsynchIO::incompletedWrites() const {
        return outstandingWrites;
    }

    inline Buffer* AsynchIO::getSendBuffer() {
        return qp->getSendBuffer();
    }

    inline void AsynchIO::returnSendBuffer(Buffer* b) {
        qp->returnSendBuffer(b);
    }

    // These are the parameters necessary to start the conversation
    // * Each peer HAS to allocate buffers of the size of the maximum receive from its peer
    // * Each peer HAS to know the initial "credit" it has for transmitting to its peer 
    struct ConnectionParams {
        uint32_t maxRecvBufferSize;
        uint16_t initialXmitCredit;
        uint16_t rdmaProtocolVersion;

	// Default to protocol version 1
        ConnectionParams(uint32_t s, uint16_t c, uint16_t v = 1) :
            maxRecvBufferSize(s),
            initialXmitCredit(c),
            rdmaProtocolVersion(v)
        {}
    };

    enum ErrorType {
        ADDR_ERROR,
        ROUTE_ERROR,
        CONNECT_ERROR,
        UNREACHABLE,
        UNKNOWN
    };

    typedef boost::function2<void, Rdma::Connection::intrusive_ptr, ErrorType> ErrorCallback;
    typedef boost::function1<void, Rdma::Connection::intrusive_ptr> DisconnectedCallback;

    class ConnectionManager {
        typedef boost::function1<void, ConnectionManager&> NotifyCallback;
 
        enum State {IDLE, STOPPED};
        qpid::sys::AtomicValue<State> state;
        Connection::intrusive_ptr ci;
        qpid::sys::DispatchHandleRef handle;
        NotifyCallback notifyCallback;

    protected:
        ErrorCallback errorCallback;
        DisconnectedCallback disconnectedCallback;

    public:
        ConnectionManager(
            ErrorCallback errc,
            DisconnectedCallback dc
        );

        virtual ~ConnectionManager();

        void start(qpid::sys::Poller::shared_ptr poller, const qpid::sys::SocketAddress& addr);
        void stop(NotifyCallback);

    private:
        void event(qpid::sys::DispatchHandle& handle);
        void doStoppedCallback();

        virtual void startConnection(Connection::intrusive_ptr ci, const qpid::sys::SocketAddress& addr) = 0;
        virtual void connectionEvent(Connection::intrusive_ptr ci) = 0;
    };

    typedef boost::function2<bool, Rdma::Connection::intrusive_ptr, const ConnectionParams&> ConnectionRequestCallback;
    typedef boost::function1<void, Rdma::Connection::intrusive_ptr> EstablishedCallback;

    class Listener : public ConnectionManager
    {
        ConnectionParams checkConnectionParams;
        ConnectionRequestCallback connectionRequestCallback;
        EstablishedCallback establishedCallback;

    public:
        Listener(
            const ConnectionParams& cp,
            EstablishedCallback ec,
            ErrorCallback errc,
            DisconnectedCallback dc,
            ConnectionRequestCallback crc = 0
        );

    private:
        void startConnection(Connection::intrusive_ptr ci, const qpid::sys::SocketAddress& addr);
        void connectionEvent(Connection::intrusive_ptr ci);
    };

    typedef boost::function2<void, Rdma::Connection::intrusive_ptr, const ConnectionParams&> RejectedCallback;
    typedef boost::function2<void, Rdma::Connection::intrusive_ptr, const ConnectionParams&> ConnectedCallback;

    class Connector : public ConnectionManager
    {
        ConnectionParams connectionParams;
        RejectedCallback rejectedCallback;
        ConnectedCallback connectedCallback;

    public:
        Connector(
            const ConnectionParams& cp,
            ConnectedCallback cc,
            ErrorCallback errc,
            DisconnectedCallback dc,
            RejectedCallback rc = 0
        );

    private:
        void startConnection(Connection::intrusive_ptr ci, const qpid::sys::SocketAddress& addr);
        void connectionEvent(Connection::intrusive_ptr ci);
    };
}

#endif // Rdma_Acceptor_h
