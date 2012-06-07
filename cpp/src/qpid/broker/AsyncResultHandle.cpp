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
 * \file AsyncResultHandle.cpp
 */

#include "AsyncResultHandle.h"

#include "qpid/messaging/PrivateImplRef.h"

namespace qpid {
namespace broker {

typedef qpid::messaging::PrivateImplRef<AsyncResultHandle> PrivateImpl;

AsyncResultHandle::AsyncResultHandle(AsyncResultHandleImpl* p) :
            qpid::messaging::Handle<AsyncResultHandleImpl>()
{
    PrivateImpl::ctor(*this, p);
}

AsyncResultHandle::AsyncResultHandle(const AsyncResultHandle& r) :
            qpid::messaging::Handle<AsyncResultHandleImpl>()
{
    PrivateImpl::copy(*this, r);
}

AsyncResultHandle::~AsyncResultHandle()
{
    PrivateImpl::dtor(*this);
}

AsyncResultHandle&
AsyncResultHandle::operator=(const AsyncResultHandle& r)
{
    return PrivateImpl::assign(*this, r);
}

int
AsyncResultHandle::getErrNo() const
{
    return impl->getErrNo();
}

std::string
AsyncResultHandle::getErrMsg() const
{
    return impl->getErrMsg();
}

const BrokerAsyncContext*
AsyncResultHandle::getBrokerAsyncContext() const
{
    return impl->getBrokerAsyncContext();
}

}} // namespace qpid::broker
