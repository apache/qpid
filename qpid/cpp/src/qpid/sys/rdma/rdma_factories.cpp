#include "rdma_factories.h"

namespace Rdma {
    void acker(::rdma_cm_event* e) throw () {
        if (e)
            // Intentionally ignore return value - we can't do anything about it here
            (void) ::rdma_ack_cm_event(e);
    }

    void destroyEChannel(::rdma_event_channel* c) throw () {
        if (c)
            // Intentionally ignore return value - we can't do anything about it here
            (void) ::rdma_destroy_event_channel(c);
    }

    void destroyId(::rdma_cm_id* i) throw () {
        if (i)
            // Intentionally ignore return value - we can't do anything about it here
            (void) ::rdma_destroy_id(i);
    }

    void deallocPd(::ibv_pd* p) throw () {
        if (p)
            // Intentionally ignore return value - we can't do anything about it here
            (void) ::ibv_dealloc_pd(p);
    }

    void destroyCChannel(::ibv_comp_channel* c) throw () {
        if (c)
            // Intentionally ignore return value - we can't do anything about it here
            (void) ::ibv_destroy_comp_channel(c);
    }

    void destroyCq(::ibv_cq* cq) throw () {
        if (cq)
            (void) ::ibv_destroy_cq(cq);
    }

}
