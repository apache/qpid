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
#include <qpid/dispatch/threading.h>
#include <qpid/dispatch/log.h>
#include <qpid/dispatch/amqp.h>
#include <qpid/dispatch/alloc.h>


//===============================================================================
// Control Functions
//===============================================================================

static uint32_t     ref_count  = 0;
static sys_mutex_t *lock       = 0;
static char        *log_module = "PYTHON";

static void dx_python_setup();


void dx_python_initialize()
{
    lock = sys_mutex();
}


void dx_python_finalize()
{
    assert(ref_count == 0);
    sys_mutex_free(lock);
}


void dx_python_start()
{
    sys_mutex_lock(lock);
    if (ref_count == 0) {
        Py_Initialize();
        dx_python_setup();
        dx_log(log_module, LOG_TRACE, "Embedded Python Interpreter Initialized");
    }
    ref_count++;
    sys_mutex_unlock(lock);
}


void dx_python_stop()
{
    sys_mutex_lock(lock);
    ref_count--;
    if (ref_count == 0) {
        Py_Finalize();
        dx_log(log_module, LOG_TRACE, "Embedded Python Interpreter Shut Down");
    }
    sys_mutex_unlock(lock);
}


//===============================================================================
// Data Conversion Functions
//===============================================================================

static PyObject *parsed_to_py_string(dx_parsed_field_t *field)
{
    switch (dx_parse_tag(field)) {
    case DX_AMQP_VBIN8:
    case DX_AMQP_VBIN32:
    case DX_AMQP_STR8_UTF8:
    case DX_AMQP_STR32_UTF8:
    case DX_AMQP_SYM8:
    case DX_AMQP_SYM32:
        break;
    default:
        return Py_None;
    }

#define SHORT_BUF 1024
    uint8_t short_buf[SHORT_BUF];
    PyObject *result;
    dx_field_iterator_t *raw = dx_parse_raw(field);
    dx_field_iterator_reset(raw);
    uint32_t length = dx_field_iterator_remaining(raw);
    uint8_t *buffer = short_buf;
    uint8_t *ptr;
    int alloc = 0;

    if (length > SHORT_BUF) {
        alloc = 1;
        buffer = (uint8_t*) malloc(length);
    }

    ptr = buffer;
    while (!dx_field_iterator_end(raw))
        *(ptr++) = dx_field_iterator_octet(raw);
    result = PyString_FromStringAndSize((char*) buffer, ptr - buffer);
    if (alloc)
        free(buffer);

    return result;
}


void dx_py_to_composed(PyObject *value, dx_composed_field_t *field)
{
    if      (PyBool_Check(value))
        dx_compose_insert_bool(field, PyInt_AS_LONG(value) ? 1 : 0);

    //else if (PyFloat_Check(value))
    //    dx_compose_insert_double(field, PyFloat_AS_DOUBLE(value));

    else if (PyInt_Check(value))
        dx_compose_insert_long(field, (int64_t) PyInt_AS_LONG(value));

    else if (PyLong_Check(value))
        dx_compose_insert_long(field, (int64_t) PyLong_AsLongLong(value));

    else if (PyString_Check(value))
        dx_compose_insert_string(field, PyString_AS_STRING(value));

    else if (PyDict_Check(value)) {
        Py_ssize_t  iter = 0;
        PyObject   *key;
        PyObject   *val;
        dx_compose_start_map(field);
        while (PyDict_Next(value, &iter, &key, &val)) {
            dx_py_to_composed(key, field);
            dx_py_to_composed(val, field);
        }
        dx_compose_end_map(field);
    }

    else if (PyList_Check(value)) {
        Py_ssize_t count = PyList_Size(value);
        dx_compose_start_list(field);
        for (Py_ssize_t idx = 0; idx < count; idx++) {
            PyObject *item = PyList_GetItem(value, idx);
            dx_py_to_composed(item, field);
        }
        dx_compose_end_list(field);
    }

    else if (PyTuple_Check(value)) {
        Py_ssize_t count = PyTuple_Size(value);
        dx_compose_start_list(field);
        for (Py_ssize_t idx = 0; idx < count; idx++) {
            PyObject *item = PyTuple_GetItem(value, idx);
            dx_py_to_composed(item, field);
        }
        dx_compose_end_list(field);
    }
}


PyObject *dx_field_to_py(dx_parsed_field_t *field)
{
    PyObject *result = Py_None;
    uint8_t   tag    = dx_parse_tag(field);

    switch (tag) {
    case DX_AMQP_NULL:
        result = Py_None;
        break;

    case DX_AMQP_BOOLEAN:
    case DX_AMQP_TRUE:
    case DX_AMQP_FALSE:
        result = dx_parse_as_uint(field) ? Py_True : Py_False;
        break;

    case DX_AMQP_UBYTE:
    case DX_AMQP_USHORT:
    case DX_AMQP_UINT:
    case DX_AMQP_SMALLUINT:
    case DX_AMQP_UINT0:
        result = PyInt_FromLong((long) dx_parse_as_uint(field));
        break;

    case DX_AMQP_ULONG:
    case DX_AMQP_SMALLULONG:
    case DX_AMQP_ULONG0:
    case DX_AMQP_TIMESTAMP:
        result = PyLong_FromUnsignedLongLong((unsigned PY_LONG_LONG) dx_parse_as_ulong(field));
        break;

    case DX_AMQP_BYTE:
    case DX_AMQP_SHORT:
    case DX_AMQP_INT:
    case DX_AMQP_SMALLINT:
        result = PyInt_FromLong((long) dx_parse_as_int(field));
        break;

    case DX_AMQP_LONG:
    case DX_AMQP_SMALLLONG:
        result = PyLong_FromUnsignedLongLong((unsigned PY_LONG_LONG) dx_parse_as_long(field));
        break;

    case DX_AMQP_FLOAT:
    case DX_AMQP_DOUBLE:
    case DX_AMQP_DECIMAL32:
    case DX_AMQP_DECIMAL64:
    case DX_AMQP_DECIMAL128:
    case DX_AMQP_UTF32:
    case DX_AMQP_UUID:
        break;

    case DX_AMQP_VBIN8:
    case DX_AMQP_VBIN32:
    case DX_AMQP_STR8_UTF8:
    case DX_AMQP_STR32_UTF8:
    case DX_AMQP_SYM8:
    case DX_AMQP_SYM32:
        result = parsed_to_py_string(field);
        break;

    case DX_AMQP_LIST0:
    case DX_AMQP_LIST8:
    case DX_AMQP_LIST32: {
        uint32_t count = dx_parse_sub_count(field);
        result = PyList_New(count);
        for (uint32_t idx = 0; idx < count; idx++) {
            dx_parsed_field_t *sub = dx_parse_sub_value(field, idx);
            PyObject *pysub = dx_field_to_py(sub);
            if (pysub == 0)
                return 0;
            PyList_SetItem(result, idx, pysub);
        }
        break;
    }
    case DX_AMQP_MAP8:
    case DX_AMQP_MAP32: {
        uint32_t count = dx_parse_sub_count(field);
        result = PyDict_New();
        for (uint32_t idx = 0; idx < count; idx++) {
            dx_parsed_field_t *key = dx_parse_sub_key(field, idx);
            dx_parsed_field_t *val = dx_parse_sub_value(field, idx);
            PyObject *pykey = parsed_to_py_string(key);
            PyObject *pyval = dx_field_to_py(val);
            if (pyval == 0)
                return 0;
            PyDict_SetItem(result, pykey, pyval);
            Py_DECREF(pykey);
            Py_DECREF(pyval);
        }
        break;
    }
    case DX_AMQP_ARRAY8:
    case DX_AMQP_ARRAY32:
        break;
    }

    return result;
}


//===============================================================================
// Logging Object
//===============================================================================

typedef struct {
    PyObject_HEAD
    PyObject *module_name;
} LogAdapter;


static int LogAdapter_init(LogAdapter *self, PyObject *args, PyObject *kwds)
{
    const char *text;
    if (!PyArg_ParseTuple(args, "s", &text))
        return -1;

    self->module_name = PyString_FromString(text);
    return 0;
}


static void LogAdapter_dealloc(LogAdapter* self)
{
    Py_XDECREF(self->module_name);
    self->ob_type->tp_free((PyObject*)self);
}


static PyObject* dx_python_log(PyObject *self, PyObject *args)
{
    int level;
    const char* text;

    if (!PyArg_ParseTuple(args, "is", &level, &text))
        return 0;

    LogAdapter *self_ptr = (LogAdapter*) self;
    char       *logmod   = PyString_AS_STRING(self_ptr->module_name);

    dx_log(logmod, level, text);

    Py_INCREF(Py_None);
    return Py_None;
}


static PyMethodDef LogAdapter_methods[] = {
    {"log", dx_python_log, METH_VARARGS, "Emit a Log Line"},
    {0, 0, 0, 0}
};

static PyMethodDef empty_methods[] = {
  {0, 0, 0, 0}
};

static PyTypeObject LogAdapterType = {
    PyObject_HEAD_INIT(0)
    0,                         /* ob_size*/
    "dispatch.LogAdapter",     /* tp_name*/
    sizeof(LogAdapter),        /* tp_basicsize*/
    0,                         /* tp_itemsize*/
    (destructor)LogAdapter_dealloc, /* tp_dealloc*/
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
    "Dispatch Log Adapter",    /* tp_doc */
    0,                         /* tp_traverse */
    0,                         /* tp_clear */
    0,                         /* tp_richcompare */
    0,                         /* tp_weaklistoffset */
    0,                         /* tp_iter */
    0,                         /* tp_iternext */
    LogAdapter_methods,        /* tp_methods */
    0,                         /* tp_members */
    0,                         /* tp_getset */
    0,                         /* tp_base */
    0,                         /* tp_dict */
    0,                         /* tp_descr_get */
    0,                         /* tp_descr_set */
    0,                         /* tp_dictoffset */
    (initproc)LogAdapter_init, /* tp_init */
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


//===============================================================================
// Message IO Object
//===============================================================================

typedef struct dx_python_io_adapter {
    int x;
} dx_python_io_adapter;

ALLOC_DECLARE(dx_python_io_adapter);
ALLOC_DEFINE(dx_python_io_adapter);

//static PyObject* dx_python_send(PyObject *self, PyObject *args)
//{
//    return 0;
//}


//===============================================================================
// Initialization of Modules and Types
//===============================================================================

static void dx_python_setup()
{
    //
    // Add LogAdapter
    //
    LogAdapterType.tp_new = PyType_GenericNew;
    if (PyType_Ready(&LogAdapterType) < 0) {
        PyErr_Print();
        dx_log(log_module, LOG_ERROR, "Unable to initialize LogAdapter");
        assert(0);
    } else {
        PyObject *m = Py_InitModule3("dispatch", empty_methods, "Dispatch Adapter Module");

        Py_INCREF(&LogAdapterType);
        PyModule_AddObject(m, "LogAdapter", (PyObject*) &LogAdapterType);

        PyObject *LogTrace = PyInt_FromLong((long) LOG_TRACE);
        Py_INCREF(LogTrace);
        PyModule_AddObject(m, "LOG_TRACE", LogTrace);

        PyObject *LogError = PyInt_FromLong((long) LOG_ERROR);
        Py_INCREF(LogError);
        PyModule_AddObject(m, "LOG_ERROR", LogError);

        PyObject *LogInfo = PyInt_FromLong((long) LOG_INFO);
        Py_INCREF(LogInfo);
        PyModule_AddObject(m, "LOG_INFO", LogInfo);
    }
}
