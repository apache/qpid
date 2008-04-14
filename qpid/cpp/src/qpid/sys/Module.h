#ifndef QPID_SYS_MODULE_H
#define QPID_SYS_MODULE_H

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

#include "qpid/sys/Shlib.h"
#include <boost/noncopyable.hpp>

namespace qpid {
namespace sys {

template <class T> class Module : public AutoShlib, private boost::noncopyable
{
  public:
    Module(const std::string& name) :
        AutoShlib(name),
        ptr(getSymbol<T*(*)()>("create")()) {}
                
    T* get() { return ptr; }
    T* operator->() { return ptr; }
    ~Module() throw() {
        getSymbol<void (*)(T*)>("destroy")(ptr);
    }

  private:
    T* ptr;
};

}}

#endif  /*!QPID_SYS_MODULE_H*/
