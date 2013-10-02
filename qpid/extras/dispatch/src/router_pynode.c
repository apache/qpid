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


static PyObject* dx_router_node_updated(PyObject *self, PyObject *args)
{
    //RouterAdapter *adapter = (RouterAdapter*) self;
    //dx_router_t   *router  = adapter->router;
    const char    *address;
    int            is_reachable;
    int            is_neighbor;
    int            link_maskbit;
    int            router_maskbit;

    if (!PyArg_ParseTuple(args, "siiii", &address, &is_reachable, &is_neighbor,
                          &link_maskbit, &router_maskbit))
        return 0;

    // TODO

    Py_INCREF(Py_None);
    return Py_None;
}


static PyObject* dx_router_add_route(PyObject *self, PyObject *args)
{
    //RouterAdapter *adapter = (RouterAdapter*) self;
    const char    *addr;
    const char    *peer;

    if (!PyArg_ParseTuple(args, "ss", &addr, &peer))
        return 0;

    // TODO

    Py_INCREF(Py_None);
    return Py_None;
}


static PyObject* dx_router_del_route(PyObject *self, PyObject *args)
{
    //RouterAdapter *adapter = (RouterAdapter*) self;
    const char    *addr;
    const char    *peer;

    if (!PyArg_ParseTuple(args, "ss", &addr, &peer))
        return 0;

    // TODO

    Py_INCREF(Py_None);
    return Py_None;
}


static PyMethodDef RouterAdapter_methods[] = {
    {"node_updated", dx_router_node_updated, METH_VARARGS, "Update the status of a remote router node"},
    {"add_route",    dx_router_add_route,    METH_VARARGS, "Add a newly discovered route"},
    {"del_route",    dx_router_del_route,    METH_VARARGS, "Delete a route"},
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

    if (router->pyTick) {
        pArgs  = PyTuple_New(0);
        pValue = PyObject_CallObject(router->pyTick, pArgs);
        if (PyErr_Occurred()) {
            PyErr_Print();
        }
        Py_DECREF(pArgs);
        if (pValue) {
            Py_DECREF(pValue);
        }
    }
}

