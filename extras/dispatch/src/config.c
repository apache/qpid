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
#include "config_private.h"
#include <qpid/dispatch/alloc.h>
#include <qpid/dispatch/log.h>

#define PYTHON_MODULE "config"

static const char *log_module = "CONFIG";

struct dx_config_t {
    PyObject *pModule;
    PyObject *pClass;
    PyObject *pObject;
};

ALLOC_DECLARE(dx_config_t);
ALLOC_DEFINE(dx_config_t);

void dx_config_initialize()
{
    dx_python_start();
}


void dx_config_finalize()
{
    dx_python_stop();
}


dx_config_t *dx_config(char *filename)
{
    dx_config_t *config = new_dx_config_t();

    //
    // Load the Python configuration module and get a reference to the config class.
    //
    PyObject *pName = PyString_FromString(PYTHON_MODULE);
    config->pModule = PyImport_Import(pName);
    Py_DECREF(pName);

    if (!config->pModule) {
        PyErr_Print();
        free_dx_config_t(config);
        dx_log(log_module, LOG_ERROR, "Unable to load configuration module: %s", PYTHON_MODULE);
        return 0;
    }

    config->pClass = PyObject_GetAttrString(config->pModule, "DXConfig");
    if (!config->pClass || !PyClass_Check(config->pClass)) {
        PyErr_Print();
        Py_DECREF(config->pModule);
        free_dx_config_t(config);
        dx_log(log_module, LOG_ERROR, "Problem with configuration module: Missing DXConfig class");
        return 0;
    }

    //
    // Instantiate the DXConfig class, passing in the configuration file name.
    //
    PyObject *pArgs = PyTuple_New(1);
    PyObject *fname = PyString_FromString(filename);
    PyTuple_SetItem(pArgs, 0, fname);
    config->pObject = PyInstance_New(config->pClass, pArgs, 0);
    Py_DECREF(pArgs);

    if (config->pObject == 0) {
        PyErr_Print();
        Py_DECREF(config->pModule);
        free_dx_config_t(config);
        dx_log(log_module, LOG_ERROR, "Configuration file '%s' could not be read", filename);
        return 0;
    }

    return config;
}


void dx_config_free(dx_config_t *config)
{
    if (config) {
        Py_DECREF(config->pClass);
        Py_DECREF(config->pModule);
        free_dx_config_t(config);
    }
}


int dx_config_item_count(const dx_config_t *config, const char *section)
{
    PyObject *pSection;
    PyObject *pMethod;
    PyObject *pArgs;
    PyObject *pResult;
    int       result = 0;

    pMethod = PyObject_GetAttrString(config->pObject, "item_count");
    if (!pMethod || !PyCallable_Check(pMethod)) {
        dx_log(log_module, LOG_ERROR, "Problem with configuration module: No callable 'item_count'");
        if (pMethod)
            Py_DECREF(pMethod);
        return 0;
    }

    pSection = PyString_FromString(section);
    pArgs    = PyTuple_New(1);
    PyTuple_SetItem(pArgs, 0, pSection);
    pResult = PyObject_CallObject(pMethod, pArgs);
    Py_DECREF(pArgs);
    if (pResult && PyInt_Check(pResult))
        result = (int) PyInt_AsLong(pResult);
    if (pResult)
        Py_DECREF(pResult);
    Py_DECREF(pMethod);

    return result;
}


static PyObject *item_value(const dx_config_t *config, const char *section, int index, const char* key, const char* method)
{
    PyObject *pSection;
    PyObject *pIndex;
    PyObject *pKey;
    PyObject *pMethod;
    PyObject *pArgs;
    PyObject *pResult;

    pMethod = PyObject_GetAttrString(config->pObject, method);
    if (!pMethod || !PyCallable_Check(pMethod)) {
        dx_log(log_module, LOG_ERROR, "Problem with configuration module: No callable '%s'", method);
        if (pMethod)
            Py_DECREF(pMethod);
        return 0;
    }

    pSection = PyString_FromString(section);
    pIndex   = PyInt_FromLong((long) index);
    pKey     = PyString_FromString(key);
    pArgs    = PyTuple_New(3);
    PyTuple_SetItem(pArgs, 0, pSection);
    PyTuple_SetItem(pArgs, 1, pIndex);
    PyTuple_SetItem(pArgs, 2, pKey);
    pResult = PyObject_CallObject(pMethod, pArgs);
    Py_DECREF(pArgs);
    Py_DECREF(pMethod);

    return pResult;
}


const char *dx_config_item_value_string(const dx_config_t *config, const char *section, int index, const char* key)
{
    PyObject *pResult = item_value(config, section, index, key, "value_string");
    char     *value   = 0;

    if (pResult && PyString_Check(pResult)) {
        Py_ssize_t size = PyString_Size(pResult);
        value = (char*) malloc(size + 1);
        strncpy(value, PyString_AsString(pResult), size + 1);
    }

    if (pResult)
        Py_DECREF(pResult);

    return value;
}


uint32_t dx_config_item_value_int(const dx_config_t *config, const char *section, int index, const char* key)
{
    PyObject *pResult = item_value(config, section, index, key, "value_int");
    uint32_t  value   = 0;

    if (pResult && PyLong_Check(pResult))
        value = (uint32_t) PyLong_AsLong(pResult);

    if (pResult)
        Py_DECREF(pResult);

    return value;
}


