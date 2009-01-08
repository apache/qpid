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

namespace qpid {
namespace client {

template <class T> class HandlePrivate;

/**
 * A handle is like a pointer: it points to some underlying object.
 * Handles can be null,  like a 0 pointer. Use isValid(), isNull() or the
 * implicit conversion to bool to test for a null handle.
 */
template <class T> class Handle {
  public:
    ~Handle();
    Handle(const Handle&);
    Handle& operator=(const Handle&);

    /**@return true if handle is valid,  i.e. not null. */
    bool isValid() const { return impl; }

    /**@return true if handle is null. It is an error to call any function on a null handle. */
    bool isNull() const { return !impl; }

    operator bool() const { return impl; }
    bool operator !() const { return impl; }

    void swap(Handle<T>&);

  protected:
    Handle(T* =0);
    T* impl;

  friend class HandlePrivate<T>;
};

}} // namespace qpid::client

#endif  /*!QPID_CLIENT_HANDLE_H*/
