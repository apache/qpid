#ifndef QPID_CLIENT_HANDLEPRIVATE_H
#define QPID_CLIENT_HANDLEPRIVATE_H

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
 * Private implementation of handle, include in .cpp file of handle
 * subclasses _after_ including the declaration of class T.
 * T can be any class that can be used with boost::intrusive_ptr.
 */

template <class T>
Handle<T>::Handle(T* p) : impl(p) { if (impl) boost::intrusive_ptr_add_ref(impl); }

template <class T>
Handle<T>::~Handle() { if(impl) boost::intrusive_ptr_release(impl); }

template <class T>
Handle<T>::Handle(const Handle& h) : impl(h.impl) { if(impl) boost::intrusive_ptr_add_ref(impl); }

template <class T>
Handle<T>& Handle<T>::operator=(const Handle<T>& h) { Handle<T>(h).swap(*this); return *this; }

template <class T>
void Handle<T>::swap(Handle<T>& h) { std::swap(impl, h.impl); }


/** Access to private impl of a Handle */
template <class T>
class HandlePrivate {
  public:
    static boost::intrusive_ptr<T> get(Handle<T>& h) { return boost::intrusive_ptr<T>(h.impl); }
};


}} // namespace qpid::client

#endif  /*!QPID_CLIENT_HANDLEPRIVATE_H*/
