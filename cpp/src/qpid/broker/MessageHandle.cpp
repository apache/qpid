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
 * \file MessageHandle.cpp
 */

#include "MessageHandle.h"

#include "qpid/messaging/PrivateImplRef.h"

namespace qpid {
namespace broker {

typedef qpid::messaging::PrivateImplRef<MessageHandle> PrivateImpl;

MessageHandle::MessageHandle(qpid::asyncStore::MessageHandleImpl* p) :
        IdHandle()
{
    PrivateImpl::ctor(*this, p);
}

MessageHandle::MessageHandle(const MessageHandle& r) :
        qpid::messaging::Handle<qpid::asyncStore::MessageHandleImpl>(),
        IdHandle()
{
    PrivateImpl::copy(*this, r);
}

MessageHandle::~MessageHandle()
{
    PrivateImpl::dtor(*this);
}

MessageHandle&
MessageHandle::operator=(const MessageHandle& r)
{
    return PrivateImpl::assign(*this, r);
}

}} // namespace qpid::broker
