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
 * \file AsyncResultHandleImpl.cpp
 */

#include "AsyncResultHandleImpl.h"

namespace qpid {
namespace broker {

AsyncResultHandleImpl::AsyncResultHandleImpl() :
        m_errNo(0),
        m_errMsg()
{}

AsyncResultHandleImpl::AsyncResultHandleImpl(boost::shared_ptr<BrokerAsyncContext> bc) :
        m_errNo(0),
        m_errMsg(),
        m_bc(bc)
{}

AsyncResultHandleImpl::AsyncResultHandleImpl(const int errNo,
                                             const std::string& errMsg,
                                             boost::shared_ptr<BrokerAsyncContext> bc) :
        m_errNo(errNo),
        m_errMsg(errMsg),
        m_bc(bc)
{}

AsyncResultHandleImpl::~AsyncResultHandleImpl()
{}

int
AsyncResultHandleImpl::getErrNo() const
{
    return m_errNo;
}

std::string
AsyncResultHandleImpl::getErrMsg() const
{
    return m_errMsg;
}

boost::shared_ptr<BrokerAsyncContext>
AsyncResultHandleImpl::getBrokerAsyncContext() const
{
    return m_bc;
}

}} // namespace qpid::broker
