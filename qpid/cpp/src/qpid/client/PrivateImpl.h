#ifndef QPID_CLIENT_PRIVATEIMPL_H
#define QPID_CLIENT_PRIVATEIMPL_H

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

template <class T> class PrivateImplPrivate;

/**
 * Base classes for objects with a private implementation.
 * 
 * PrivateImpl objects have value semantics: copying the object also
 * makes a copy of the implementation.
 */
template <class T> class PrivateImpl {
  public:
    QPID_CLIENT_EXTERN ~PrivateImpl();
    QPID_CLIENT_EXTERN PrivateImpl(const PrivateImpl&);
    QPID_CLIENT_EXTERN PrivateImpl& operator=(const PrivateImpl&);
    QPID_CLIENT_EXTERN void swap(PrivateImpl<T>&);

  protected:
    QPID_CLIENT_EXTERN PrivateImpl(T*);
    T* impl;

  friend class PrivateImplPrivate<T>;
};

}} // namespace qpid::client

#endif  /*!QPID_CLIENT_PRIVATEIMPL_H*/
