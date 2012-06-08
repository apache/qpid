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

/**
 * \file ConfigHandle.cpp
 */

#include "ConfigHandle.h"

#include "qpid/messaging/PrivateImplRef.h"

namespace qpid {
namespace broker {

typedef qpid::messaging::PrivateImplRef<ConfigHandle> PrivateImpl;

ConfigHandle::ConfigHandle(qpid::asyncStore::ConfigHandleImpl* p) :
        qpid::messaging::Handle<qpid::asyncStore::ConfigHandleImpl>(),
        IdHandle()
{
    PrivateImpl::ctor(*this, p);
}

ConfigHandle::ConfigHandle(const ConfigHandle& r) :
        qpid::messaging::Handle<qpid::asyncStore::ConfigHandleImpl>(),
        IdHandle()
{
    PrivateImpl::copy(*this, r);
}

ConfigHandle::~ConfigHandle()
{
    PrivateImpl::dtor(*this);
}

ConfigHandle&
ConfigHandle::operator=(const ConfigHandle& r)
{
    return PrivateImpl::assign(*this, r);
}

}} // namespace qpid::broker
