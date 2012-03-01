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
#ifndef RDMA_FACTORIES_H
#define RDMA_FACTORIES_H

#include <rdma/rdma_cma.h>

#include <boost/shared_ptr.hpp>

namespace Rdma {
    boost::shared_ptr< ::rdma_event_channel > mkEChannel();
    boost::shared_ptr< ::rdma_cm_id > mkId(::rdma_event_channel* ec, void* context, ::rdma_port_space ps);
    boost::shared_ptr< ::rdma_cm_id > mkId(::rdma_cm_id* i);
    boost::shared_ptr< ::rdma_cm_event > mkEvent(::rdma_cm_event* e);
    boost::shared_ptr< ::ibv_qp > mkQp(::ibv_qp* qp);
    boost::shared_ptr< ::ibv_pd > allocPd(::ibv_context* c);
    boost::shared_ptr< ::ibv_mr > regMr(::ibv_pd* pd, void* addr, size_t length, ::ibv_access_flags access);
    boost::shared_ptr< ::ibv_comp_channel > mkCChannel(::ibv_context* c);
    boost::shared_ptr< ::ibv_cq > mkCq(::ibv_context* c, int cqe, void* context, ::ibv_comp_channel* cc);
}

#endif // RDMA_FACTORIES_H
