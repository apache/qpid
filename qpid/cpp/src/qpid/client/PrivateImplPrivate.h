#ifndef QPID_CLIENT_PRIVATEIMPLPRIVATE_H
#define QPID_CLIENT_PRIVATEIMPLPRIVATE_H

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

#include <algorithm>

namespace qpid {
namespace client {

/** @file
 * Implementation of PrivateImpl functions, to include in .cpp file of handle subclasses.
 * T can be any class with value semantics.
 */

template <class T>
PrivateImpl<T>::PrivateImpl(T* p) : impl(p) { assert(impl); }

template <class T>
PrivateImpl<T>::~PrivateImpl() { delete impl; }

template <class T>
PrivateImpl<T>::PrivateImpl(const PrivateImpl& h) : impl(new T(*h.impl)) {}

template <class T>
PrivateImpl<T>& PrivateImpl<T>::operator=(const PrivateImpl<T>& h) { PrivateImpl<T>(h).swap(*this); return *this; }

template <class T>
void PrivateImpl<T>::swap(PrivateImpl<T>& h) { std::swap(impl, h.impl); }


/** Access to private impl of a PrivateImpl */
template <class T>
class PrivateImplPrivate {
  public:
    static T* get(const PrivateImpl<T>& h) { return h.impl; }
    static void set(PrivateImpl<T>& h, const T& p) { PrivateImpl<T>(p).swap(h); }
};

template<class T> T* privateImplGetPtr(PrivateImpl<T>& h) { return PrivateImplPrivate<T>::get(h); }
template<class T> T* privateImplGetPtr(const PrivateImpl<T>& h) { return PrivateImplPrivate<T>::get(h); }
template<class T> void privateImplSetPtr(PrivateImpl<T>& h, const T*& p) { PrivateImplPrivate<T>::set(h, p); }

}} // namespace qpid::client

#endif  /*!QPID_CLIENT_PRIVATEIMPLPRIVATE_H*/

