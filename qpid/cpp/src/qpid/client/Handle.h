#ifndef QPID_CLIENT_HANDLE_H
#define QPID_CLIENT_HANDLE_H

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

#include "qpid/client/ClientImportExport.h"

namespace qpid {
namespace client {

template <class T> class HandlePrivate;

/**
 * A handle is like a pointer: it points to some implementation object.
 * Copying the handle does not copy the object.
 * 
 * Handles can be null,  like a 0 pointer. Use isValid(), isNull() or the
 * conversion to bool to test for a null handle.
 */
template <class T> class Handle {
  public:
    QPID_CLIENT_EXTERN ~Handle();
    QPID_CLIENT_EXTERN Handle(const Handle&);
    QPID_CLIENT_EXTERN Handle& operator=(const Handle&);

    /**@return true if handle is valid,  i.e. not null. */
    QPID_CLIENT_EXTERN bool isValid() const { return impl; }

    /**@return true if handle is null. It is an error to call any function on a null handle. */
    QPID_CLIENT_EXTERN bool isNull() const { return !impl; }

    /** Conversion to bool supports idiom if (handle) { handle->... } */
    QPID_CLIENT_EXTERN operator bool() const { return impl; }

    /** Operator ! supports idiom if (!handle) { do_if_handle_is_null(); } */
    QPID_CLIENT_EXTERN bool operator !() const { return !impl; }

    QPID_CLIENT_EXTERN void swap(Handle<T>&);

  protected:
    QPID_CLIENT_EXTERN Handle(T* =0);
    T* impl;

  friend class HandlePrivate<T>;
};

}} // namespace qpid::client

#endif  /*!QPID_CLIENT_HANDLE_H*/
