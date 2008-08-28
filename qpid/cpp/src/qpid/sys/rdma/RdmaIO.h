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

#include "rdma_wrap.h"

#include "qpid/sys/Dispatcher.h"
#include "qpid/sys/Mutex.h"

#include <netinet/in.h>

#include <boost/function.hpp>
#include <boost/ptr_container/ptr_deque.hpp>
#include <deque>

using qpid::sys::DispatchHandle;
using qpid::sys::Poller;

namespace Rdma {

    class Connection;

    typedef boost::function1<void, Rdma::Connection::intrusive_ptr&> ConnectedCallback;
    typedef boost::function1<void, Rdma::Connection::intrusive_ptr&> ErrorCallback;
    typedef boost::function1<void, Rdma::Connection::intrusive_ptr&> DisconnectedCallback;
    typedef boost::function1<bool, Rdma::Connection::intrusive_ptr&> ConnectionRequestCallback;
    typedef boost::function1<void, Rdma::Connection::intrusive_ptr&> RejectedCallback;

    class AsynchIO
    {
        typedef boost::function1<void, AsynchIO&> ErrorCallback;
        typedef boost::function2<void, AsynchIO&, Buffer*> ReadCallback;
        typedef boost::function1<void, AsynchIO&>  IdleCallback;
        typedef boost::function1<void, AsynchIO&>  FullCallback;

        QueuePair::intrusive_ptr qp;
        DispatchHandle dataHandle;
        int bufferSize;
        int recvBufferCount;
        int xmitBufferCount;
        int outstandingWrites;
        std::deque<Buffer*> bufferQueue;
        qpid::sys::Mutex bufferQueueLock;
        boost::ptr_deque<Buffer> buffers;

        ReadCallback readCallback;
        IdleCallback idleCallback;
        FullCallback fullCallback;
        ErrorCallback errorCallback;

    public:
        AsynchIO(
            QueuePair::intrusive_ptr q,
            int s,
            ReadCallback rc,
            IdleCallback ic,
            FullCallback fc,
            ErrorCallback ec
        );
        ~AsynchIO();

        void start(Poller::shared_ptr poller);
        void queueWrite(Buffer* buff);
        void notifyPendingWrite();
        void queueWriteClose();
        Buffer* getBuffer();

    private:
        void dataEvent(DispatchHandle& handle);
    };

    class Listener
    {
        sockaddr src_addr;
        Connection::intrusive_ptr ci;
        DispatchHandle handle;
        ConnectedCallback connectedCallback;
        ErrorCallback errorCallback;
        DisconnectedCallback disconnectedCallback;
        ConnectionRequestCallback connectionRequestCallback;

    public:
        Listener(
            const sockaddr& src,
            ConnectedCallback cc,
            ErrorCallback errc,
            DisconnectedCallback dc,
            ConnectionRequestCallback crc = 0
        );
        void start(Poller::shared_ptr poller);

    private:
        void connectionEvent(DispatchHandle& handle);
    };

    class Connector
    {
        sockaddr dst_addr;
        Connection::intrusive_ptr ci;
        DispatchHandle handle;
        ConnectedCallback connectedCallback;
        ErrorCallback errorCallback;
        DisconnectedCallback disconnectedCallback;
        RejectedCallback rejectedCallback;

    public:
        Connector(
            const sockaddr& dst,
            ConnectedCallback cc,
            ErrorCallback errc,
            DisconnectedCallback dc,
            RejectedCallback rc = 0
        );
        void start(Poller::shared_ptr poller);

    private:
        void connectionEvent(DispatchHandle& handle);
    };
}

#endif // Rdma_Acceptor_h
