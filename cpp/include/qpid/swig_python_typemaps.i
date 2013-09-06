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

/* For UUID objects, to convert them to Python uuid.UUID objects,
 * we'll need a reference to the uuid module.
 */
%{
static PyObject* pUuidModule;
%}

%init %{
  /* Instead of directly referencing the uuid module (which is not available
   * on older versions of Python), reference the wrapper defined in
   * qpid.datatypes.
   */
  pUuidModule = PyImport_ImportModule("qpid.datatypes");

  /* Although it is not required, we'll publish the uuid module in our
   * module, as if this module was a python module and we called
   * "import uuid"
   */
  Py_INCREF(pUuidModule);
  PyModule_AddObject(m, "uuid", pUuidModule);
%}


%wrapper %{

#if PY_VERSION_HEX < 0x02050000 && !defined(PY_SSIZE_T_MIN)
typedef int Py_ssize_t;
#define PY_SSIZE_T_MAX INT_MAX
#define PY_SSIZE_T_MIN INT_MIN
#endif


    PyObject* MapToPy(const qpid::types::Variant::Map*);
    PyObject* ListToPy(const qpid::types::Variant::List*);
    PyObject* UuidToPy(const qpid::types::Uuid*);
    void PyToMap(PyObject*, qpid::types::Variant::Map*);
    void PyToList(PyObject*, qpid::types::Variant::List*);

    qpid::types::Variant PyToVariant(PyObject* value) {
        if (PyBool_Check(value))   return qpid::types::Variant(bool(PyInt_AS_LONG(value) ? true : false));
        if (PyFloat_Check(value))  return qpid::types::Variant(PyFloat_AS_DOUBLE(value));
        if (PyInt_Check(value))    return qpid::types::Variant(int64_t(PyInt_AS_LONG(value)));
        if (PyLong_Check(value))   return qpid::types::Variant(int64_t(PyLong_AsLongLong(value)));
        if (PyString_Check(value)) return qpid::types::Variant(std::string(PyString_AS_STRING(value)));
        if (PyUnicode_Check(value)) {
            qpid::types::Variant v(std::string(PyUnicode_AS_DATA(value)));
            v.setEncoding("utf8");
            return v;
        }
        if (PyDict_Check(value)) {
            qpid::types::Variant::Map map;
            PyToMap(value, &map);
            return qpid::types::Variant(map);
        }
        if (PyList_Check(value)) {
            qpid::types::Variant::List list;
            PyToList(value, &list);
            return qpid::types::Variant(list);
        }
        return qpid::types::Variant();
    }

    PyObject* VariantToPy(const qpid::types::Variant* v) {
        PyObject* result;
        try {
            switch (v->getType()) {
            case qpid::types::VAR_VOID: {
                result = Py_None;
                break;
            }
            case qpid::types::VAR_BOOL : {
                result = v->asBool() ? Py_True : Py_False;
                break;
            }
            case qpid::types::VAR_UINT8 :
            case qpid::types::VAR_UINT16 :
            case qpid::types::VAR_UINT32 : {
                result = PyInt_FromLong((long) v->asUint32());
                break;
            }
            case qpid::types::VAR_UINT64 : {
                result = PyLong_FromUnsignedLongLong((unsigned PY_LONG_LONG) v->asUint64());
                break;
            }
            case qpid::types::VAR_INT8 : 
            case qpid::types::VAR_INT16 :
            case qpid::types::VAR_INT32 : {
                result = PyInt_FromLong((long) v->asInt32());
                break;
            }
            case qpid::types::VAR_INT64 : {
                result = PyLong_FromLongLong((PY_LONG_LONG) v->asInt64());
                break;
            }
            case qpid::types::VAR_FLOAT : {
                result = PyFloat_FromDouble((double) v->asFloat());
                break;
            }
            case qpid::types::VAR_DOUBLE : {
                result = PyFloat_FromDouble((double) v->asDouble());
                break;
            }
            case qpid::types::VAR_STRING : {
                const std::string val(v->asString());
                if (v->getEncoding() == "utf8")
                    result = PyUnicode_DecodeUTF8(val.c_str(), val.size(), NULL);
                else
                    result = PyString_FromStringAndSize(val.c_str(), val.size());
                break;
            }
            case qpid::types::VAR_MAP : {
                result = MapToPy(&(v->asMap()));
                break;
            }
            case qpid::types::VAR_LIST : {
                result = ListToPy(&(v->asList()));
                break;
            }
            case qpid::types::VAR_UUID : {
                qpid::types::Uuid uuid = v->asUuid();
                result = UuidToPy(&uuid);
                break;
            }
            }
        } catch (qpid::types::Exception& ex) {
            PyErr_SetString(PyExc_RuntimeError, ex.what());
            result = 0;
        }

        return result;
    }

    PyObject* MapToPy(const qpid::types::Variant::Map* map) {
        PyObject* result = PyDict_New();
        qpid::types::Variant::Map::const_iterator iter;
        for (iter = map->begin(); iter != map->end(); iter++) {
            const std::string key(iter->first);
            PyObject* pyval = VariantToPy(&(iter->second));
            if (pyval == 0)
                return 0;
            PyDict_SetItem(result, PyString_FromStringAndSize(key.c_str(), key.size()), pyval);
        }
        return result;
    }

    PyObject* ListToPy(const qpid::types::Variant::List* list) {
        PyObject* result = PyList_New(list->size());
        qpid::types::Variant::List::const_iterator iter;
        Py_ssize_t idx(0);
        for (iter = list->begin(); iter != list->end(); iter++) {
            PyObject* pyval = VariantToPy(&(*iter));
            if (pyval == 0)
                return 0;
            PyList_SetItem(result, idx, pyval);
            idx++;
        }
        return result;
    }

    PyObject* UuidToPy(const qpid::types::Uuid * uuid) {
        PyObject* pUuidClass = PyObject_GetAttrString(pUuidModule, "UUID");
        if (!pUuidClass) {
          // Failed to get UUID class
          return 0;
        }

        PyObject* pArgs = PyTuple_New(0);
        PyObject* pKw = PyDict_New();
        PyObject* pData = PyString_FromStringAndSize(
          (const char*)(uuid->data()), 16);
        PyDict_SetItemString(pKw, "bytes", pData);

        PyObject* result = PyObject_Call(pUuidClass, pArgs, pKw);

        Py_DECREF(pData);
        Py_DECREF(pKw);
        Py_DECREF(pArgs);
        Py_DECREF(pUuidClass);

        return result;
    }


    void PyToMap(PyObject* obj, qpid::types::Variant::Map* map) {
        map->clear();
        Py_ssize_t iter(0);
        PyObject *key;
        PyObject *val;
        while (PyDict_Next(obj, &iter, &key, &val))
            (*map)[std::string(PyString_AS_STRING(key))] = PyToVariant(val);
    }

    void PyToList(PyObject* obj, qpid::types::Variant::List* list) {
        list->clear();
        Py_ssize_t count(PyList_Size(obj));
        for (Py_ssize_t idx = 0; idx < count; idx++)
            list->push_back(PyToVariant(PyList_GetItem(obj, idx)));
    }

%}


/* unsigned32 Convert from Python --> C */
%typemap(in) uint32_t {
    if (PyInt_Check($input)) {
        $1 = (uint32_t) PyInt_AsUnsignedLongMask($input);
    } else if (PyLong_Check($input)) {
        $1 = (uint32_t) PyLong_AsUnsignedLong($input);
    } else {
        SWIG_exception_fail(SWIG_ValueError, "unknown integer type");
    }
}

/* unsinged32 Convert from C --> Python */
%typemap(out) uint32_t {
    $result = PyInt_FromLong((long)$1);
}


/* unsigned16 Convert from Python --> C */
%typemap(in) uint16_t {
    if (PyInt_Check($input)) {
        $1 = (uint16_t) PyInt_AsUnsignedLongMask($input);
    } else if (PyLong_Check($input)) {
        $1 = (uint16_t) PyLong_AsUnsignedLong($input);
    } else {
        SWIG_exception_fail(SWIG_ValueError, "unknown integer type");
    }
}

/* unsigned16 Convert from C --> Python */
%typemap(out) uint16_t {
    $result = PyInt_FromLong((long)$1);
}


/* signed32 Convert from Python --> C */
%typemap(in) int32_t {
    if (PyInt_Check($input)) {
        $1 = (int32_t) PyInt_AsLong($input);
    } else if (PyLong_Check($input)) {
        $1 = (int32_t) PyLong_AsLong($input);
    } else {
        SWIG_exception_fail(SWIG_ValueError, "unknown integer type");
    }
}

/* signed32 Convert from C --> Python */
%typemap(out) int32_t {
    $result = PyInt_FromLong((long)$1);
}


/* unsigned64 Convert from Python --> C */
%typemap(in) uint64_t {
%#ifdef HAVE_LONG_LONG
    if (PyLong_Check($input)) {
        $1 = (uint64_t)PyLong_AsUnsignedLongLong($input);
    } else if (PyInt_Check($input)) {
        $1 = (uint64_t)PyInt_AsUnsignedLongLongMask($input);
    } else
%#endif
    {
        SWIG_exception_fail(SWIG_ValueError, "unsupported integer size - uint64_t input too large");
    }
}

/* unsigned64 Convert from C --> Python */
%typemap(out) uint64_t {
%#ifdef HAVE_LONG_LONG
    $result = PyLong_FromUnsignedLongLong((unsigned PY_LONG_LONG)$1);
%#else
    SWIG_exception_fail(SWIG_ValueError, "unsupported integer size - uint64_t output too large");
%#endif
}

/* signed64 Convert from Python --> C */
%typemap(in) int64_t {
%#ifdef HAVE_LONG_LONG
    if (PyLong_Check($input)) {
        $1 = (int64_t)PyLong_AsLongLong($input);
    } else if (PyInt_Check($input)) {
        $1 = (int64_t)PyInt_AsLong($input);
    } else
%#endif
    {
        SWIG_exception_fail(SWIG_ValueError, "unsupported integer size - int64_t input too large");
    }
}

/* signed64 Convert from C --> Python */
%typemap(out) int64_t {
%#ifdef HAVE_LONG_LONG
    $result = PyLong_FromLongLong((PY_LONG_LONG)$1);
%#else
    SWIG_exception_fail(SWIG_ValueError, "unsupported integer size - int64_t output too large");
%#endif
}


/* Convert from Python --> C */
%typemap(in) void * {
    $1 = (void *)$input;
}

/* Convert from C --> Python */
%typemap(out) void * {
    $result = (PyObject *) $1;
    Py_INCREF($result);
}

/*
 * Variant types: C++ --> Python
 */
%typemap(out) qpid::types::Variant::Map {
    $result = MapToPy(&$1);
}

%typemap(out) qpid::types::Variant::Map& {
    $result = MapToPy($1);
}

%typemap(out) qpid::types::Variant::List {
    $result = ListToPy(&$1);
}

%typemap(out) qpid::types::Variant::List& {
    $result = ListToPy($1);
}

%typemap(out) qpid::types::Variant& {
    $result = VariantToPy($1);
}

/*
 * UUID type: C++ --> Python
 */
%typemap(out) qpid::types::UUID & {
    $result = UuidToPy($1);
}


/*
 * Variant types: Ruby --> C++
 */
%typemap(in) qpid::types::Variant& {
    $1 = new qpid::types::Variant(PyToVariant($input));
}

%typemap(in) qpid::types::Variant::Map& {
    $1 = new qpid::types::Variant::Map();
    PyToMap($input, $1);
}

%typemap(in) qpid::types::Variant::List& {
    $1 = new qpid::types::Variant::List();
    PyToList($input, $1);
}

%typemap(in) const qpid::types::Variant::Map const & {
    $1 = new qpid::types::Variant::Map();
    PyToMap($input, $1);
}

%typemap(in) const qpid::types::Variant::List const & {
    $1 = new qpid::types::Variant::List();
    PyToList($input, $1);
}

%typemap(freearg) qpid::types::Variant& {
    delete $1;
}

%typemap(freearg) qpid::types::Variant::Map& {
    delete $1;
}

%typemap(freearg) qpid::types::Variant::List& {
    delete $1;
}


/*
 * Variant types: typecheck maps
 */
%typemap(typecheck) qpid::types::Variant::Map& {
    $1 = PyDict_Check($input) ? 1 : 0;
}

%typemap(typecheck)  qpid::types::Variant::List& {
    $1 = PyList_Check($input) ? 1 : 0;
}

%typemap(typecheck) qpid::types::Variant& {
    $1 = (PyFloat_Check($input)  ||
          PyString_Check($input) ||
          PyInt_Check($input)    ||
          PyLong_Check($input)   ||
          PyDict_Check($input)   ||
          PyList_Check($input)   ||
          PyBool_Check($input)) ? 1 : 0;
}

%typemap(typecheck) const qpid::types::Variant::Map const & {
    $1 = PyDict_Check($input) ? 1 : 0;
}

%typemap(typecheck) const qpid::types::Variant::List const & {
    $1 = PyList_Check($input) ? 1 : 0;
}

%typemap(typecheck) const qpid::types::Variant const & {
    $1 = (PyFloat_Check($input)  ||
          PyString_Check($input) ||
          PyInt_Check($input)    ||
          PyLong_Check($input)   ||
          PyDict_Check($input)   ||
          PyList_Check($input)   ||
          PyBool_Check($input)) ? 1 : 0;
}

%typemap(typecheck) bool {
    $1 = PyBool_Check($input) ? 1 : 0;
}



%typemap (typecheck, precedence=SWIG_TYPECHECK_UINT64) uint64_t {
    $1 = PyLong_Check($input) ? 1 : 0;
}

%typemap (typecheck, precedence=SWIG_TYPECHECK_UINT32) uint32_t {
    $1 = PyInt_Check($input) ? 1 : 0;
}

