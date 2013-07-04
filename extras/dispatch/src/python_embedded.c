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

#include "python_embedded.h"
#include <qpid/dispatch/threading.h>
#include <qpid/dispatch/log.h>
#include <qpid/dispatch/amqp.h>

static uint32_t     ref_count  = 0;
static sys_mutex_t *lock       = 0;
static char        *log_module = "PYTHON";


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

