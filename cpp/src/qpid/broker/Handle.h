#ifndef QPID_BROKER_HANDLE_H
#define QPID_BROKER_HANDLE_H

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

/*
 * NOTE: This is a copy of qpid::messaging::Handle (but stripped of its
 * messaging-specific Windows decoration macros)
 *
 * This (together with PrivateImplRef.h) has been placed here so
 * as not to introduce unnecessary dependencies on qpid::messaging
 * for users of the Handle template in the qpid::broker namespace.
 *
 * Any fixes made here should also be made to qpid/messaging/Handle.h
 *
 * TODO: Find the correct Windows decorations for these functions.
 * TODO: Find (if possible) a way to eliminate two copies of the same code.
 */

namespace qpid {
namespace broker {

template <class> class PrivateImplRef;

/** \ingroup messaging 
 * A handle is like a pointer: refers to an underlying implementation object.
 * Copying the handle does not copy the object.
 *
 * Handles can be null,  like a 0 pointer. Use isValid(), isNull() or the
 * conversion to bool to test for a null handle.
 */
template <class T> class Handle {
  public:

    /**@return true if handle is valid,  i.e. not null. */
    bool isValid() const { return impl; }

    /**@return true if handle is null. It is an error to call any function on a null handle. */
    bool isNull() const { return !impl; }

    /** Conversion to bool supports idiom if (handle) { handle->... } */
    operator bool() const { return impl; }

    /** Operator ! supports idiom if (!handle) { do_if_handle_is_null(); } */
    bool operator !() const { return !impl; }

    void swap(Handle<T>& h) { T* t = h.impl; h.impl = impl; impl = t; }

    void reset() { impl = 0; }

  protected:
    typedef T Impl;
    Handle() :impl() {}

    // Not implemented,subclasses must implement.
    Handle(const Handle&);
    Handle& operator=(const Handle&);

    Impl* impl;

  friend class PrivateImplRef<T>;
};

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_HANDLE_H*/
