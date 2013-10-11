/*
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
 */

#include <qpid/dispatch/python_embedded.h>
#include <stdio.h>
#include <string.h>
#include <stdbool.h>
#include <stdlib.h>
#include <qpid/dispatch.h>
#include "dispatch_private.h"
#include "router_private.h"

static char *module = "router.pynode";

typedef struct {
    PyObject_HEAD
    dx_router_t *router;
} RouterAdapter;


static char *dx_add_router(dx_router_t *router, const char *address, int router_maskbit, int link_maskbit)
{
    if (router_maskbit >= dx_bitmask_width() || router_maskbit < 0)
        return "Router bit mask out of range";

    if (link_maskbit >= dx_bitmask_width() || link_maskbit < -1)
        return "Link bit mask out of range";

    sys_mutex_lock(router->lock);
    if (router->routers_by_mask_bit[router_maskbit] != 0) {
        sys_mutex_unlock(router->lock);
        return "Adding router over already existing router";
    }

    if (link_maskbit >= 0 && router->out_links_by_mask_bit[link_maskbit] == 0) {
        sys_mutex_unlock(router->lock);
        return "Adding neighbor router with invalid link reference";
    }

    //
    // Hash lookup the address to ensure there isn't an existing router address.
    //
    dx_field_iterator_t *iter = dx_field_iterator_string(address, ITER_VIEW_ADDRESS_HASH);
    dx_address_t        *addr;

    dx_hash_retrieve(router->addr_hash, iter, (void**) &addr);
    assert(addr == 0);

    //
    // Create an address record for this router and insert it in the hash table.
    // This record will be found whenever a "foreign" topological address to this
    // remote router is looked up.
    //
    addr = new_dx_address_t();
    memset(addr, 0, sizeof(dx_address_t));
    DEQ_ITEM_INIT(addr);
    DEQ_INIT(addr->rlinks);
    DEQ_INIT(addr->rnodes);
    dx_hash_insert(router->addr_hash, iter, addr, &addr->hash_handle);
    DEQ_INSERT_TAIL(router->addrs, addr);
    dx_field_iterator_free(iter);

    //
    // Create a router-node record to represent the remote router.
    //
    dx_router_node_t *rnode = new_dx_router_node_t();
    DEQ_ITEM_INIT(rnode);
    rnode->owning_addr   = addr;
    rnode->mask_bit      = router_maskbit;
    rnode->next_hop      = 0;
    rnode->peer_link     = 0;
    rnode->ref_count     = 0;
    rnode->valid_origins = dx_bitmask(0);

    DEQ_INSERT_TAIL(router->routers, rnode);

    //
    // Link the router record to the address record.
    //
    dx_router_add_node_ref_LH(&addr->rnodes, rnode);

    //
    // Link the router record to the router address record.
    //
    dx_router_add_node_ref_LH(&router->router_addr->rnodes, rnode);

    //
    // Add the router record to the mask-bit index.
    //
    router->routers_by_mask_bit[router_maskbit] = rnode;

    //
    // If this is a neighbor router, add the peer_link reference to the
    // router record.
    //
    if (link_maskbit >= 0)
        rnode->peer_link = router->out_links_by_mask_bit[link_maskbit];

    sys_mutex_unlock(router->lock);
    return 0;
}


static char *dx_del_router(dx_router_t *router, int router_maskbit)
{
    if (router_maskbit >= dx_bitmask_width() || router_maskbit < 0)
        return "Router bit mask out of range";

    sys_mutex_lock(router->lock);
    if (router->routers_by_mask_bit[router_maskbit] == 0) {
        sys_mutex_unlock(router->lock);
        return "Deleting nonexistent router";
    }

    dx_router_node_t *rnode = router->routers_by_mask_bit[router_maskbit];
    dx_address_t     *oaddr = rnode->owning_addr;
    assert(oaddr);

    //
    // Unlink the router node from the address record
    //
    dx_router_del_node_ref_LH(&oaddr->rnodes, rnode);

    //
    // While the router node has a non-zero reference count, look for addresses
    // to unlink the node from.
    //
    dx_address_t *addr = DEQ_HEAD(router->addrs);
    while (addr && rnode->ref_count > 0) {
        dx_router_del_node_ref_LH(&addr->rnodes, rnode);
        addr = DEQ_NEXT(addr);
    }
    assert(rnode->ref_count == 0);

    //
    // Free the router node and the owning address records.
    //
    dx_bitmask_free(rnode->valid_origins);
    DEQ_REMOVE(router->routers, rnode);
    free_dx_router_node_t(rnode);

    dx_hash_remove_by_handle(router->addr_hash, oaddr->hash_handle);
    DEQ_REMOVE(router->addrs, oaddr);
    dx_hash_handle_free(oaddr->hash_handle);
    router->routers_by_mask_bit[router_maskbit] = 0;
    free_dx_address_t(oaddr);

    sys_mutex_unlock(router->lock);
    return 0;
}


static PyObject* dx_add_remote_router(PyObject *self, PyObject *args)
{
    RouterAdapter *adapter = (RouterAdapter*) self;
    dx_router_t   *router  = adapter->router;
    const char    *address;
    int            router_maskbit;

    if (!PyArg_ParseTuple(args, "si", &address, &router_maskbit))
        return 0;

    char *error = dx_add_router(router, address, router_maskbit, -1);
    if (error) {
        PyErr_SetString(PyExc_Exception, error);
        return 0;
    }

    Py_INCREF(Py_None);
    return Py_None;
}


static PyObject* dx_del_remote_router(PyObject *self, PyObject *args)
{
    RouterAdapter *adapter = (RouterAdapter*) self;
    dx_router_t   *router  = adapter->router;
    int router_maskbit;

    if (!PyArg_ParseTuple(args, "i", &router_maskbit))
        return 0;

    char *error = dx_del_router(router, router_maskbit);
    if (error) {
        PyErr_SetString(PyExc_Exception, error);
        return 0;
    }

    Py_INCREF(Py_None);
    return Py_None;
}


static PyObject* dx_set_next_hop(PyObject *self, PyObject *args)
{
    RouterAdapter *adapter = (RouterAdapter*) self;
    dx_router_t   *router  = adapter->router;
    int            router_maskbit;
    int            next_hop_maskbit;

    if (!PyArg_ParseTuple(args, "ii", &router_maskbit, &next_hop_maskbit))
        return 0;

    if (router_maskbit >= dx_bitmask_width() || router_maskbit < 0) {
        PyErr_SetString(PyExc_Exception, "Router bit mask out of range");
        return 0;
    }

    if (next_hop_maskbit >= dx_bitmask_width() || next_hop_maskbit < 0) {
        PyErr_SetString(PyExc_Exception, "Next Hop bit mask out of range");
        return 0;
    }

    if (router->routers_by_mask_bit[router_maskbit] == 0) {
        PyErr_SetString(PyExc_Exception, "Router Not Found");
        return 0;
    }

    if (router->routers_by_mask_bit[next_hop_maskbit] == 0) {
        PyErr_SetString(PyExc_Exception, "Next Hop Not Found");
        return 0;
    }

    if (router_maskbit != next_hop_maskbit) {
        dx_router_node_t *rnode = router->routers_by_mask_bit[router_maskbit];
        rnode->next_hop = router->routers_by_mask_bit[next_hop_maskbit];
    }

    Py_INCREF(Py_None);
    return Py_None;
}


static PyObject* dx_set_valid_origins(PyObject *self, PyObject *args)
{
    RouterAdapter *adapter = (RouterAdapter*) self;
    dx_router_t   *router  = adapter->router;
    int            router_maskbit;
    PyObject      *origin_list;
    Py_ssize_t     idx;

    if (!PyArg_ParseTuple(args, "iO", &router_maskbit, &origin_list))
        return 0;

    if (router_maskbit >= dx_bitmask_width() || router_maskbit < 0) {
        PyErr_SetString(PyExc_Exception, "Router bit mask out of range");
        return 0;
    }

    if (router->routers_by_mask_bit[router_maskbit] == 0) {
        PyErr_SetString(PyExc_Exception, "Router Not Found");
        return 0;
    }

    if (!PyList_Check(origin_list)) {
        PyErr_SetString(PyExc_Exception, "Expected List as argument 2");
        return 0;
    }

    Py_ssize_t        origin_count = PyList_Size(origin_list);
    dx_router_node_t *rnode        = router->routers_by_mask_bit[router_maskbit];
    int               maskbit;

    for (idx = 0; idx < origin_count; idx++) {
        maskbit = PyInt_AS_LONG(PyList_GetItem(origin_list, idx));

        if (maskbit >= dx_bitmask_width() || maskbit < 0) {
            PyErr_SetString(PyExc_Exception, "Origin bit mask out of range");
            return 0;
        }
        
        if (router->routers_by_mask_bit[maskbit] == 0) {
            PyErr_SetString(PyExc_Exception, "Origin router Not Found");
            return 0;
        }
    }

    dx_bitmask_clear_all(rnode->valid_origins);
    dx_bitmask_set_bit(rnode->valid_origins, 0);  // This router is a valid origin for all destinations
    for (idx = 0; idx < origin_count; idx++) {
        maskbit = PyInt_AS_LONG(PyList_GetItem(origin_list, idx));
        dx_bitmask_set_bit(rnode->valid_origins, maskbit);
    }

    Py_INCREF(Py_None);
    return Py_None;
}


static PyObject* dx_add_neighbor_router(PyObject *self, PyObject *args)
{
    RouterAdapter *adapter = (RouterAdapter*) self;
    dx_router_t   *router  = adapter->router;
    const char    *address;
    int            router_maskbit;
    int            link_maskbit;

    if (!PyArg_ParseTuple(args, "sii", &address, &router_maskbit, &link_maskbit))
        return 0;

    char *error = dx_add_router(router, address, router_maskbit, link_maskbit);
    if (error) {
        PyErr_SetString(PyExc_Exception, error);
        return 0;
    }

    Py_INCREF(Py_None);
    return Py_None;
}


static PyObject* dx_del_neighbor_router(PyObject *self, PyObject *args)
{
    RouterAdapter *adapter = (RouterAdapter*) self;
    dx_router_t   *router  = adapter->router;
    int router_maskbit;

    if (!PyArg_ParseTuple(args, "i", &router_maskbit))
        return 0;

    char *error = dx_del_router(router, router_maskbit);
    if (error) {
        PyErr_SetString(PyExc_Exception, error);
        return 0;
    }

    Py_INCREF(Py_None);
    return Py_None;
}


static PyObject* dx_map_destination(PyObject *self, PyObject *args)
{
    //RouterAdapter *adapter = (RouterAdapter*) self;
    //dx_router_t   *router  = adapter->router;
    const char *addr;
    int         router_maskbit;

    if (!PyArg_ParseTuple(args, "si", &addr, &router_maskbit))
        return 0;

    // TODO

    Py_INCREF(Py_None);
    return Py_None;
}


static PyObject* dx_unmap_destination(PyObject *self, PyObject *args)
{
    //RouterAdapter *adapter = (RouterAdapter*) self;
    //dx_router_t   *router  = adapter->router;
    const char *addr;
    int         router_maskbit;

    if (!PyArg_ParseTuple(args, "si", &addr, &router_maskbit))
        return 0;

    // TODO

    Py_INCREF(Py_None);
    return Py_None;
}


static PyMethodDef RouterAdapter_methods[] = {
    {"add_remote_router",   dx_add_remote_router,   METH_VARARGS, "A new remote/reachable router has been discovered"},
    {"del_remote_router",   dx_del_remote_router,   METH_VARARGS, "We've lost reachability to a remote router"},
    {"set_next_hop",        dx_set_next_hop,        METH_VARARGS, "Set the next hop for a remote router"},
    {"set_valid_origins",   dx_set_valid_origins,   METH_VARARGS, "Set the valid origins for a remote router"},
    {"add_neighbor_router", dx_add_neighbor_router, METH_VARARGS, "A new neighbor router has been discovered"},
    {"del_neighbor_router", dx_del_neighbor_router, METH_VARARGS, "We've lost reachability to a neighbor router"},
    {"map_destination",     dx_map_destination,     METH_VARARGS, "Add a newly discovered destination mapping"},
    {"unmap_destination",   dx_unmap_destination,   METH_VARARGS, "Delete a destination mapping"},
    {0, 0, 0, 0}
};

static PyTypeObject RouterAdapterType = {
    PyObject_HEAD_INIT(0)
    0,                         /* ob_size*/
    "dispatch.RouterAdapter",  /* tp_name*/
    sizeof(RouterAdapter),     /* tp_basicsize*/
    0,                         /* tp_itemsize*/
    0,                         /* tp_dealloc*/
    0,                         /* tp_print*/
    0,                         /* tp_getattr*/
    0,                         /* tp_setattr*/
    0,                         /* tp_compare*/
    0,                         /* tp_repr*/
    0,                         /* tp_as_number*/
    0,                         /* tp_as_sequence*/
    0,                         /* tp_as_mapping*/
    0,                         /* tp_hash */
    0,                         /* tp_call*/
    0,                         /* tp_str*/
    0,                         /* tp_getattro*/
    0,                         /* tp_setattro*/
    0,                         /* tp_as_buffer*/
    Py_TPFLAGS_DEFAULT,        /* tp_flags*/
    "Dispatch Router Adapter", /* tp_doc */
    0,                         /* tp_traverse */
    0,                         /* tp_clear */
    0,                         /* tp_richcompare */
    0,                         /* tp_weaklistoffset */
    0,                         /* tp_iter */
    0,                         /* tp_iternext */
    RouterAdapter_methods,     /* tp_methods */
    0,                         /* tp_members */
    0,                         /* tp_getset */
    0,                         /* tp_base */
    0,                         /* tp_dict */
    0,                         /* tp_descr_get */
    0,                         /* tp_descr_set */
    0,                         /* tp_dictoffset */
    0,                         /* tp_init */
    0,                         /* tp_alloc */
    0,                         /* tp_new */
    0,                         /* tp_free */
    0,                         /* tp_is_gc */
    0,                         /* tp_bases */
    0,                         /* tp_mro */
    0,                         /* tp_cache */
    0,                         /* tp_subclasses */
    0,                         /* tp_weaklist */
    0,                         /* tp_del */
    0                          /* tp_version_tag */
};


void dx_router_python_setup(dx_router_t *router)
{
    //
    // If we are not operating as an interior router, don't start the
    // router module.
    //
    if (router->router_mode != DX_ROUTER_MODE_INTERIOR)
        return;

    PyObject *pDispatchModule = dx_python_module();

    RouterAdapterType.tp_new = PyType_GenericNew;
    if (PyType_Ready(&RouterAdapterType) < 0) {
        PyErr_Print();
        dx_log(module, LOG_CRITICAL, "Unable to initialize the Python Router Adapter");
        return;
    }

    PyTypeObject *raType = &RouterAdapterType;
    Py_INCREF(raType);
    PyModule_AddObject(pDispatchModule, "RouterAdapter", (PyObject*) &RouterAdapterType);

    //
    // Attempt to import the Python Router module
    //
    PyObject* pName;
    PyObject* pId;
    PyObject* pArea;
    PyObject* pMaxRouters;
    PyObject* pModule;
    PyObject* pClass;
    PyObject* pArgs;

    pName   = PyString_FromString("qpid.dispatch.router");
    pModule = PyImport_Import(pName);
    Py_DECREF(pName);
    if (!pModule) {
        dx_log(module, LOG_CRITICAL, "Can't Locate 'router' Python module");
        return;
    }

    pClass = PyObject_GetAttrString(pModule, "RouterEngine");
    if (!pClass || !PyClass_Check(pClass)) {
        dx_log(module, LOG_CRITICAL, "Can't Locate 'RouterEngine' class in the 'router' module");
        return;
    }

    PyObject *adapterType     = PyObject_GetAttrString(pDispatchModule, "RouterAdapter");
    PyObject *adapterInstance = PyObject_CallObject(adapterType, 0);
    assert(adapterInstance);

    ((RouterAdapter*) adapterInstance)->router = router;

    //
    // Constructor Arguments for RouterEngine
    //
    pArgs = PyTuple_New(4);

    // arg 0: adapter instance
    PyTuple_SetItem(pArgs, 0, adapterInstance);

    // arg 1: router_id
    pId = PyString_FromString(router->router_id);
    PyTuple_SetItem(pArgs, 1, pId);

    // arg 2: area_id
    pArea = PyString_FromString(router->router_area);
    PyTuple_SetItem(pArgs, 2, pArea);

    // arg 3: max_routers
    pMaxRouters = PyInt_FromLong((long) dx_bitmask_width());
    PyTuple_SetItem(pArgs, 3, pMaxRouters);

    //
    // Instantiate the router
    //
    router->pyRouter = PyInstance_New(pClass, pArgs, 0);
    Py_DECREF(pArgs);
    Py_DECREF(adapterType);

    if (!router->pyRouter) {
        PyErr_Print();
        dx_log(module, LOG_CRITICAL, "'RouterEngine' class cannot be instantiated");
        return;
    }

    router->pyTick = PyObject_GetAttrString(router->pyRouter, "handleTimerTick");
    if (!router->pyTick || !PyCallable_Check(router->pyTick)) {
        dx_log(module, LOG_CRITICAL, "'RouterEngine' class has no handleTimerTick method");
        return;
    }
}


void dx_pyrouter_tick(dx_router_t *router)
{
    PyObject *pArgs;
    PyObject *pValue;

    if (router->pyTick && router->router_mode == DX_ROUTER_MODE_INTERIOR) {
        dx_python_lock();
        pArgs  = PyTuple_New(0);
        pValue = PyObject_CallObject(router->pyTick, pArgs);
        if (PyErr_Occurred()) {
            PyErr_Print();
        }
        Py_DECREF(pArgs);
        if (pValue) {
            Py_DECREF(pValue);
        }
        dx_python_unlock();
    }
}

