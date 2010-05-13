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

#include "qpid/sys/rdma/rdma_wrap.h"

#include "qpid/sys/rdma/rdma_factories.h"
#include "qpid/sys/rdma/rdma_exception.h"

#include "qpid/sys/posix/PrivatePosix.h"

#include <fcntl.h>
#include <netdb.h>

#include <iostream>
#include <stdexcept>

namespace Rdma {
    const ::rdma_conn_param DEFAULT_CONNECT_PARAM = {
        0,    // .private_data
        0,    // .private_data_len
        4,    // .responder_resources
        4,    // .initiator_depth
        0,    // .flow_control
        5,    // .retry_count
        7     // .rnr_retry_count
    };
    
    // This is moderately inefficient so don't use in a critical path
    int deviceCount() {
        int count;
        ::ibv_free_device_list(::ibv_get_device_list(&count));
        return count;
    }

    Buffer::Buffer(::ibv_pd* pd, char* const b, const int32_t s) :
        bytes(b),
        byteCount(s),
        dataStart(0),
        dataCount(0),
        mr(CHECK_NULL(::ibv_reg_mr(
        pd, bytes, byteCount,
        ::IBV_ACCESS_LOCAL_WRITE)))
    {}

    Buffer::~Buffer() {
        (void) ::ibv_dereg_mr(mr);
        delete [] bytes;
    }

    QueuePairEvent::QueuePairEvent() :
        dir(NONE)
    {}

    QueuePairEvent::QueuePairEvent(
        const ::ibv_wc& w,
        boost::shared_ptr< ::ibv_cq > c,
        QueueDirection d) :
        cq(c),
        wc(w),
        dir(d)
    {
        assert(dir != NONE);
    }

    QueuePairEvent::operator bool() const {
        return dir != NONE;
    }

    bool QueuePairEvent::immPresent() const {
        return wc.wc_flags & IBV_WC_WITH_IMM;
    }

    uint32_t QueuePairEvent::getImm() const {
        return ntohl(wc.imm_data);
    }

    QueueDirection QueuePairEvent::getDirection() const {
        return dir;
    }

    ::ibv_wc_opcode QueuePairEvent::getEventType() const {
        return wc.opcode;
    }

    ::ibv_wc_status QueuePairEvent::getEventStatus() const {
        return wc.status;
    }

    Buffer* QueuePairEvent::getBuffer() const {
        Buffer* b = reinterpret_cast<Buffer*>(wc.wr_id);
        b->dataCount = wc.byte_len;
        return b;
    }

    QueuePair::QueuePair(boost::shared_ptr< ::rdma_cm_id > i) :
        qpid::sys::IOHandle(new qpid::sys::IOHandlePrivate),
        pd(allocPd(i->verbs)),
        cchannel(mkCChannel(i->verbs)),
        scq(mkCq(i->verbs, DEFAULT_CQ_ENTRIES, 0, cchannel.get())),
        rcq(mkCq(i->verbs, DEFAULT_CQ_ENTRIES, 0, cchannel.get())),
        outstandingSendEvents(0),
        outstandingRecvEvents(0)
    {
        impl->fd = cchannel->fd;

        // Set cq context to this QueuePair object so we can find
        // ourselves again
        scq->cq_context = this;
        rcq->cq_context = this;

        ::ibv_qp_init_attr qp_attr = {};

        // TODO: make a default struct for this
        qp_attr.cap.max_send_wr  = DEFAULT_WR_ENTRIES;
        qp_attr.cap.max_send_sge = 4;
        qp_attr.cap.max_recv_wr  = DEFAULT_WR_ENTRIES;
        qp_attr.cap.max_recv_sge = 4;

        qp_attr.send_cq      = scq.get();
        qp_attr.recv_cq      = rcq.get();
        qp_attr.qp_type      = IBV_QPT_RC;

        CHECK(::rdma_create_qp(i.get(), pd.get(), &qp_attr));
        qp = mkQp(i->qp);

        // Set the qp context to this so we can find ourselves again
        qp->qp_context = this;
    }

    QueuePair::~QueuePair() {
        if (outstandingSendEvents > 0)
            ::ibv_ack_cq_events(scq.get(), outstandingSendEvents);
        if (outstandingRecvEvents > 0)
            ::ibv_ack_cq_events(rcq.get(), outstandingRecvEvents);

        // Reset back pointer in case someone else has the qp
        qp->qp_context = 0;
    }

    // Create a buffer to use for writing
    Buffer* QueuePair::createBuffer(int s) {
        return new Buffer(pd.get(), new char[s], s);
    }

    // Make channel non-blocking by making
    // associated fd nonblocking
    void QueuePair::nonblocking() {
        ::fcntl(cchannel->fd, F_SETFL, O_NONBLOCK);
    }

    // If we get EAGAIN because the channel has been set non blocking
    // and we'd have to wait then return an empty event
    QueuePair::intrusive_ptr QueuePair::getNextChannelEvent() {
        // First find out which cq has the event
        ::ibv_cq* cq;
        void* ctx;
        int rc = ::ibv_get_cq_event(cchannel.get(), &cq, &ctx);
        if (rc == -1 && errno == EAGAIN)
            return 0;
        CHECK(rc);

        // Batch acknowledge the event
        if (cq == scq.get()) {
            if (++outstandingSendEvents > DEFAULT_CQ_ENTRIES / 2) {
                ::ibv_ack_cq_events(cq, outstandingSendEvents);
                outstandingSendEvents = 0;
            }
        } else if (cq == rcq.get()) {
            if (++outstandingRecvEvents > DEFAULT_CQ_ENTRIES / 2) {
                ::ibv_ack_cq_events(cq, outstandingRecvEvents);
                outstandingRecvEvents = 0;
            }
        }

        return static_cast<QueuePair*>(ctx);
    }

    QueuePairEvent QueuePair::getNextEvent() {
        ::ibv_wc w;
        if (::ibv_poll_cq(scq.get(), 1, &w) == 1)
            return QueuePairEvent(w, scq, SEND);
        else if (::ibv_poll_cq(rcq.get(), 1, &w) == 1)
            return QueuePairEvent(w, rcq, RECV);
        else
            return QueuePairEvent();
    }

    void QueuePair::notifyRecv() {
        CHECK_IBV(ibv_req_notify_cq(rcq.get(), 0));
    }

    void QueuePair::notifySend() {
        CHECK_IBV(ibv_req_notify_cq(scq.get(), 0));
    }

    void QueuePair::postRecv(Buffer* buf) {
        ::ibv_recv_wr rwr = {};
        ::ibv_sge sge;

        sge.addr = (uintptr_t) buf->bytes+buf->dataStart;
        sge.length = buf->dataCount;
        sge.lkey = buf->mr->lkey;

        rwr.wr_id = reinterpret_cast<uint64_t>(buf);
        rwr.sg_list = &sge;
        rwr.num_sge = 1;

        ::ibv_recv_wr* badrwr = 0;
        CHECK_IBV(::ibv_post_recv(qp.get(), &rwr, &badrwr));
        if (badrwr)
            throw std::logic_error("ibv_post_recv(): Bad rwr");
    }

    void QueuePair::postSend(Buffer* buf) {
        ::ibv_send_wr swr = {};
        ::ibv_sge sge;

        sge.addr = (uintptr_t) buf->bytes+buf->dataStart;
        sge.length = buf->dataCount;
        sge.lkey = buf->mr->lkey;

        swr.wr_id = reinterpret_cast<uint64_t>(buf);
        swr.opcode = IBV_WR_SEND;
        swr.send_flags = IBV_SEND_SIGNALED;
        swr.sg_list = &sge;
        swr.num_sge = 1;

        ::ibv_send_wr* badswr = 0;
        CHECK_IBV(::ibv_post_send(qp.get(), &swr, &badswr));
        if (badswr)
            throw std::logic_error("ibv_post_send(): Bad swr");
    }

    void QueuePair::postSend(uint32_t imm, Buffer* buf) {
        ::ibv_send_wr swr = {};
        ::ibv_sge sge;

        sge.addr = (uintptr_t) buf->bytes+buf->dataStart;
        sge.length = buf->dataCount;
        sge.lkey = buf->mr->lkey;
        swr.send_flags = IBV_SEND_SIGNALED;        

        swr.wr_id = reinterpret_cast<uint64_t>(buf);
        swr.imm_data = htonl(imm);
        swr.opcode = IBV_WR_SEND_WITH_IMM;
        swr.sg_list = &sge;
        swr.num_sge = 1;

        ::ibv_send_wr* badswr = 0;
        CHECK_IBV(::ibv_post_send(qp.get(), &swr, &badswr));
        if (badswr)
            throw std::logic_error("ibv_post_send(): Bad swr");
    }

    ConnectionEvent::ConnectionEvent(::rdma_cm_event* e) :
        id((e->event != RDMA_CM_EVENT_CONNECT_REQUEST) ?
                Connection::find(e->id) : new Connection(e->id)),
        listen_id(Connection::find(e->listen_id)),
        event(mkEvent(e))
    {}

    ConnectionEvent::operator bool() const {
        return event;
    }

    ::rdma_cm_event_type ConnectionEvent::getEventType() const {
        return event->event;
    }

    ::rdma_conn_param ConnectionEvent::getConnectionParam() const {
        // It's badly documented, but it seems from the librdma source code that all the following
        // event types have a valid param.conn
        switch (event->event) {
        case RDMA_CM_EVENT_CONNECT_REQUEST:
        case RDMA_CM_EVENT_ESTABLISHED:
        case RDMA_CM_EVENT_REJECTED:
        case RDMA_CM_EVENT_DISCONNECTED:
        case RDMA_CM_EVENT_CONNECT_ERROR:
            return event->param.conn;
        default:
            ::rdma_conn_param p = {};
            return p;
        }
    }

    boost::intrusive_ptr<Connection> ConnectionEvent::getConnection () const {
        return id;
    }

    boost::intrusive_ptr<Connection> ConnectionEvent::getListenId() const {
        return listen_id;
    }

    // Wrap the passed in rdma_cm_id with a Connection
    // this basically happens only on connection request
    Connection::Connection(::rdma_cm_id* i) :
        qpid::sys::IOHandle(new qpid::sys::IOHandlePrivate),
        id(mkId(i)),
        context(0)
    {
        impl->fd = id->channel->fd;

        // Just overwrite the previous context as it will
        // have come from the listening connection
        if (i)
            i->context = this;
    }

    Connection::Connection() :
        qpid::sys::IOHandle(new qpid::sys::IOHandlePrivate),
        channel(mkEChannel()),
        id(mkId(channel.get(), this, RDMA_PS_TCP)),
        context(0)
    {
        impl->fd = channel->fd;
    }

    Connection::~Connection() {
        // Reset the id context in case someone else has it
        id->context = 0;
    }

    void Connection::ensureQueuePair() {
        assert(id.get());

        // Only allocate a queue pair if there isn't one already
        if (qp)
            return;

        qp = new QueuePair(id);
    }

    Connection::intrusive_ptr Connection::make() {
        return new Connection();
    }

    Connection::intrusive_ptr Connection::find(::rdma_cm_id* i) {
        if (!i)
            return 0;
        Connection* id = static_cast< Connection* >(i->context);
        if (!id)
            throw std::logic_error("Couldn't find existing Connection");
        return id;
    }

    // Make channel non-blocking by making
    // associated fd nonblocking
    void Connection::nonblocking() {
        assert(id.get());
        ::fcntl(id->channel->fd, F_SETFL, O_NONBLOCK);
    }

    // If we get EAGAIN because the channel has been set non blocking
    // and we'd have to wait then return an empty event
    ConnectionEvent Connection::getNextEvent() {
        assert(id.get());
        ::rdma_cm_event* e;
        int rc = ::rdma_get_cm_event(id->channel, &e);
        if (GETERR(rc) == EAGAIN)
            return ConnectionEvent();
        CHECK(rc);
        return ConnectionEvent(e);
    }

    void Connection::bind(const qpid::sys::SocketAddress& src_addr) const {
        assert(id.get());
        CHECK(::rdma_bind_addr(id.get(), getAddrInfo(src_addr).ai_addr));
    }

    void Connection::listen(int backlog) const {
        assert(id.get());
        CHECK(::rdma_listen(id.get(), backlog));
    }

    void Connection::resolve_addr(
        const qpid::sys::SocketAddress& dst_addr,
        int timeout_ms) const
    {
        assert(id.get());
        CHECK(::rdma_resolve_addr(id.get(), 0, getAddrInfo(dst_addr).ai_addr, timeout_ms));
    }

    void Connection::resolve_route(int timeout_ms) const {
        assert(id.get());
        CHECK(::rdma_resolve_route(id.get(), timeout_ms));
    }

    void Connection::disconnect() const {
        assert(id.get());
        int rc = ::rdma_disconnect(id.get());
        // iWarp doesn't let you disconnect a disconnected connection
        // but Infiniband can do so it's okay to call rdma_disconnect()
        // in response to a disconnect event, but we may get an error
        if (GETERR(rc) == EINVAL)
            return;
        CHECK(rc);
    }

    // TODO: Currently you can only connect with the default connection parameters
    void Connection::connect(const void* data, size_t len) {
        assert(id.get());
        // Need to have a queue pair before we can connect
        ensureQueuePair();

        ::rdma_conn_param p = DEFAULT_CONNECT_PARAM;
        p.private_data = data;
        p.private_data_len = len;
        CHECK(::rdma_connect(id.get(), &p));
    }

    void Connection::connect() {
        connect(0, 0);
    }

    void Connection::accept(const ::rdma_conn_param& param, const void* data, size_t len) {
        assert(id.get());
        // Need to have a queue pair before we can accept
        ensureQueuePair();

        ::rdma_conn_param p = param;
        p.private_data = data;
        p.private_data_len = len;
        CHECK(::rdma_accept(id.get(), &p));
    }

    void Connection::accept(const ::rdma_conn_param& param) {
        accept(param, 0, 0);
    }

    void Connection::reject(const void* data, size_t len) const {
        assert(id.get());
        CHECK(::rdma_reject(id.get(), data, len));
    }

    void Connection::reject() const {
        assert(id.get());
        CHECK(::rdma_reject(id.get(), 0, 0));
    }

    QueuePair::intrusive_ptr Connection::getQueuePair() {
        assert(id.get());

        ensureQueuePair();

        return qp;
    }

    std::string Connection::getLocalName() const {
        ::sockaddr* addr = ::rdma_get_local_addr(id.get());
        char hostName[NI_MAXHOST];
        char portName[NI_MAXSERV];
        CHECK_IBV(::getnameinfo(
        addr, sizeof(::sockaddr_storage),
        hostName, sizeof(hostName),
        portName, sizeof(portName),
        NI_NUMERICHOST | NI_NUMERICSERV));
        std::string r(hostName);
        r += ":";
        r += portName;
        return r;
    }

    std::string Connection::getPeerName() const {
        ::sockaddr* addr = ::rdma_get_peer_addr(id.get());
        char hostName[NI_MAXHOST];
        char portName[NI_MAXSERV];
        CHECK_IBV(::getnameinfo(
        addr, sizeof(::sockaddr_storage),
        hostName, sizeof(hostName),
        portName, sizeof(portName),
        NI_NUMERICHOST | NI_NUMERICSERV));
        std::string r(hostName);
        r += ":";
        r += portName;
        return r;
    }
}

std::ostream& operator<<(std::ostream& o, ::rdma_cm_event_type t) {
#   define CHECK_TYPE(t) case t: o << #t; break;
    switch(t) {
        CHECK_TYPE(RDMA_CM_EVENT_ADDR_RESOLVED)
        CHECK_TYPE(RDMA_CM_EVENT_ADDR_ERROR)
        CHECK_TYPE(RDMA_CM_EVENT_ROUTE_RESOLVED)
        CHECK_TYPE(RDMA_CM_EVENT_ROUTE_ERROR)
        CHECK_TYPE(RDMA_CM_EVENT_CONNECT_REQUEST)
        CHECK_TYPE(RDMA_CM_EVENT_CONNECT_RESPONSE)
        CHECK_TYPE(RDMA_CM_EVENT_CONNECT_ERROR)
        CHECK_TYPE(RDMA_CM_EVENT_UNREACHABLE)
        CHECK_TYPE(RDMA_CM_EVENT_REJECTED)
        CHECK_TYPE(RDMA_CM_EVENT_ESTABLISHED)
        CHECK_TYPE(RDMA_CM_EVENT_DISCONNECTED)
        CHECK_TYPE(RDMA_CM_EVENT_DEVICE_REMOVAL)
        CHECK_TYPE(RDMA_CM_EVENT_MULTICAST_JOIN)
        CHECK_TYPE(RDMA_CM_EVENT_MULTICAST_ERROR)
    default:
         o << "UNKNOWN_EVENT";
    }
#   undef CHECK_TYPE
    return o;
}
