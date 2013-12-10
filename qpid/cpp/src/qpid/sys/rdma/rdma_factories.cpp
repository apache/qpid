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
#include "qpid/sys/rdma/rdma_factories.h"

#include "qpid/sys/rdma/rdma_exception.h"


namespace Rdma {
    // Intentionally ignore return values for these functions
    // - we can't do anything about then anyway
    void acker(::rdma_cm_event* e) throw () {
        if (e) (void) ::rdma_ack_cm_event(e);
    }

    void destroyEChannel(::rdma_event_channel* c) throw () {
        if (c) (void) ::rdma_destroy_event_channel(c);
    }

    void destroyId(::rdma_cm_id* i) throw () {
        if (i) (void) ::rdma_destroy_id(i);
    }

    void deallocPd(::ibv_pd* p) throw () {
        if (p) (void) ::ibv_dealloc_pd(p);
    }

    void deregMr(::ibv_mr* mr) throw () {
        if (mr) (void) ::ibv_dereg_mr(mr);
    }

    void destroyCChannel(::ibv_comp_channel* c) throw () {
        if (c) (void) ::ibv_destroy_comp_channel(c);
    }

    void destroyCq(::ibv_cq* cq) throw () {
        if (cq) (void) ::ibv_destroy_cq(cq);
    }

    void destroyQp(::ibv_qp* qp) throw () {
        if (qp) (void) ::ibv_destroy_qp(qp);
    }

    boost::shared_ptr< ::rdma_cm_id > mkId(::rdma_cm_id* i) {
        return boost::shared_ptr< ::rdma_cm_id >(i, destroyId);
    }
    
    boost::shared_ptr< ::rdma_cm_event > mkEvent(::rdma_cm_event* e) {
        return boost::shared_ptr< ::rdma_cm_event >(e, acker);
    }
    
    boost::shared_ptr< ::ibv_qp > mkQp(::ibv_qp* qp) {
    	return boost::shared_ptr< ::ibv_qp > (qp, destroyQp);
    }

    boost::shared_ptr< ::rdma_event_channel > mkEChannel() {
        ::rdma_event_channel* c = CHECK_NULL(::rdma_create_event_channel());
        return boost::shared_ptr< ::rdma_event_channel >(c, destroyEChannel);
    }

    boost::shared_ptr< ::rdma_cm_id >
    mkId(::rdma_event_channel* ec, void* context, ::rdma_port_space ps) {
        ::rdma_cm_id* i;
        CHECK(::rdma_create_id(ec, &i, context, ps));
        return mkId(i);
    }

    boost::shared_ptr< ::ibv_pd > allocPd(::ibv_context* c) {
        ::ibv_pd* pd = CHECK_NULL(::ibv_alloc_pd(c));
        return boost::shared_ptr< ::ibv_pd >(pd, deallocPd);
    }

    boost::shared_ptr< ::ibv_mr > regMr(::ibv_pd* pd, void* addr, size_t length, ::ibv_access_flags access) {
        ::ibv_mr* mr = CHECK_NULL(::ibv_reg_mr(pd, addr, length, access));
        return boost::shared_ptr< ::ibv_mr >(mr, deregMr);
    }

    boost::shared_ptr< ::ibv_comp_channel > mkCChannel(::ibv_context* c) {
        ::ibv_comp_channel* cc = CHECK_NULL(::ibv_create_comp_channel(c));
        return boost::shared_ptr< ::ibv_comp_channel >(cc, destroyCChannel);
    }

    boost::shared_ptr< ::ibv_cq >
    mkCq(::ibv_context* c, int cqe, void* context, ::ibv_comp_channel* cc) {
        ::ibv_cq* cq = CHECK_NULL(::ibv_create_cq(c, cqe, context, cc, 0));
        return boost::shared_ptr< ::ibv_cq >(cq, destroyCq);
    }
}
