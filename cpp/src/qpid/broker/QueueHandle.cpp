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
 * \file QueueHandle.cpp
 */

#include "QueueHandle.h"

#include "qpid/messaging/PrivateImplRef.h"

namespace qpid {
namespace broker {

typedef qpid::messaging::PrivateImplRef<QueueHandle> PrivateImpl;

QueueHandle::QueueHandle(qpid::asyncStore::QueueHandleImpl* p) :
        IdHandle()
{
    PrivateImpl::ctor(*this, p);
}

QueueHandle::QueueHandle(const QueueHandle& r) :
        qpid::messaging::Handle<qpid::asyncStore::QueueHandleImpl>(),
        IdHandle()
{
    PrivateImpl::copy(*this, r);
}

QueueHandle::~QueueHandle()
{
    PrivateImpl::dtor(*this);
}

QueueHandle&
QueueHandle::operator=(const QueueHandle& r)
{
    return PrivateImpl::assign(*this, r);
}

// --- QueueHandleImpl methods ---
const std::string&
QueueHandle::getName() const
{
    return impl->getName();
}

}} // namespace qpid::broker
