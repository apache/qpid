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
#ifndef RDMA_WRAP_H
#define RDMA_WRAP_H

#include <rdma/rdma_cma.h>

#include "qpid/RefCounted.h"
#include "qpid/sys/IOHandle.h"
#include "qpid/sys/Mutex.h"

#include <boost/shared_ptr.hpp>
#include <boost/scoped_ptr.hpp>
#include <boost/intrusive_ptr.hpp>
#include <boost/ptr_container/ptr_deque.hpp>

#include <vector>

namespace qpid {
namespace sys {
    class SocketAddress;
}}

namespace Rdma {
    const int DEFAULT_TIMEOUT = 2000; // 2 secs
    const int DEFAULT_BACKLOG = 100;
    const int DEFAULT_CQ_ENTRIES = 256;
    const int DEFAULT_WR_ENTRIES = 64;
    extern const ::rdma_conn_param DEFAULT_CONNECT_PARAM;

    int deviceCount();

    struct Buffer {
        friend class QueuePair;
        friend class QueuePairEvent;

        char* bytes() const;
        uint32_t* words() const;
        int32_t byteCount() const;
        int32_t wordCount() const;
        int32_t dataCount() const;
        void dataCount(int32_t);

    private:
        Buffer(uint32_t lkey, char* bytes, const int32_t byteCount, const int32_t reserve=0);
        int32_t bufferSize;
        int32_t reserved;   // for framing header
        ::ibv_sge sge;
    };

    inline char* Buffer::bytes() const {
      return (char*) sge.addr;
    }

    inline uint32_t* Buffer::words() const {
        return (uint32_t*) sge.addr;
    }

    /** return the number of bytes available for application data */
    inline int32_t Buffer::byteCount() const {
        return bufferSize - reserved;
    }

    /** return the number of words available for application data */
    inline int32_t Buffer::wordCount() const {
        return (bufferSize - reserved) / sizeof(uint32_t);
    }

    inline int32_t Buffer::dataCount() const {
        return sge.length;
    }

    inline void Buffer::dataCount(int32_t s) {
        // catch any attempt to overflow a buffer
        assert(s <= bufferSize + reserved);
        sge.length = s;
    }

    class Connection;

    enum QueueDirection {
        NONE,
        SEND,
        RECV
    };

    class QueuePairEvent {
        boost::shared_ptr< ::ibv_cq > cq;
        ::ibv_wc wc;
        QueueDirection dir;

        friend class QueuePair;

        QueuePairEvent();
        QueuePairEvent(
            const ::ibv_wc& w,
            boost::shared_ptr< ::ibv_cq > c,
            QueueDirection d);

    public:
        operator bool() const;
        bool immPresent() const;
        uint32_t getImm() const;
        QueueDirection getDirection() const;
        ::ibv_wc_opcode getEventType() const;
        ::ibv_wc_status getEventStatus() const;
        Buffer* getBuffer() const;
    };

    // Wrapper for a queue pair - this has the functionality for
    // putting buffers on the receive queue and for sending buffers
    // to the other end of the connection.
    class QueuePair : public qpid::RefCounted {
        friend class Connection;

        boost::scoped_ptr< qpid::sys::IOHandle > handle;
        boost::shared_ptr< ::ibv_pd > pd;
        boost::shared_ptr< ::ibv_mr > smr;
        boost::shared_ptr< ::ibv_mr > rmr;
        boost::shared_ptr< ::ibv_comp_channel > cchannel;
        boost::shared_ptr< ::ibv_cq > scq;
        boost::shared_ptr< ::ibv_cq > rcq;
        boost::shared_ptr< ::ibv_qp > qp;
        int outstandingSendEvents;
        int outstandingRecvEvents;
        std::vector<Buffer> sendBuffers;
        std::vector<Buffer> recvBuffers;
        qpid::sys::Mutex bufferLock;
        std::vector<int> freeBuffers;

        QueuePair(boost::shared_ptr< ::rdma_cm_id > id);
        ~QueuePair();

    public:
        typedef boost::intrusive_ptr<QueuePair> intrusive_ptr;

        operator qpid::sys::IOHandle&() const;

        // Create a buffers to use for writing
        void createSendBuffers(int sendBufferCount, int dataSize, int headerSize);

        // Get a send buffer
        Buffer* getSendBuffer();

        // Return buffer to pool after use
        void returnSendBuffer(Buffer* b);

        // Create and post recv buffers
        void allocateRecvBuffers(int recvBufferCount, int bufferSize);

        // Make channel non-blocking by making
        // associated fd nonblocking
        void nonblocking();

        // If we get EAGAIN because the channel has been set non blocking
        // and we'd have to wait then return an empty event
        QueuePair::intrusive_ptr getNextChannelEvent();

        QueuePairEvent getNextEvent();

        void postRecv(Buffer* buf);
        void postSend(Buffer* buf);
        void postSend(uint32_t imm, Buffer* buf);
        void notifyRecv();
        void notifySend();
    };

    class ConnectionEvent {
        friend class Connection;

        // The order of the members is important as we have to acknowledge
        // the event before destroying the ids on destruction
        boost::intrusive_ptr<Connection> id;
        boost::intrusive_ptr<Connection> listen_id;
        boost::shared_ptr< ::rdma_cm_event > event;

        ConnectionEvent() {}
        ConnectionEvent(::rdma_cm_event* e);

        // Default copy, assignment and destructor ok
    public:
        operator bool() const;
        ::rdma_cm_event_type getEventType() const;
        ::rdma_conn_param getConnectionParam() const;
        boost::intrusive_ptr<Connection> getConnection () const;
        boost::intrusive_ptr<Connection> getListenId() const;
    };

    // For the moment this is a fairly simple wrapper for rdma_cm_id.
    //
    // NB: It allocates a protection domain (pd) per connection which means that
    // registered buffers can't be shared between different connections
    // (this can only happen between connections on the same controller in any case,
    // so needs careful management if used)
    class Connection : public qpid::RefCounted {
        boost::scoped_ptr< qpid::sys::IOHandle > handle;
        boost::shared_ptr< ::rdma_event_channel > channel;
        boost::shared_ptr< ::rdma_cm_id > id;
        QueuePair::intrusive_ptr qp;

        void* context;

        friend class ConnectionEvent;
        friend class QueuePair;

        // Wrap the passed in rdma_cm_id with a Connection
        // this basically happens only on connection request
        Connection(::rdma_cm_id* i);
        Connection();
        ~Connection();

        void ensureQueuePair();

    public:
        typedef boost::intrusive_ptr<Connection> intrusive_ptr;

        operator qpid::sys::IOHandle&() const;

        static intrusive_ptr make();
        static intrusive_ptr find(::rdma_cm_id* i);

        template <typename T>
        void addContext(T* c) {
            // Don't allow replacing context
            if (!context)
                context = c;
        }

        void removeContext() {
            context = 0;
        }

        template <typename T>
        T* getContext() {
            return static_cast<T*>(context);
        }

        // Make channel non-blocking by making
        // associated fd nonblocking
        void nonblocking();

        // If we get EAGAIN because the channel has been set non blocking
        // and we'd have to wait then return an empty event
        ConnectionEvent getNextEvent();

        void bind(const qpid::sys::SocketAddress& src_addr) const;
        void listen(int backlog = DEFAULT_BACKLOG) const;
        void resolve_addr(
            const qpid::sys::SocketAddress& dst_addr,
            int timeout_ms = DEFAULT_TIMEOUT) const;
        void resolve_route(int timeout_ms = DEFAULT_TIMEOUT) const;
        void disconnect() const;

        // TODO: Currently you can only connect with the default connection parameters
        void connect(const void* data, size_t len);
        void connect();
        template <typename T>
        void connect(const T* data) {
            connect(data, sizeof(T));
	}

        // TODO: Not sure how to default accept params - they come from the connection request
        // event
        void accept(const ::rdma_conn_param& param, const void* data, size_t len);
        void accept(const ::rdma_conn_param& param);
        template <typename T>
        void accept(const ::rdma_conn_param& param, const T* data) {
            accept(param, data, sizeof(T));
        }

        void reject(const void* data, size_t len) const;
        void reject() const;
        template <typename T>
        void reject(const T* data) const {
            reject(data, sizeof(T));
        }

        QueuePair::intrusive_ptr getQueuePair();
        std::string getLocalName() const;
        std::string getPeerName() const;
        std::string getFullName() const { return getLocalName()+"-"+getPeerName(); }
    };
}

std::ostream& operator<<(std::ostream& o, ::rdma_cm_event_type t);

#endif // RDMA_WRAP_H
